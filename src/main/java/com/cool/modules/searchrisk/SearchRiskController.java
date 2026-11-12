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
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping({"/admin/search-risk","/admin/content-risk"}) @lombok.RequiredArgsConstructor
public class SearchRiskController {
 private final SearchWordMapper words; private final SearchRiskMapper records; private final SupportAccess access;
 private final SearchRiskAuditMapper audits;
 private final ContentRiskPolicyMapper policies;
 private final PlatformWarningService warnings;
 private final ContentRiskDictionary dictionary;
 private final com.cool.modules.user.mapper.UserInfoMapper appUsers;
 private final com.cool.modules.base.mapper.sys.BaseSysUserMapper workspaceUsers;
 @GetMapping("/word-sources") public R<?> wordSources(){read();return R.ok(dictionary.sources());}
 public record Policy(String scene,Boolean enabled,String action){}
 @GetMapping("/policies") public R<?> policies(){read();return R.ok(SearchRiskService.SCENES.stream().sorted().map(s->{var p=policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,s));return new Policy(s,p==null||Boolean.TRUE.equals(p.getEnabled()),p==null?"INHERIT":p.getAction());}).toList());}
 @PostMapping("/policies/save") @org.springframework.transaction.annotation.Transactional public R<?> policy(@RequestBody Policy p){
  access.platform("search:risk:manage");if(!SearchRiskService.SCENES.contains(java.util.Objects.toString(p.scene(),""))||p.enabled()==null||!java.util.Set.of("INHERIT","BLOCK","REVIEW").contains(java.util.Objects.toString(p.action(),"")))throw new CoolException("入口策略无效",400);
  var row=policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,p.scene()).forUpdate());if(row==null){row=new ContentRiskPolicy();row.setScene(p.scene());row.setEnabled(p.enabled());row.setAction(p.action());policies.insert(row);}else{row.setEnabled(p.enabled());row.setAction(p.action());policies.update(row);}audit(row.getId(),"POLICY_SAVE",p);return R.ok();
 }
 @GetMapping("/warnings/preview") public R<?> warningPreview(@RequestParam Long riskId){return R.ok(warnings.preview(riskId));}
 @PostMapping("/warnings/send") public R<?> warningSend(@RequestBody PlatformWarningService.Send input){return R.ok(warnings.send(input));}
 @GetMapping("/warnings/receipts") public R<?> warningReceipts(@RequestParam Long riskId){return R.ok(warnings.receipts(riskId));}
 private void audit(Long id,String action,Object detail){var a=new SearchRiskAudit();a.setOperatorId(CoolSecurityUtil.getCurrentUserId());a.setTargetId(id);a.setAction(action);a.setDetail(cn.hutool.json.JSONUtil.toJsonStr(detail));audits.insert(a);}
 private void read(){access.platform("search:risk:view");}
 @GetMapping("/records/detail") public R<?> detail(@RequestParam Long id){read();var row=records.selectOneById(id);if(row==null)throw new CoolException("记录不存在",404);fillAccountNames(java.util.List.of(row));return R.ok(row);}
 @GetMapping("/audit") public R<?> auditPage(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){read();SupportAccess.page(page,size);var result=audits.paginate(page,size,QueryWrapper.create().orderBy(SearchRiskAudit::getId,false));return R.ok(java.util.Map.of("list",result.getRecords(),"total",result.getTotalRow()));}
 @GetMapping("/words") public R<?> words(){read();return R.ok(words.selectAll());}
 public record WordInput(Long id,String word,String mode,Boolean enabled){}
 public record Id(Long id){}
 @PostMapping("/words/delete") @org.springframework.transaction.annotation.Transactional public R<?> delete(@RequestBody Id input){access.platform("search:risk:manage");if(input.id()==null)throw new CoolException("缺少词条编号");var word=words.selectOneById(input.id());if(word==null)throw new CoolException("词条不存在",404);words.deleteById(input.id());audit(input.id(),"WORD_DELETE",word);return R.ok();}
 @PostMapping("/words/save") @org.springframework.transaction.annotation.Transactional public R<?> save(@RequestBody WordInput p){access.platform("search:risk:manage");
 if(p.word()==null||p.word().isBlank()||p.word().length()>100||!java.util.Set.of("BLOCK","REVIEW","ALLOW").contains(p.mode()==null?"":p.mode())||p.enabled()==null)throw new CoolException("词条参数无效");
 var w=p.id()==null?new SearchWord():words.selectOneById(p.id());if(w==null)throw new CoolException("词条不存在");
 w.setWord(p.word().trim());w.setMode(p.mode());w.setEnabled(p.enabled());if(p.id()==null)words.insert(w);else words.update(w);audit(w.getId(),"WORD_SAVE",p);return R.ok(w.getId());}
 @GetMapping("/records") public R<?> records(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,
 @RequestParam(required=false) String status,@RequestParam(required=false) Long userId,@RequestParam(required=false) String scene,@RequestParam(required=false) String source,
 @RequestParam(required=false) String subjectType,@RequestParam(required=false) Long subjectId,
 @RequestParam(required=false) Long jobId,@RequestParam(required=false) String businessType,@RequestParam(required=false) Long businessId,
 @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") java.util.Date from,
 @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") java.util.Date to){
 read();SupportAccess.page(page,size);if(from!=null&&to!=null&&!from.before(to))throw new CoolException("时间范围无效",400);
 var q=QueryWrapper.create();if(status!=null&&!status.isBlank())q.eq(SearchRisk::getStatus,status);if(userId!=null)q.eq(SearchRisk::getUserId,userId);
 if(scene!=null)q.eq(SearchRisk::getScene,scene);if(source!=null)q.eq(SearchRisk::getSource,source);if(subjectType!=null)q.eq(SearchRisk::getSubjectType,subjectType);if(subjectId!=null)q.eq(SearchRisk::getSubjectId,subjectId);
 if(from!=null)q.ge(SearchRisk::getCreateTime,from);if(to!=null)q.lt(SearchRisk::getCreateTime,to);
 if(jobId!=null)q.eq(SearchRisk::getJobId,jobId);if(businessType!=null)q.eq(SearchRisk::getBusinessType,businessType);if(businessId!=null)q.eq(SearchRisk::getBusinessId,businessId);
 var p=records.paginate(page,size,q.orderBy(SearchRisk::getId,false));fillAccountNames(p.getRecords());return R.ok(new Result(p.getRecords(),p.getTotalRow()));
 }
 // Resolve each identity namespace separately: app and workspace IDs can overlap.
 private void fillAccountNames(java.util.List<SearchRisk> rows){
  var appIds=new java.util.HashSet<Long>();var workspaceIds=new java.util.HashSet<Long>();
  for(var row:rows){
   var type=subjectType(row);var id=subjectId(row);
   if(id!=null){if("APP".equals(type))appIds.add(id);else if("ADMIN".equals(type)||"MERCHANT".equals(type))workspaceIds.add(id);}
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
    case "ADMIN","MERCHANT" -> id==null?"来源待核实":workspaceNames.getOrDefault(id,"账号已删除或不存在");
    case "SYSTEM" -> "系统输出（不归责用户）";
    case "GUEST" -> "未登录访客";
    default -> "来源待核实";
   };
   row.setSubjectName(name);
   row.setOperatorName(row.getOperatorId()==null?null:workspaceNames.getOrDefault(row.getOperatorId(),"账号已删除或不存在"));
  }
 }
 private static String subjectType(SearchRisk row){
  if("AI_OUTPUT".equals(row.getScene()))return "SYSTEM";
  return row.getSubjectType()==null||row.getSubjectType().isBlank()?(row.getUserId()==null?"GUEST":"APP"):row.getSubjectType();
 }
 private static Long subjectId(SearchRisk row){return row.getSubjectId()!=null?row.getSubjectId():"APP".equals(subjectType(row))?row.getUserId():null;}
 private static String displayName(String name,String nickname){return name!=null&&!name.isBlank()?name.trim():nickname!=null&&!nickname.isBlank()?nickname.trim():"未设置姓名";}
 public record Result(java.util.List<SearchRisk> list,long total){}
 public record Resolution(Long id,String status,String reason){}
 @PostMapping("/records/resolve") @org.springframework.transaction.annotation.Transactional public R<?> resolve(@RequestBody Resolution p){access.platform("search:risk:manage");if(p.id()==null)throw new CoolException("缺少记录编号",400);if(!java.util.Set.of("IGNORED","FALSE_POSITIVE","CONFIRMED").contains(p.status()==null?"":p.status())||p.reason()==null||p.reason().isBlank()||p.reason().length()>200)throw new CoolException("请填写处理状态和原因");var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getId,p.id()).forUpdate());if(row==null)throw new CoolException("记录不存在");if(!"PENDING".equals(row.getStatus()))throw new CoolException("记录已处理",409);row.setStatus(p.status());row.setReason(p.reason());row.setOperatorId(CoolSecurityUtil.getCurrentUserId());row.setHandledAt(new java.util.Date());records.update(row);audit(row.getId(),"RESOLVE",p);return R.ok();}
 public record Stats(long pendingRecords,long pendingAccounts,long confirmedAccounts){}
 public record Dashboard(long pendingRecords,long pendingAccounts,long todayBlocked,long bannedAccounts){}
 @GetMapping("/dashboard") public R<?> dashboard(){
  read();var zone=java.time.ZoneId.of("Asia/Shanghai");var day=java.time.LocalDate.now(zone);var start=java.util.Date.from(day.atStartOfDay(zone).toInstant());var end=java.util.Date.from(day.plusDays(1).atStartOfDay(zone).toInstant());
  long banned=appUsers.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.user.entity.UserInfoEntity::getStatus,0))+workspaceUsers.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.base.entity.sys.BaseSysUserEntity::getStatus,0));
  return R.ok(new Dashboard(records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getStatus,"PENDING")),accountCount("PENDING"),records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getMode,"BLOCK").ge(SearchRisk::getCreateTime,start).lt(SearchRisk::getCreateTime,end)),banned));
 }
 public long accountCount(String status){return records.paginate(1,1,QueryWrapper.create().select(SearchRisk::getSubjectType,SearchRisk::getSubjectId).eq(SearchRisk::getStatus,status).isNotNull(SearchRisk::getSubjectId).in(SearchRisk::getSubjectType,java.util.List.of("APP","MERCHANT","ADMIN")).groupBy(SearchRisk::getSubjectType,SearchRisk::getSubjectId)).getTotalRow();}
 @GetMapping("/stats") public R<?> stats(){read();return R.ok(new Stats(records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getStatus,"PENDING")),accountCount("PENDING"),accountCount("CONFIRMED")));}
}
