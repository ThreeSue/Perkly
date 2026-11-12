import com.cool.CoolApplication;
import com.cool.core.cache.CoolCache;
import com.cool.core.security.jwt.*;
import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.searchrisk.*;
import com.cool.modules.searchrisk.mapper.*;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

public class ContentRiskDevServer {
 static ObjectMapper json=new ObjectMapper();
 static HttpClient http=HttpClient.newHttpClient();
 static JsonNode request(String method,String path,String token,Object body) throws Exception {
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8001"+path)).timeout(java.time.Duration.ofSeconds(20)).header("Authorization",token);
  if(body==null)b.GET();else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());return json.readTree(r.body());
 }
 static JsonNode ok(String method,String path,String token,Object body) throws Exception {var r=request(method,path,token,body);if(r.path("code").asInt()!=1000)throw new IllegalStateException("Smoke request failed: "+path+" code="+r.path("code"));return r.path("data");}
 public static void main(String[] args){
  var app=new SpringApplication(CoolApplication.class);
  app.addListeners((ApplicationListener<ApplicationReadyEvent>) e->{
   new Thread(()->{try{smoke(e.getApplicationContext());System.out.println("CONTENT_RISK_LIVE_SMOKE_PASS");}catch(Exception failure){System.err.println("CONTENT_RISK_LIVE_SMOKE_FAILED "+failure.getClass().getSimpleName()+": "+failure.getMessage());}},"content-risk-smoke").start();
  });
  app.run(args);
 }
 static void smoke(org.springframework.context.ConfigurableApplicationContext c) throws Exception {
  try(var db=c.getBean(javax.sql.DataSource.class).getConnection()){if(!"cool_test".equals(db.getCatalog()))throw new IllegalStateException("Smoke only allows cool_test");}
  var users=c.getBean(UserInfoMapper.class);var admins=c.getBean(BaseSysUserMapper.class);var cache=c.getBean(CoolCache.class);var tokens=c.getBean(JwtTokenUtil.class);
  var risks=c.getBean(SearchRiskMapper.class);var words=c.getBean(SearchWordMapper.class);var warnings=c.getBean(PlatformWarningMapper.class);var audits=c.getBean(SearchRiskAuditMapper.class);var jobs=c.getBean(ContentRiskJobMapper.class);
  String key="smoke-risk-"+UUID.randomUUID();
  var admin=new BaseSysUserEntity();admin.setUsername("risk_smoke_"+UUID.randomUUID());admin.setName("风险接口验证");admin.setNickName("临时验证");admin.setPassword(UUID.randomUUID().toString());admin.setStatus(1);admin.setPasswordV(1);admins.insertSelective(admin);
  var user=new UserInfoEntity();user.setNickName("风险接口临时验证");user.setStatus(1);users.insertSelective(user);
  var other=new UserInfoEntity();other.setNickName("风险隔离临时验证");other.setStatus(1);users.insertSelective(other);
  Long wordId=null,jobId=null;String ak="admin:userDetails:"+admin.getUsername(),vk="admin:passwordVersion:"+admin.getId();
  try{
   var perms=List.of("ROLE_ADMIN","search:risk:view","search:risk:manage","notice:send","user:info:update").stream().map(x->(org.springframework.security.core.GrantedAuthority)new SimpleGrantedAuthority(x)).toList();
   cache.set(ak,new JwtUser(admin.getId(),admin.getUsername(),"",perms,true),300);cache.set(vk,1,300);
   String at=tokens.generateToken(new HashMap<>(Map.of("userType","ADMIN","userId",admin.getId(),"username",admin.getUsername(),"passwordVersion",1)));
   String ut=appToken(user,cache,tokens),ot=appToken(other,cache,tokens);
   ok("GET","/admin/content-risk/dashboard",at,null);ok("GET","/admin/content-risk/policies",at,null);ok("GET","/admin/content-risk/jobs/status",at,null);ok("GET","/app/platform/notices/page",ut,null);
   var dashboard=ok("GET","/admin/content-risk/dashboard",at,null);
   if(!dashboard.has("todayBlocked")||!dashboard.has("bannedAccounts")||dashboard.has("confirmedAccounts"))throw new IllegalStateException("Dashboard contract mismatch");
   ok("GET","/admin/content-risk/word-sources",at,null);
   var schedule=ok("GET","/admin/content-risk/schedule",at,null);
   if(schedule.path("enabled").asBoolean())throw new IllegalStateException("Paid scheduler must remain disabled during dev smoke");
   if(request("GET","/admin/content-risk/records",ut,null).path("code").asInt()!=403)throw new IllegalStateException("APP accessed operator raw content");
   wordId=ok("POST","/admin/content-risk/words/save",at,Map.of("word",key,"mode","BLOCK","enabled",true)).asLong();
   var blocked=request("POST","/app/support/feedback/submit",ut,Map.of("requestId","smoke_feedback_001","content",key,"images",List.of()));
   if(blocked.path("code").asInt()!=422)throw new IllegalStateException("Feedback was not blocked");
   var row=risks.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key).eq(SearchRisk::getSubjectId,user.getId()));
   if(row==null)throw new IllegalStateException("Rollback lost risk record");
   ok("GET","/admin/content-risk/records?subjectType=APP&subjectId="+user.getId(),at,null);
   ok("POST","/admin/content-risk/records/resolve",at,Map.of("id",row.getId(),"status","CONFIRMED","reason","开发接口验证"));
   ok("GET","/admin/content-risk/warnings/preview?riskId="+row.getId(),at,null);
   var input=Map.of("riskId",row.getId(),"requestId","smoke_warning_001","title","开发验证提醒","content","此为临时接口验证，确认仅代表知悉。","noticeType","STRONG");
   long nid=ok("POST","/admin/content-risk/warnings/send",at,input).path("id").asLong();
   if(ok("POST","/admin/content-risk/warnings/send",at,input).path("id").asLong()!=nid)throw new IllegalStateException("Warning idempotency failed");
   if(request("GET","/app/platform/notices/detail?id="+nid,ot,null).path("code").asInt()!=404)throw new IllegalStateException("Notice isolation failed");
   ok("GET","/app/platform/notices/pending",ut,null);ok("POST","/app/platform/notices/read",ut,Map.of("id",nid));ok("POST","/app/platform/notices/confirm",ut,Map.of("id",nid));
   ok("GET","/admin/content-risk/warnings/receipts?riskId="+row.getId(),at,null);
   ok("POST","/admin/user/info/update",at,Map.of("id",user.getId(),"status",0,"banReason","开发验证临时停用"));
   var denied=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8001/app/platform/notices/unread")).header("Authorization",ut).GET().build(),HttpResponse.BodyHandlers.discarding());
   if(denied.statusCode()!=403)throw new IllegalStateException("Existing token was not banned");
   ok("POST","/admin/user/info/update",at,Map.of("id",user.getId(),"status",1));ok("GET","/app/platform/notices/unread",ut,null);
   jobId=ok("POST","/admin/content-risk/jobs/start",at,Map.of("source","PROFILE","afterId",user.getId()-1,"limitCount",1,"modelBudget",0)).path("id").asLong();
   boolean done=false;for(int i=0;i<20;i++){var state=ok("GET","/admin/content-risk/jobs/status?id="+jobId,at,null).path("state").asText();if("COMPLETED".equals(state)){done=true;break;}if("FAILED".equals(state))throw new IllegalStateException("Scan task failed");Thread.sleep(250);}
   if(!done)throw new IllegalStateException("Scan task did not finish");
  }finally{
   for(var row:risks.selectListByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key))){
    warnings.deleteByQuery(QueryWrapper.create().eq(PlatformWarning::getRiskId,row.getId()));audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,row.getId()).in(SearchRiskAudit::getAction,List.of("RESOLVE","WARNING_SEND")));risks.deleteById(row.getId());
   }
   if(wordId!=null){words.deleteById(wordId);audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,wordId).eq(SearchRiskAudit::getAction,"WORD_SAVE"));}
   if(jobId!=null){jobs.deleteById(jobId);audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,jobId).eq(SearchRiskAudit::getAction,"JOB_START"));}
   audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,user.getId()).eq(SearchRiskAudit::getAction,"USER_STATUS"));
   cache.del(ak,vk,"app:userDetails:"+user.getId(),"app:userDetails:"+other.getId());users.deleteById(user.getId());users.deleteById(other.getId());admins.deleteById(admin.getId());
  }
 }
 static String appToken(UserInfoEntity u,CoolCache cache,JwtTokenUtil tokens){
  cache.set("app:userDetails:"+u.getId(),new JwtUser(u.getId(),List.of(new SimpleGrantedAuthority("ROLE_APP")),true),300);
  return tokens.generateToken(new HashMap<>(Map.of("userType","APP","userId",u.getId(),"credentialVersion",com.cool.modules.user.util.UserPasswordUtil.credentialVersion(u))));
 }
}

