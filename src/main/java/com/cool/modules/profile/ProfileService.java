package com.cool.modules.profile;
import com.cool.modules.profile.mapper.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.ai.service.AiSettingsService;
import com.cool.modules.support.service.SupportAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 合并画像任务并在短事务外执行模型保持失败旧快照可用
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:12:00
 */
@Service @RequiredArgsConstructor
public class ProfileService {
 private final ProfileSnapshotMapper snapshots;private final ProfileJobMapper jobs;
 private final ProfileHistoryMapper history;
 private final ProfileCorrectionMapper corrections;
 private final ProfileSourceReader source;private final ProfileModel model;private final ObjectMapper json;
 private final TransactionTemplate transaction;private final AiProperties ai;private final AiSettingsService settings;
 private final SupportAccess access;
 public record View(Long customerId,Long revision,Date cutoff,Date updatedAt,String state,String errorCode,
  ProfileSourceReader.Evidence evidence,ProfileModel.Interpretation interpretation,String modelVersion,String promptVersion,List<String> excludedLabels) {}
 public View view(Long customer) {
  access.customer(customer);var s=snapshots.selectOneById(customer);var j=jobs.selectOneById(customer);
  try{var correction=corrections.selectOneById(customer);List<String> excluded=correction==null?List.of():Arrays.asList(json.readValue(correction.getExcludedLabels(),String[].class));
   var interpretation=s==null?null:json.readValue(s.getInterpretationJson(),ProfileModel.Interpretation.class);
   if(interpretation!=null)interpretation=new ProfileModel.Interpretation(interpretation.tags().stream().filter(t->!excluded.contains(t.label())).toList(),interpretation.sufficiency());
   return new View(customer,s==null?null:s.getRevision(),s==null?null:s.getCutoff(),s==null?null:s.getUpdatedAt(),
   j==null?"NOT_BUILT":j.getState(),j==null?null:j.getErrorCode(),s==null?null:json.readValue(s.getEvidenceJson(),ProfileSourceReader.Evidence.class),
   interpretation,s==null?null:s.getModelVersion(),s==null?null:s.getPromptVersion(),excluded);
  }catch(Exception e){throw new CoolException("画像快照无法读取，请重新更新",503);}
 }
 @org.springframework.transaction.annotation.Transactional
 public void correct(Long customer,List<String> labels) {
  access.customer(customer);
  if(labels==null||labels.size()>8)throw new CoolException("最多排除8个已有标签");
  var snapshot=snapshots.selectOneByQuery(QueryWrapper.create().eq(ProfileSnapshotEntity::getCustomerId,customer).forUpdate());
  if(snapshot==null)throw new CoolException("请先生成画像");
  try {
   var raw=json.readValue(snapshot.getInterpretationJson(),ProfileModel.Interpretation.class);
   var old=corrections.selectOneById(customer);Set<String> allowed=new HashSet<>();raw.tags().forEach(t->allowed.add(t.label()));
   if(old!=null)allowed.addAll(Arrays.asList(json.readValue(old.getExcludedLabels(),String[].class)));
   if(!allowed.containsAll(labels))throw new CoolException("只能排除已有标签，不能杜撰偏好");
   var row=new ProfileCorrectionEntity();row.setCustomerId(customer);row.setExcludedLabels(encode(labels.stream().distinct().toList()));row.setUpdatedAt(new Date());
   if(old==null)corrections.insert(row);else corrections.update(row,false);
  }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new CoolException("画像数据无法读取，请先重新分析");}
 }
 public void request(Long customer,boolean force) {
  access.customer(customer);
  transaction.executeWithoutResult(tx->{
   var j=jobs.selectOneByQuery(QueryWrapper.create().eq(ProfileJobEntity::getCustomerId,customer).forUpdate());
   Date now=new Date();
   if(j==null){j=new ProfileJobEntity();j.setCustomerId(customer);j.setState("PENDING");j.setAttempts(0);j.setRequestedVersion(1L);
    j.setRequestedAt(now);j.setNextAttemptAt(now);try{jobs.insert(j);}catch(org.springframework.dao.DuplicateKeyException concurrent){}return;}
   if(Set.of("PENDING","RUNNING").contains(j.getState()))return;
   var s=snapshots.selectOneById(customer);
   if(!force&&s!=null&&s.getCutoff().toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(LocalDate.now(ZoneId.of("Asia/Shanghai"))))return;
   if(!force&&"FAILED".equals(j.getState())&&j.getRequestedAt().toInstant().plus(Duration.ofDays(1)).isAfter(Instant.now()))return;
   j.setRequestedVersion(j.getRequestedVersion()+1);j.setState("PENDING");j.setAttempts(0);j.setErrorCode(null);j.setRequestedAt(now);j.setNextAttemptAt(now);jobs.update(j,false);
  });
 }
 public boolean runOne() {
  if(!Boolean.TRUE.equals(settings.current().getEnabled()))return false;
  var claim=transaction.execute(tx->{
   var now=new Date();var j=jobs.selectOneByQuery(QueryWrapper.create().eq(ProfileJobEntity::getState,"PENDING")
    .le(ProfileJobEntity::getNextAttemptAt,now).orderBy(ProfileJobEntity::getRequestedAt,true).limit(1).forUpdate());
   if(j==null)j=jobs.selectOneByQuery(QueryWrapper.create().eq(ProfileJobEntity::getState,"RUNNING").lt(ProfileJobEntity::getLeaseUntil,now).limit(1).forUpdate());
   if(j==null)return null;
   if(j.getAttempts()>=3){j.setState("FAILED");j.setErrorCode("RETRY_EXHAUSTED");jobs.update(j);return null;}
   j.setState("RUNNING");j.setAttempts(j.getAttempts()+1);j.setRunningVersion(j.getRequestedVersion());j.setLeaseUntil(Date.from(Instant.now().plusSeconds(180)));jobs.update(j);return j;
  });
  if(claim==null)return false;
  ProfileSourceReader.Evidence facts=null;ProfileModel.Interpretation interpretation=null;String failure=null;
  try{access.customer(claim.getCustomerId());facts=source.read(claim.getCustomerId(),Instant.now());interpretation=model.interpret(facts);}catch(Exception e){failure="PROFILE_ANALYSIS_FAILED";}
  final var evidence=facts;final var result=interpretation;final String error=failure;
  transaction.executeWithoutResult(tx->{
   var j=jobs.selectOneByQuery(QueryWrapper.create().eq(ProfileJobEntity::getCustomerId,claim.getCustomerId()).forUpdate());
   if(j==null||!"RUNNING".equals(j.getState())||!Objects.equals(j.getLeaseUntil(),claim.getLeaseUntil()))return;
   if(error==null){
    var s=new ProfileSnapshotEntity();s.setCustomerId(j.getCustomerId());s.setRevision(j.getRunningVersion());s.setCutoff(Date.from(evidence.statistics().cutoff()));
    s.setUpdatedAt(new Date());s.setEvidenceJson(encode(evidence));s.setInterpretationJson(encode(result));s.setModelVersion(ai.getTextModel());s.setPromptVersion(ProfileModel.VERSION);
    if(snapshots.selectOneById(s.getCustomerId())==null)snapshots.insert(s);else snapshots.update(s,false);
    var h=new ProfileHistoryEntity();h.setCustomerId(s.getCustomerId());h.setRevision(s.getRevision());h.setCutoff(s.getCutoff());h.setSnapshotJson(encode(s));history.insert(h);
    j.setState("COMPLETED");j.setErrorCode(null);
   }else{j.setState(j.getAttempts()>=3?"FAILED":"PENDING");j.setErrorCode(error);j.setNextAttemptAt(Date.from(Instant.now().plusSeconds(60L*j.getAttempts())));}
   j.setLeaseUntil(null);jobs.update(j,false);
   settings.audit(null,null,null,error==null?"PROFILE_COMPLETED":"PROFILE_FAILED",encode(Map.of("customerId",j.getCustomerId(),"revision",j.getRunningVersion(),"attempt",j.getAttempts())));
  });return true;
 }
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Profile serialization failed");}}
}
