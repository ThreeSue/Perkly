package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtUser;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class SearchRiskService {
 public static final Set<String> SCENES=Set.of("SEARCH","PROFILE","FEEDBACK","CHAT","MERCHANT","STORE","COUPON","NOTICE","AI_INPUT","AI_OUTPUT");
 private final SearchWordMapper words; private final SearchRiskRecorder recorder;
 @org.springframework.beans.factory.annotation.Autowired private ContentRiskPolicyMapper policies;
 @org.springframework.beans.factory.annotation.Autowired private ContentRiskDictionary dictionary;
 public record Subject(String type,Long id){}
 public boolean enabled(String scene){var p=policies==null?null:policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,scene));return p==null||Boolean.TRUE.equals(p.getEnabled());}
 public boolean whitelisted(String text){return text!=null&&words.selectListByQuery(QueryWrapper.create().eq(SearchWord::getEnabled,true).eq(SearchWord::getMode,"ALLOW")).stream().anyMatch(w->normalize(w.getWord()).equals(normalize(text)));}
 public Subject currentSubject(){
  var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.isAuthenticated()&&auth.getPrincipal() instanceof JwtUser u)return new Subject(u.getUserTypeEnum().name(),u.getUserId());
  return new Subject("GUEST",null);
 }
 public void check(String text,String source){
  if(text!=null&&text.length()>100)throw new CoolException("搜索关键词不能超过100个字符");
  checkContent(text,"SEARCH",source,null);
 }
 public void checkContent(String text,String scene,String businessType,Long businessId){
  inspect(text,scene,businessType,businessId,"AI_OUTPUT".equals(scene)?new Subject("SYSTEM",null):currentSubject(),null,null);
 }
 public Long inspect(String text,String scene,String businessType,Long businessId,Subject subject,Long jobId,String advice){
  if(text==null||text.isBlank())return null;
  if(!SCENES.contains(scene))throw new IllegalArgumentException("Unknown content scene");
  if(text.length()>20000)throw new CoolException("待检测文字不能超过20000字");
  var policy=policies==null?null:policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,scene));
  if(policy!=null&&!Boolean.TRUE.equals(policy.getEnabled()))return null;
  var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  var request=attributes instanceof org.springframework.web.context.request.ServletRequestAttributes a?a.getRequest():null;
  String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(text);
  String key="contentRisk:"+scene+":"+businessType+":"+businessId+":"+subject.type()+":"+subject.id()+":"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(text.trim());
  if(jobId==null&&request!=null&&request.getAttribute(key)!=null)return null;
  var rules=words.selectListByQuery(QueryWrapper.create());
  boolean allowed=rules.stream().anyMatch(w->Boolean.TRUE.equals(w.getEnabled())&&"ALLOW".equals(w.getMode())&&normalize(w.getWord()).equals(normalize(text)));
  if(allowed)return null;
  var hits=dictionary().find(text,rules);
  if(hits.isEmpty()&&(advice==null||advice.isBlank()))return null;
  var row=new SearchRisk();row.setContent(text);row.setContentHash(hash);row.setScene(scene);row.setBusinessType(businessType);row.setBusinessId(businessId);
  row.setSubjectType(subject.type());row.setSubjectId(subject.id());row.setUserId("APP".equals(subject.type())?subject.id():null);
  row.setSource(jobId==null&&request!=null?request.getRequestURI():jobId!=null?"SCAN/"+businessType:businessType);
  row.setMatchedWords(cn.hutool.json.JSONUtil.toJsonStr(hits.stream().map(ContentRiskDictionary.Hit::word).distinct().toList()));
  row.setMatchedDetails(cn.hutool.json.JSONUtil.toJsonStr(hits));
  boolean block=hits.stream().anyMatch(w->"BLOCK".equals(w.mode()));
  if(policy!=null&&!"INHERIT".equals(policy.getAction())&&hits.stream().anyMatch(h->"CUSTOM".equals(h.source())))block="BLOCK".equals(policy.getAction());
  row.setMode(jobId!=null?"REVIEW":block?"BLOCK":"REVIEW");row.setStatus("PENDING");row.setJobId(jobId);row.setAiAdvice(advice);
  if(jobId!=null)row.setDedupKey(cn.hutool.crypto.digest.DigestUtil.sha256Hex(scene+":"+businessType+":"+businessId+":"+subject.type()+":"+subject.id()+":"+hash));
  Long id=recorder.persist(row);
  if(jobId==null&&request!=null&&businessId==null&&id!=null){
   String pending="contentRisk.pending."+scene;
   @SuppressWarnings("unchecked") var ids=(java.util.List<Long>)request.getAttribute(pending);
   if(ids==null){ids=new java.util.ArrayList<>();request.setAttribute(pending,ids);}ids.add(id);
  }
  if(jobId==null&&block)throw new CoolException("SEARCH".equals(scene)?"搜索内容包含不适宜词语，请修改后重试":"内容未通过平台规则检测，请修改后重试",422);
  if(request!=null&&jobId==null)request.setAttribute(key,true);
  return id;
 }
 public void bind(String scene,Long businessId){
  var a=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  if(!(a instanceof org.springframework.web.context.request.ServletRequestAttributes s)||businessId==null)return;
  @SuppressWarnings("unchecked") var ids=(java.util.List<Long>)s.getRequest().getAttribute("contentRisk.pending."+scene);
  if(ids==null||ids.isEmpty())return;var copy=java.util.List.copyOf(ids);s.getRequest().removeAttribute("contentRisk.pending."+scene);
  Runnable update=()->recorder.bind(copy,businessId);
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){update.run();}});
  else update.run();
 }
 static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
 private ContentRiskDictionary dictionary(){if(dictionary==null)dictionary=new ContentRiskDictionary();return dictionary;}
 public static List<SearchWord> matches(String text,List<SearchWord> rules){
  var hits=new ContentRiskDictionary().find(text,rules);
  return rules.stream().filter(r->hits.stream().anyMatch(h->"CUSTOM".equals(h.source())&&h.word().equals(r.getWord()))).toList();
 }
}

