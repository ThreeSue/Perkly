package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 搜索风险检测与运营复核
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:00:23
 */
import com.cool.core.request.R;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.support.service.SupportAccess;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping({"/admin/search-risk","/admin/content-risk"}) @lombok.RequiredArgsConstructor
public class SearchRiskController {
 private final com.fasterxml.jackson.databind.ObjectMapper auditJson;
 private final SearchRiskMapper records; private final SupportAccess access;
 private final SearchRiskAuditMapper audits;
 private final ContentRiskPolicyMapper policies;
 private final PlatformWarningService warnings;
 private final com.cool.modules.user.mapper.UserInfoMapper appUsers;
 private final com.cool.modules.base.mapper.sys.BaseSysUserMapper workspaceUsers;
 private final com.cool.modules.merchant.mapper.MerchantInfoMapper merchants;
 private final com.cool.modules.merchant.mapper.MerchantStoreMapper stores;
 private final com.cool.modules.coupon.mapper.CouponTemplateMapper coupons;
 private final com.cool.modules.approval.mapper.MerchantApplicationMapper applications;
 private final com.cool.modules.support.mapper.StoreChatMapper chats;
 private final com.cool.modules.support.mapper.StoreChatMessageMapper messages;
 private final com.cool.modules.support.mapper.UserFeedbackMapper feedback;
 private final com.cool.modules.workspace.mapper.WorkspaceNoticeMapper notices;
 private final PlatformWarningMapper warningRows;
 private final RiskChatContext chatContext;
 public record Policy(String scene,Boolean enabled,String action){}
 @GetMapping("/policies") public R<?> policies(){read();return R.ok(SearchRiskService.SCENES.stream().sorted().map(s->{var p=policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,s));return new Policy(s,p==null||Boolean.TRUE.equals(p.getEnabled()),p==null?"INHERIT":p.getAction());}).toList());}
 @PostMapping("/policies/save") @org.springframework.transaction.annotation.Transactional public R<?> policy(@RequestBody Policy p){
  access.platform("search:risk:manage");if(!SearchRiskService.SCENES.contains(java.util.Objects.toString(p.scene(),""))||p.enabled()==null||!java.util.Set.of("INHERIT","BLOCK","REVIEW").contains(java.util.Objects.toString(p.action(),"")))throw new CoolException("入口策略无效",400);
  var row=policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,p.scene()).forUpdate());if(row==null){row=new ContentRiskPolicy();row.setScene(p.scene());row.setEnabled(p.enabled());row.setAction(p.action());policies.insert(row);}else{row.setEnabled(p.enabled());row.setAction(p.action());policies.update(row);}audit(row.getId(),"POLICY_SAVE",p);return R.ok();
 }
 @GetMapping("/warnings/preview") public R<?> warningPreview(@RequestParam Long riskId){return R.ok(warnings.preview(riskId));}
 @PostMapping("/warnings/send") public R<?> warningSend(@RequestBody PlatformWarningService.Send input){return R.ok(warnings.send(input));}
 @GetMapping("/warnings/receipts") public R<?> warningReceipts(@RequestParam Long riskId){return R.ok(warnings.receipts(riskId));}
 private void audit(Long id,String action,Object detail){var a=new SearchRiskAudit();a.setOperatorId(CoolSecurityUtil.getCurrentUserId());a.setTargetId(id);a.setAction(action);a.setDetail(auditJson.valueToTree(detail).toString());audits.insert(a);}
 private void read(){access.platform("search:risk:view");}
 @GetMapping("/records/detail") public R<?> detail(@RequestParam Long id){
  read();var row=records.selectOneById(id);if(row==null)throw new CoolException("记录不存在",404);
  if(row.getChatContext()==null&&"CHAT".equals(row.getOriginType())&&"VERIFIED".equals(row.getProvenanceStatus())&&row.getOriginId()!=null){
   var message=messages.selectOneById(row.getOriginId());
   var chat=message==null?null:chats.selectOneById(message.getChatId());
   if(chat!=null)row.setChatContext(chatContext.historical(chat,message.getRound()));
  }
  fillAccountNames(java.util.List.of(row));fillRelatedName(row);return R.ok(row);
 }
 private void fillRelatedName(SearchRisk row){
  if(row.getOriginName()!=null||row.getOriginId()==null||!"VERIFIED".equals(row.getProvenanceStatus()))return;
  Long id=row.getOriginId();
  switch(Objects.toString(row.getOriginType(),"")){
   case "FEEDBACK" -> {
    var item=feedback.selectOneById(id);
    var customer=item==null?null:appUsers.selectOneById(item.getCustomerId());
    if(customer!=null)row.setOriginName("用户反馈 · "+displayName(customer.getNickName(), null));
   }
   case "NOTICE" -> {var item=notices.selectOneById(id);if(item!=null)row.setOriginName(item.getTitle());}
   case "PLATFORM_WARNING" -> {var item=warningRows.selectOneById(id);if(item!=null)row.setOriginName(item.getTitle());}
   case "PROFILE" -> {
    if("workspaceNickName".equals(row.getOriginField())){
     var item=workspaceUsers.selectOneById(id);if(item!=null)row.setOriginName(displayName(item.getName(),item.getNickName()));
    }else if("nickName".equals(row.getOriginField())){
     var item=appUsers.selectOneById(id);if(item!=null)row.setOriginName(displayName(item.getNickName(),null));
    }
   }
   default -> {}
  }
 }
 public record AuditView(Long id,java.util.Date createTime,Long operatorId,Long targetId,String action,String detail,String operatorName,String targetName){}
 private AuditView auditView(SearchRiskAudit row){
  var actor=row.getOperatorId()==null?null:workspaceUsers.selectOneById(row.getOperatorId());
  String actorName=row.getOperatorId()==null?"系统任务":actor==null?"账号已删除或不存在":displayName(actor.getName(),actor.getNickName());
  cn.hutool.json.JSONObject data=new cn.hutool.json.JSONObject();
  try{data=cn.hutool.json.JSONUtil.parseObj(row.getDetail());}catch(Exception ignored){}
  String target;
  switch(java.util.Objects.toString(row.getAction(),"")){
   case "WORD_SAVE","WORD_DELETE" -> target="词条："+data.getStr("word","历史词条（名称未记录）");
   case "WORD_IMPORT" -> target="导入词库："+data.getStr("sourceVersion","houbb");
   case "POLICY_SAVE" -> target="检测入口："+data.getStr("scene","未知入口");
   case "USER_STATUS" -> {
    var user=row.getTargetId()==null?null:appUsers.selectOneById(row.getTargetId());
    target="用户："+(user==null?"账号已删除或不存在":displayName(user.getNickName(),null));
   }
   case "RESOLVE","WARNING_SEND","HISTORY_UPDATE_ORIGIN","HISTORY_MERGE","ORIGIN_BIND_MERGE" -> {
    var risk=row.getTargetId()==null?null:records.selectOneById(row.getTargetId());
    String content=risk==null?"记录已删除或不存在":java.util.Objects.toString(risk.getContent(),"").replaceAll("\\s+"," ");
    target="风险内容："+(content.length()>60?content.substring(0,60)+"…":content);
   }
   case "SCHEDULE_SAVE","SCHEDULE_CONFIG" -> target="定时巡检计划";
   case "HISTORY_RESCAN" -> target="重扫资料："+data.getStr("source","未知来源");
   case "JOB_START","JOB_RETRY" -> target="巡检任务："+data.getStr("source","未知来源");
   default -> target="历史操作对象（未记录名称）";
  }
  return new AuditView(row.getId(),row.getCreateTime(),row.getOperatorId(),row.getTargetId(),row.getAction(),row.getDetail(),actorName,target);
 }
 @GetMapping("/audit") public R<?> auditPage(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){read();SupportAccess.page(page,size);var result=audits.paginate(page,size,QueryWrapper.create().orderBy(SearchRiskAudit::getId,false));return R.ok(java.util.Map.of("list",result.getRecords().stream().map(this::auditView).toList(),"total",result.getTotalRow()));}
 @GetMapping("/records") public R<?> records(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
 @RequestParam(required=false) String status,@RequestParam(required=false) Long userId,@RequestParam(required=false) String scene,@RequestParam(required=false) String source,
 @RequestParam(required=false) String subjectType,@RequestParam(required=false) Long subjectId,@RequestParam(required=false) String matchedWord,@RequestParam(required=false) Long matchedWordId,
 @RequestParam(required=false) Long jobId,@RequestParam(required=false) String businessType,@RequestParam(required=false) Long businessId,
 @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") java.util.Date from,
 @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") java.util.Date to){
 read();SupportAccess.page(page,size);if(from!=null&&to!=null&&!from.before(to))throw new CoolException("时间范围无效",400);
 var q=QueryWrapper.create();if(status!=null&&!status.isBlank())q.eq(SearchRisk::getStatus,status);if(userId!=null)q.eq(SearchRisk::getUserId,userId);
 if("PENDING".equals(status))q.isNull(SearchRisk::getDuplicateOfId);
 if(matchedWord!=null&&matchedWord.length()>100||matchedWordId!=null&&matchedWordId<1)throw new CoolException("命中词筛选无效",400);
 if(matchedWordId!=null){
  q.and("(JSON_CONTAINS(IF(JSON_VALID(matched_details), matched_details, '[]'), JSON_OBJECT('wordId', CAST(? AS UNSIGNED))) OR "+legacyWordFilter()+")",
    matchedWordId,matchedWord);
 }else if(matchedWord!=null&&!matchedWord.isBlank()){
  q.and("JSON_CONTAINS(IF(JSON_VALID(matched_words), matched_words, '[]'), JSON_QUOTE(?))",matchedWord);
 }
 if(scene!=null)q.eq(SearchRisk::getScene,scene);if(source!=null)q.eq(SearchRisk::getSource,source);if(subjectType!=null)q.eq(SearchRisk::getSubjectType,subjectType);if(subjectId!=null)q.eq(SearchRisk::getSubjectId,subjectId);
 if(from!=null)q.ge(SearchRisk::getCreateTime,from);if(to!=null)q.lt(SearchRisk::getCreateTime,to);
 if(jobId!=null)q.eq(SearchRisk::getJobId,jobId);if(businessType!=null)q.eq(SearchRisk::getBusinessType,businessType);if(businessId!=null)q.eq(SearchRisk::getBusinessId,businessId);
 var p=records.paginate(page,size,q.orderBy(SearchRisk::getId,false));fillAccountNames(p.getRecords());return R.ok(new Result(p.getRecords(),p.getTotalRow()));
 }
 private static String legacyWordFilter(){
  return "(NOT JSON_CONTAINS_PATH(IF(JSON_VALID(matched_details), matched_details, '[]'), 'one', '$[*].wordId')"
   +" AND JSON_CONTAINS(IF(JSON_VALID(matched_words), matched_words, '[]'), JSON_QUOTE(?)))";
 }
 // Resolve each identity namespace separately: app and workspace IDs can overlap.
 private void fillAccountNames(java.util.List<SearchRisk> rows){
  fillOriginNames(rows);
  var appIds=new java.util.HashSet<Long>();var workspaceIds=new java.util.HashSet<Long>();
  for(var row:rows){
   row.setDetectionResult(row.effectiveDetectionResult());
  var type=subjectType(row);var id=subjectId(row);
   if(id!=null){if("APP".equals(type))appIds.add(id);else if("ADMIN".equals(type)||"MERCHANT".equals(type)||"WORKSPACE".equals(type))workspaceIds.add(id);}
   if(row.getOperatorId()!=null)workspaceIds.add(row.getOperatorId());
  }
  var appNames=new java.util.HashMap<Long,String>();var workspaceNames=new java.util.HashMap<Long,String>();
  if(!appIds.isEmpty())for(var user:appUsers.selectListByQuery(QueryWrapper.create().in(com.cool.modules.user.entity.UserInfoEntity::getId,appIds)))
   appNames.put(user.getId(),displayName(user.getNickName(),null));
  if(!workspaceIds.isEmpty())for(var user:workspaceUsers.selectListByQuery(QueryWrapper.create().in(com.cool.modules.base.entity.sys.BaseSysUserEntity::getId,workspaceIds)))
   workspaceNames.put(user.getId(),displayName(user.getName(),user.getNickName()));
  for(var row:rows){
   var type=subjectType(row);var id=subjectId(row);
   var name=switch(type){
    case "APP" -> id==null?"未登录访客":appNames.getOrDefault(id,"账号已删除或不存在");
    case "ADMIN","MERCHANT","WORKSPACE" -> id==null?"来源待核实":workspaceNames.getOrDefault(id,"账号已删除或不存在");
    case "SYSTEM" -> "系统输出（不归责用户）";
    case "GUEST" -> "未登录访客";
    default -> "来源待核实";
   };
   row.setSubjectName(name);
   row.setOperatorName(row.getOperatorId()==null?null:workspaceNames.getOrDefault(row.getOperatorId(),"账号已删除或不存在"));
  }
 }
 private void fillOriginNames(java.util.List<SearchRisk> rows){
  var merchantIds=new java.util.HashSet<Long>();var storeIds=new java.util.HashSet<Long>();
  var couponIds=new java.util.HashSet<Long>();var applicationIds=new java.util.HashSet<Long>();
  for(var row:rows){
   if(!"VERIFIED".equals(row.getProvenanceStatus())||row.getOriginId()==null)continue;
   if("MERCHANT".equals(row.getOriginType()))merchantIds.add(row.getOriginId());
   if("STORE".equals(row.getOriginType()))storeIds.add(row.getOriginId());
   if("COUPON".equals(row.getOriginType()))couponIds.add(row.getOriginId());
   if("MERCHANT_APPLICATION".equals(row.getOriginType()))applicationIds.add(row.getOriginId());
  }
  var merchantNames=new java.util.HashMap<Long,String>();var storeNames=new java.util.HashMap<Long,String>();
  var couponNames=new java.util.HashMap<Long,String>();var applicationNames=new java.util.HashMap<Long,String>();
  var owners=new java.util.HashMap<Long,Long>();var storeOwners=new java.util.HashMap<Long,Long>();
  if(!storeIds.isEmpty())for(var row:stores.selectListByQuery(QueryWrapper.create().in(com.cool.modules.merchant.entity.MerchantStoreEntity::getId,storeIds))){
   storeNames.put(row.getId(),row.getName());storeOwners.put(row.getId(),row.getMerchantId());
   if(row.getMerchantId()!=null)merchantIds.add(row.getMerchantId());
  }
  if(!couponIds.isEmpty())for(var row:coupons.selectListByQuery(QueryWrapper.create().in(com.cool.modules.coupon.entity.CouponTemplateEntity::getId,couponIds))){
   couponNames.put(row.getId(),row.getName());owners.put(row.getId(),row.getMerchantId());
   if(row.getMerchantId()!=null)merchantIds.add(row.getMerchantId());
  }
  if(!merchantIds.isEmpty())for(var row:merchants.selectListByQuery(QueryWrapper.create().in(com.cool.modules.merchant.entity.MerchantInfoEntity::getId,merchantIds)))merchantNames.put(row.getId(),row.getName());
  if(!applicationIds.isEmpty())for(var row:applications.selectListByQuery(QueryWrapper.create().in(com.cool.modules.approval.entity.MerchantApplicationEntity::getId,applicationIds)))applicationNames.put(row.getId(),row.getMerchantName());
  for(var row:rows){
   if("MERCHANT".equals(row.getOriginType()))row.setOriginName(merchantNames.get(row.getOriginId()));
   if("STORE".equals(row.getOriginType())){
    row.setOriginName(storeNames.get(row.getOriginId()));row.setOriginMerchantName(merchantNames.get(storeOwners.get(row.getOriginId())));
   }
   if("COUPON".equals(row.getOriginType())){
    row.setOriginName(couponNames.get(row.getOriginId()));row.setOriginMerchantName(merchantNames.get(owners.get(row.getOriginId())));
    if(row.getCouponContext()!=null){
     row.setOriginName(row.getCouponContext().couponName());row.setOriginMerchantName(row.getCouponContext().merchantName());
    }
   }
   if(row.getChatContext()!=null)row.setOriginName(row.getChatContext().storeName());
   if("MERCHANT_APPLICATION".equals(row.getOriginType()))row.setOriginName(applicationNames.get(row.getOriginId()));
  }
 }
 private static String subjectType(SearchRisk row){
  if(row.getSubmittedByType()!=null)return row.getSubmittedByType();
  if("RULE_RESCAN".equals(row.getDetectionEntry())||"SCHEDULED_SCAN".equals(row.getDetectionEntry())
     ||row.getJobId()!=null||"AI_OUTPUT".equals(row.getScene()))return "UNKNOWN";
  return row.getSubjectType()==null||row.getSubjectType().isBlank()?"UNKNOWN":row.getSubjectType();
 }
 private static Long subjectId(SearchRisk row){return row.getSubmittedByType()!=null?row.getSubmittedById():row.getSubjectId()!=null?row.getSubjectId():"APP".equals(subjectType(row))?row.getUserId():null;}
 private static String displayName(String name,String nickname){return name!=null&&!name.isBlank()?name.trim():nickname!=null&&!nickname.isBlank()?nickname.trim():"未设置姓名";}
 public record Result(java.util.List<SearchRisk> list,long total){}
 public record Resolution(Long id,String status,String reason){}
 static String reviewReason(String status,String reason){
  if(!java.util.Set.of("IGNORED","FALSE_POSITIVE","CONFIRMED").contains(java.util.Objects.toString(status,"")))throw new CoolException("处理状态无效",400);
  if(reason!=null && reason.length()>200)throw new CoolException("原因最多200字",400);
  return reason==null || reason.isBlank()?null:reason.trim();
 }
 private void applyResolution(SearchRisk row,String status,String reason){
  applyResolution(row,status,reason,new Resolution(row.getId(),status,reason));
 }
 private void applyResolution(SearchRisk row,String status,String reason,Object auditDetail){
  row.setStatus(status);row.setReason(reason);row.setOperatorId(CoolSecurityUtil.getCurrentUserId());row.setHandledAt(new java.util.Date());
  records.update(row,false);audit(row.getId(),"RESOLVE",auditDetail);
 }
 @PostMapping("/records/resolve") @org.springframework.transaction.annotation.Transactional
 public R<?> resolve(@RequestBody Resolution p){
  access.platform("search:risk:manage");if(p==null || p.id()==null)throw new CoolException("缺少记录编号",400);
  String reason=reviewReason(p.status(),p.reason());
  var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getId,p.id()).forUpdate());
  if(row==null)throw new CoolException("记录不存在",404);validateResolution(row);
  applyResolution(row,p.status(),reason);return R.ok();
 }
 public record BatchResolution(java.util.List<Long> ids,String status,String reason){}
 @PostMapping("/records/resolve-batch") @org.springframework.transaction.annotation.Transactional
 public R<?> resolveBatch(@RequestBody BatchResolution p){
  access.platform("search:risk:manage");
  if(p==null)throw new CoolException("请选择1至100条不同记录",400);
  String reason=reviewReason(p.status(),p.reason());
  var rows=lockPendingRows(p.ids());
  rows.forEach(row->applyResolution(row,p.status(),reason));
  return R.ok(Map.of("resolvedCount",rows.size(),"ids",p.ids()));
 }

 public record AiResolutionItem(Long id,String expectedAdvice){}
 public record AiBatchResolution(List<AiResolutionItem> items){}
 public record AiResolutionAudit(Long id,String status,String reason,String aiAdvice){}

 @PostMapping("/records/resolve-by-ai") @org.springframework.transaction.annotation.Transactional
 public R<?> resolveByAi(@RequestBody AiBatchResolution input){
  access.platform("search:risk:manage");
  if (input==null || input.items()==null || input.items().size()>100 || input.items().stream().anyMatch(item ->
      item==null || item.expectedAdvice()==null || item.expectedAdvice().isBlank() || item.expectedAdvice().length()>500)) {
   throw new CoolException("请选择带有 AI 建议的记录",400);
  }
  var ids=input.items().stream().map(AiResolutionItem::id).toList();
  var rows=lockPendingRows(ids);
  var expectedAdvice=new HashMap<Long,String>();
  input.items().forEach(item->expectedAdvice.put(item.id(),item.expectedAdvice()));

  var decisions=new ArrayList<AiResolutionAudit>();
  for (var row:rows) {
   String status=row.getAiSuggestedStatus();
   if (status==null || !Objects.equals(row.getAiAdvice(),expectedAdvice.get(row.getId()))) {
    throw new CoolException("所选记录的 AI 建议已变化或仍需人工核实，请刷新后重新选择",409);
   }
   decisions.add(new AiResolutionAudit(row.getId(),status,"采纳 AI 预处理建议",row.getAiAdvice()));
  }
  for (int index=0;index<rows.size();index++) {
   var decision=decisions.get(index);
   applyResolution(rows.get(index),decision.status(),decision.reason(),decision);
  }
  return R.ok(Map.of("resolvedCount",rows.size(),"ids",ids));
 }

 private List<SearchRisk> lockPendingRows(List<Long> ids){
  if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id->id==null||id<1)
     || ids.stream().distinct().count()!=ids.size())throw new CoolException("请选择1至100条不同记录",400);
  var rows=new ArrayList<SearchRisk>();
  for(Long id:ids.stream().sorted().toList()){
   var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getId,id).forUpdate());
   if(row==null)throw new CoolException("记录不存在",404);validateResolution(row);rows.add(row);
  }
  return rows;
 }
 private void validateResolution(SearchRisk row){
  if(row.getDuplicateOfId()!=null)throw new CoolException("该风险已归并，请处理主记录",409);
  if(!"PENDING".equals(row.getStatus()))throw new CoolException("记录已处理，请刷新",409);
 }
 public record Stats(long pendingRecords,long pendingAccounts,long confirmedAccounts){}
 public record Dashboard(long pendingRecords,long pendingAccounts,long todayBlocked,long bannedAccounts){}
 @GetMapping("/dashboard") public R<?> dashboard(){
  read();var zone=java.time.ZoneId.of("Asia/Shanghai");var day=java.time.LocalDate.now(zone);var start=java.util.Date.from(day.atStartOfDay(zone).toInstant());var end=java.util.Date.from(day.plusDays(1).atStartOfDay(zone).toInstant());
  long banned=appUsers.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.user.entity.UserInfoEntity::getStatus,0))+workspaceUsers.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.base.entity.sys.BaseSysUserEntity::getStatus,0));
  return R.ok(new Dashboard(pendingCount(),accountCount("PENDING"),records.selectCountByQuery(QueryWrapper.create().isNull(SearchRisk::getDuplicateOfId).eq(SearchRisk::getMode,"BLOCK").ge(SearchRisk::getCreateTime,start).lt(SearchRisk::getCreateTime,end)),banned));
 }
 private long pendingCount(){return records.selectCountByQuery(QueryWrapper.create().isNull(SearchRisk::getDuplicateOfId).eq(SearchRisk::getStatus,"PENDING"));}
 public long accountCount(String status){return records.paginate(1,1,QueryWrapper.create().select(SearchRisk::getSubjectType,SearchRisk::getSubjectId).isNull(SearchRisk::getDuplicateOfId).eq(SearchRisk::getStatus,status).isNotNull(SearchRisk::getSubjectId).in(SearchRisk::getSubjectType,java.util.List.of("APP","MERCHANT","ADMIN")).groupBy(SearchRisk::getSubjectType,SearchRisk::getSubjectId)).getTotalRow();}
 @GetMapping("/stats") public R<?> stats(){read();return R.ok(new Stats(pendingCount(),accountCount("PENDING"),accountCount("CONFIRMED")));}
}
