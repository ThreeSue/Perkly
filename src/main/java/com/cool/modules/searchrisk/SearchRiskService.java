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
 @org.springframework.beans.factory.annotation.Autowired private RiskContentAuthors authors;
 public record Subject(String type,Long id){}
 public boolean enabled(String scene){var p=policies==null?null:policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,scene));return p==null||Boolean.TRUE.equals(p.getEnabled());}
 public boolean whitelisted(String text){return text!=null&&(dictionary!=null?dictionary.isAllowed(text):words.selectListByQuery(QueryWrapper.create().eq(SearchWord::getEnabled,true).eq(SearchWord::getMode,"ALLOW")).stream().anyMatch(w->Boolean.TRUE.equals(w.getEnabled())&&"ALLOW".equals(w.getMode())&&normalize(w.getWord()).equals(normalize(text))));}
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
 public void checkChangedContent(String previous,String text,String scene,String businessType,Long businessId){
  checkChangedContent(previous,text,scene,businessType,businessId,currentSubject());
 }
 public void checkChangedContent(String previous,String text,String scene,String businessType,Long businessId,Subject author){
  if(text==null || text.isBlank() || Objects.equals(RiskOrigin.version(previous),RiskOrigin.version(text)))return;
  inspect(text,scene,businessType,businessId,author,null,null);
 }
 public void recordSavedContent(String previous,String text,String businessType,Long businessId){
  recordSavedContent(previous,text,businessType,businessId,currentSubject());
 }
 public void recordSavedContent(String previous,String text,String businessType,Long businessId,Subject author){
  authors.saveChanged(previous,text,businessType,businessId,author);
 }
 public Long checkChatMessage(String text,Subject author,java.util.function.Supplier<SearchRisk.ChatContext> context){
  String scene="SYSTEM".equals(author.type())?"AI_OUTPUT":"CHAT";
  return evaluate(text,scene,"CHAT_MESSAGE",null,author,null,null,false,false,row->row.setChatContext(context.get())).id();
 }
 public void bindChatMessage(Long riskId,Long messageId){
  if(riskId!=null&&messageId!=null)bindAfterCommit(List.of(riskId),messageId);
 }
 public Long inspect(String text,String scene,String businessType,Long businessId,Subject subject,Long jobId,String advice){
  return evaluate(text,scene,businessType,businessId,subject,jobId,advice,false,true,null).id();
 }
 public void checkCouponChange(String previous,String text,String field,Long couponId,SearchRisk.CouponContext context){
  if(text==null||text.isBlank()||Objects.equals(RiskOrigin.version(previous),RiskOrigin.version(text)))return;
  evaluate(text,"COUPON",field,couponId,currentSubject(),null,null,false,true,row->row.setCouponContext(context));
 }
 public Long checkNewCouponItem(String text,String field,SearchRisk.CouponContext context){
  return evaluate(text,"COUPON",field,null,currentSubject(),null,null,false,false,row->row.setCouponContext(context)).id();
 }
 public void bindCouponItem(Long riskId,Long couponId,Long productId){
  if(riskId==null)return;
  afterCommit(()->recorder.bindCouponItem(riskId,couponId,productId));
 }
 public SearchRiskRecorder.Saved rescan(RiskOriginalContent.Field field,Long businessId){
  return evaluate(field.value(),field.scene(),field.businessType(),businessId,field.author(),null,null,true,false,null);
 }
 public record RuleResult(String action,List<ContentRiskDictionary.Hit> hits){}
 public RuleResult testRule(String text,String scene){
  if(scene==null||!SCENES.contains(scene)||text==null||text.isBlank()||text.length()>20000
    ||"SEARCH".equals(scene)&&text.length()>100)throw new CoolException("请选择检测场景，并输入符合长度限制的文字",400);
  return rule(text,scene);
 }
 private RuleResult rule(String text,String scene){
  var policy=policies==null?null:policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,scene));
  if(policy!=null&&!Boolean.TRUE.equals(policy.getEnabled()))return new RuleResult("DISABLED",List.of());
  if(dictionary==null&&whitelisted(text))return new RuleResult("WHITELISTED",List.of());
  var match=dictionary==null?new ContentRiskDictionary.Match(false,new ContentRiskDictionary().find(text,words.selectListByQuery(QueryWrapper.create()))):dictionary.evaluate(text);
  if(match.whitelisted())return new RuleResult("WHITELISTED",List.of());
  if(match.hits().isEmpty())return new RuleResult("PASS",List.of());
  boolean block=match.hits().stream().anyMatch(hit->"BLOCK".equals(hit.mode()));
  if(policy!=null&&!"INHERIT".equals(policy.getAction())&&match.hits().stream().anyMatch(hit->"CUSTOM".equals(hit.source())))block="BLOCK".equals(policy.getAction());
  return new RuleResult(block?"BLOCK":"REVIEW",match.hits());
 }
 private SearchRiskRecorder.Saved evaluate(String text,String scene,String businessType,Long businessId,Subject actor,Long jobId,String advice,boolean manual,boolean bindByScene,java.util.function.Consumer<SearchRisk> context){
  var skipped=new SearchRiskRecorder.Saved(null,false);
  boolean scanning=manual||jobId!=null;
  Subject subject=actor==null?new Subject("UNKNOWN",null):actor;
  if(text==null||text.isBlank())return skipped;
  if(!SCENES.contains(scene))throw new IllegalArgumentException("Unknown content scene");
  if(text.length()>20000)throw new CoolException("待检测文字不能超过20000字");
  var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  var request=attributes instanceof org.springframework.web.context.request.ServletRequestAttributes a?a.getRequest():null;
  String hash=cn.hutool.crypto.digest.DigestUtil.sha256Hex(text);
  String key="contentRisk:"+scene+":"+businessType+":"+businessId+":"+subject.type()+":"+subject.id()+":"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(text.trim());
  if(bindByScene&&!scanning&&request!=null&&request.getAttribute(key)!=null)return skipped;
  var decision=rule(text,scene);
  if("DISABLED".equals(decision.action())||"WHITELISTED".equals(decision.action()))return skipped;
  var hits=decision.hits();
  if(hits.isEmpty()&&(advice==null||advice.isBlank()))return skipped;
  var row=new SearchRisk();row.setContent(text);row.setContentHash(hash);row.setScene(scene);row.setBusinessType(businessType);row.setBusinessId(businessId);
  if(context!=null)context.accept(row);
  row.setSubjectType(subject.type());row.setSubjectId(subject.id());row.setUserId("APP".equals(subject.type())?subject.id():null);
  row.setDetectionEntry(manual?"RULE_RESCAN":jobId!=null?"SCHEDULED_SCAN":"AI_OUTPUT".equals(scene)?"GENERATED_CONTENT":"SEARCH".equals(scene)?"SEARCH_INPUT":"WRITE");
  RiskOrigin.apply(row,businessType,businessId,actor);
  row.setSource(scanning?"SCAN/"+businessType:request!=null?request.getRequestURI():businessType);
  row.setMatchedWords(cn.hutool.json.JSONUtil.toJsonStr(hits.stream().map(ContentRiskDictionary.Hit::word).distinct().toList()));
  row.setMatchedDetails(cn.hutool.json.JSONUtil.toJsonStr(hits));
  boolean block="BLOCK".equals(decision.action());
  row.setDetectionResult(scanning?"SCAN_HIT":block?"BLOCKED":"ALLOWED_RECORDED");
  row.setMode(scanning?"REVIEW":block?"BLOCK":"REVIEW");row.setStatus("PENDING");row.setJobId(jobId);row.setAiAdvice(advice);
  row.setDedupKey(RiskOrigin.key(row));
  if(manual)return recorder.persistOutcome(row);
  Long id=recorder.persist(row);
  if(bindByScene&&jobId==null&&request!=null&&businessId==null&&id!=null){
   String pending="contentRisk.pending."+scene;
   @SuppressWarnings("unchecked") var ids=(java.util.List<Long>)request.getAttribute(pending);
   if(ids==null){ids=new java.util.ArrayList<>();request.setAttribute(pending,ids);}ids.add(id);
  }
  if(jobId==null&&block)throw new CoolException("SEARCH".equals(scene)?"搜索内容包含不适宜词语，请修改后重试":"内容未通过平台规则检测，请修改后重试",422);
  if(bindByScene&&request!=null&&jobId==null)request.setAttribute(key,true);
  return new SearchRiskRecorder.Saved(id,id!=null);
 }
 public void bind(String scene,Long businessId){
  var a=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
  if(!(a instanceof org.springframework.web.context.request.ServletRequestAttributes s)||businessId==null)return;
  @SuppressWarnings("unchecked") var ids=(java.util.List<Long>)s.getRequest().getAttribute("contentRisk.pending."+scene);
  if(ids==null||ids.isEmpty())return;var copy=java.util.List.copyOf(ids);s.getRequest().removeAttribute("contentRisk.pending."+scene);
  bindAfterCommit(copy,businessId);
 }
 private void bindAfterCommit(List<Long> ids,Long businessId){
  afterCommit(()->recorder.bind(ids,businessId));
 }
 private void afterCommit(Runnable update){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){update.run();}});
  else update.run();
 }
 static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
 public static List<SearchWord> matches(String text,List<SearchWord> rules){
  var hits=new ContentRiskDictionary().find(text,rules);
  return rules.stream().filter(r->hits.stream().anyMatch(h->"CUSTOM".equals(h.source())&&h.word().equals(r.getWord()))).toList();
 }
}

