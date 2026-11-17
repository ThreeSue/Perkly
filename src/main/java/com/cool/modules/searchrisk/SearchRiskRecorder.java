package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.dao.DuplicateKeyException;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 搜索风险检测与运营复核
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:00:23
 */
@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class SearchRiskRecorder {
 private final SearchRiskMapper records;
 private final SearchRiskAuditMapper audits;
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void bind(java.util.List<Long> ids,Long businessId){
  for(Long id:ids) bindOne(id,businessId,null);
 }
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void bindCouponItem(Long riskId,Long couponId,Long productId){
  bindOne(riskId,couponId,productId);
 }
 private void bindOne(Long id,Long businessId,Long productId){
  var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getId,id).forUpdate());
  if(row!=null&&row.getBusinessId()==null){
  if(productId!=null)row.setBusinessType(row.getBusinessType()+":"+productId);
  row.setBusinessId(businessId);RiskOrigin.apply(row,row.getBusinessType(),businessId,null);String key=RiskOrigin.key(row);
  row.setDedupKey(key);
  try{records.update(row);}catch(DuplicateKeyException collision){
   var prior=byKey(key,true);
   if(prior==null)throw collision;
   row.setDuplicateOfId(prior.getId());row.setDedupKey(null);records.update(row,false);
   var audit=new SearchRiskAudit();
   audit.setTargetId(row.getId());audit.setAction("ORIGIN_BIND_MERGE");
   audit.setDetail(json.valueToTree(java.util.Map.of("duplicateOfId",prior.getId(),"originType",row.getOriginType(),
    "originId",businessId,"originField",row.getOriginField(),"contentVersion",row.getContentVersion(),
    "evidence","提交后绑定同一原始对象、字段和内容版本")).toString());
   audits.insert(audit);
  }
 }}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void record(SearchRisk entry){records.insert(entry);}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public Long persist(SearchRisk entry){
  return save(entry).id();
 }
 public record Saved(Long id,boolean created){}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public Saved persistOutcome(SearchRisk entry){return save(entry);}
 private Saved save(SearchRisk entry){
  var old=byKey(entry.getDedupKey(),false);
  if(old!=null)return reuse(entry,old);
  try{records.insert(entry);}catch(DuplicateKeyException duplicate){
   old=byKey(entry.getDedupKey(),true);
   if(old==null)throw duplicate;
   return reuse(entry,old);
  }
  return new Saved(entry.getId(),true);
 }
 private SearchRisk byKey(String key,boolean lock){
  if(key==null)return null;
  var query=QueryWrapper.create().eq(SearchRisk::getDedupKey,key);
  return records.selectOneByQuery(lock?query.forUpdate():query);
 }
 private Saved reuse(SearchRisk entry,SearchRisk old){
  boolean hasAuthor=entry.getSubmittedByType()!=null&&old.getSubmittedByType()==null
   &&!"WRITE".equals(entry.getDetectionEntry());
  if(hasAuthor||entry.getAiAdvice()!=null){
   // Recheck author evidence under lock and leave concurrent human resolution intact.
   var locked=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getId,old.getId()).forUpdate());
   if(locked==null)return new Saved(old.getId(),false);
   var update=new SearchRisk();update.setId(old.getId());update.setAiAdvice(entry.getAiAdvice());
   if(hasAuthor&&locked.getSubmittedByType()==null){
    update.setSubmittedByType(entry.getSubmittedByType());update.setSubmittedById(entry.getSubmittedById());
    update.setSubjectType(entry.getSubjectType());update.setSubjectId(entry.getSubjectId());update.setUserId(entry.getUserId());
   }
   records.update(update);
  }
  return new Saved(old.getId(),false);
 }
}
