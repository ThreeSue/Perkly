package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 隔离数据库验证风险记录不随业务回滚
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:40:00
 */
@SpringBootTest(properties={"perkly.profile.worker-enabled=false","perkly.stamp.scheduler-enabled=false","perkly.support.ai-dispatch-enabled=false","perkly.content-risk.dispatch-enabled=false","perkly.ai.worker-enabled=false"}) @ActiveProfiles("local")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
class SearchRiskPersistenceTest {
 static {if(!java.util.Objects.toString(System.getenv("SPRING_DATASOURCE_URL"),"").contains("/perkly_ai_regression_20261106?"))throw new IllegalStateException("Isolated regression DB required");}
 @Autowired SearchRiskService service; @Autowired SearchWordMapper words; @Autowired SearchRiskMapper records; @Autowired TransactionTemplate tx;
 @Autowired javax.sql.DataSource dataSource;
 @Autowired SearchRiskController controller;
 @Autowired SearchRiskAuditMapper audits;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @Autowired PlatformWarningMapper warnings;
 @Autowired PlatformWarningService warningService;
 @Autowired com.cool.modules.user.mapper.UserInfoMapper customers;
 @Autowired com.cool.modules.support.mapper.UserFeedbackMapper feedback;
 @Autowired ContentRiskJobs jobs;
 @Autowired ContentRiskJobMapper jobRows;
 @Autowired ContentRiskPolicyMapper policies;
 @Autowired ContentRiskScheduleMapper schedules;
 @Autowired ContentRiskScheduleState scheduleState;
 @Autowired ContentRiskScheduler scheduler;
 @Autowired com.cool.modules.base.mapper.sys.BaseSysUserMapper workspaceUsers;
 @Autowired com.cool.modules.merchant.mapper.MerchantInfoMapper merchants;
 @Autowired com.cool.modules.workspace.mapper.WorkspaceNoticeMapper workspaceNotices;
 @Autowired com.cool.modules.workspace.mapper.WorkspaceNoticeRecipientMapper workspaceReceipts;
 @Autowired com.cool.modules.workspace.service.WorkspaceNoticeService workspaceNoticeService;
 @Autowired com.cool.modules.ai.config.AiProperties aiProperties;
 @org.springframework.boot.test.mock.mockito.MockBean ContentRiskModel model;
 void app(Long id){var p=new com.cool.core.security.jwt.JwtUser(id,java.util.List.of(),true);org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
 com.cool.modules.user.entity.UserInfoEntity user(String name){var u=new com.cool.modules.user.entity.UserInfoEntity();u.setNickName(name);u.setStatus(1);customers.insert(u);return u;}
 com.fasterxml.jackson.databind.JsonNode post(String url,Object value) throws Exception {return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType("application/json").content(json.writeValueAsString(value))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
 com.fasterxml.jackson.databind.JsonNode get(String url) throws Exception {return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
 @Test void httpBlockedFeedbackReviewWarningAndPrivateReceipt() throws Exception {
  String key="http-risk-"+java.util.UUID.randomUUID();var w=new SearchWord();w.setWord(key);w.setMode("BLOCK");w.setEnabled(true);words.insert(w);
  var u=user("risk customer");var other=user("other customer");Long rid=null;
  try{
   app(u.getId());var result=post("/app/support/feedback/submit",java.util.Map.of("requestId","risk_feedback_001","content",key,"images",java.util.List.of()));assertEquals(422,result.path("code").asInt());
   var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));assertNotNull(row);rid=row.getId();assertEquals(u.getId(),row.getSubjectId());assertEquals("APP",row.getSubjectType());
   assertEquals(0,feedback.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.support.entity.UserFeedbackEntity::getCustomerId,u.getId())));
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage","notice:send");
   assertEquals(1000,post("/admin/content-risk/records/resolve",java.util.Map.of("id",rid,"status","CONFIRMED","reason","测试人工核查")).path("code").asInt());
   assertEquals(u.getId().longValue(),get("/admin/content-risk/warnings/preview?riskId="+rid).path("data").path("subjectId").asLong());
   var send=java.util.Map.of("riskId",rid,"requestId","warning_"+java.util.UUID.randomUUID(),"title","平台提醒","content","请核对使用规则，确认仅代表知悉。","noticeType","STRONG");
   var sent=post("/admin/content-risk/warnings/send",send);assertEquals(1000,sent.path("code").asInt());long nid=sent.path("data").path("id").asLong();assertTrue(nid>0);
   assertEquals(nid,post("/admin/content-risk/warnings/send",send).path("data").path("id").asLong());
   app(other.getId());assertEquals(404,get("/app/platform/notices/detail?id="+nid).path("code").asInt());
   app(u.getId());assertEquals(1,get("/app/platform/notices/unread").path("data").asLong());assertEquals(1,get("/app/platform/notices/pending").path("data").path("total").asLong());
   assertEquals(1000,post("/app/platform/notices/read",java.util.Map.of("id",nid)).path("code").asInt());assertEquals(0,get("/app/platform/notices/unread").path("data").asLong());assertEquals(1,get("/app/platform/notices/pending").path("data").path("total").asLong());
   var confirmed=post("/app/platform/notices/confirm",java.util.Map.of("id",nid));assertEquals(1000,confirmed.path("code").asInt());assertFalse(confirmed.path("data").path("confirmedAt").isNull());
   assertEquals(0,get("/app/platform/notices/pending").path("data").path("total").asLong());assertEquals(1,customers.selectOneById(u.getId()).getStatus());
  }finally{if(rid!=null){warnings.deleteByQuery(QueryWrapper.create().eq(PlatformWarning::getRiskId,rid));audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,rid).in(SearchRiskAudit::getAction,java.util.List.of("RESOLVE","WARNING_SEND")));}records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));words.deleteById(w.getId());customers.deleteById(u.getId());customers.deleteById(other.getId());}
 }
 @Test void aiOutputIsSystemAndCannotReceiveAccountWarning(){
  String key="output-risk-"+java.util.UUID.randomUUID();var w=new SearchWord();w.setWord(key);w.setMode("REVIEW");w.setEnabled(true);words.insert(w);
  try{identity(com.cool.core.enums.UserTypeEnum.APP);service.checkContent(key,"AI_OUTPUT","TEST_AI",null);var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));assertEquals("SYSTEM",row.getSubjectType());assertNull(row.getSubjectId());assertNull(row.getUserId());identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:manage");assertThrows(com.cool.core.exception.CoolException.class,()->warningService.preview(row.getId()));}
  finally{records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));words.deleteById(w.getId());}
 }
 @Test void merchantWarningUsesExistingNoticeAndReceipt(){
  var merchant=new com.cool.modules.merchant.entity.MerchantInfoEntity();merchant.setName("risk test merchant");merchant.setCategoryId(1L);merchant.setContactName("测试");merchant.setContactPhone("13800000001");merchant.setStatus(1);merchants.insertSelective(merchant);
  var recipient=new com.cool.modules.base.entity.sys.BaseSysUserEntity();recipient.setUsername("risk_owner_"+java.util.UUID.randomUUID());recipient.setName("风险测试商家");recipient.setNickName("测试");recipient.setPassword("not-login-password");recipient.setStatus(1);recipient.setMerchantId(merchant.getId());recipient.setMerchantRole("OWNER");recipient.setAccountType("MERCHANT");workspaceUsers.insertSelective(recipient);
  var sender=new com.cool.modules.base.entity.sys.BaseSysUserEntity();sender.setUsername("risk_admin_"+java.util.UUID.randomUUID());sender.setName("风险测试运营");sender.setNickName("测试");sender.setPassword("not-login-password");sender.setStatus(1);workspaceUsers.insertSelective(sender);
  var risk=new SearchRisk();risk.setContent("merchant warning test");risk.setMatchedWords("[]");risk.setSource("TEST");risk.setScene("MERCHANT");risk.setSubjectType("MERCHANT");risk.setSubjectId(recipient.getId());risk.setStatus("CONFIRMED");risk.setMode("REVIEW");records.insert(risk);
  Long noticeId=null;
  try{
   var principal=new com.cool.core.security.jwt.JwtUser(sender.getId(),sender.getUsername(),"",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("search:risk:manage"),new org.springframework.security.core.authority.SimpleGrantedAuthority("notice:send")),true);
   org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
   var n=warningService.send(new PlatformWarningService.Send(risk.getId(),"merchant_warn_"+java.util.UUID.randomUUID(),"平台提醒","请核对平台规则，确认表示知悉。","STRONG"));noticeId=n.workspaceNoticeId();assertNotNull(noticeId);
   assertEquals("CONTENT_RISK",workspaceNotices.selectOneById(noticeId).getBusinessType());assertNotNull(workspaceNoticeService.confirm(recipient.getId(),noticeId).confirmedAt());
   assertNotNull(warningService.view(warnings.selectOneById(n.id())).confirmedAt());
  }finally{
   if(noticeId!=null){workspaceReceipts.deleteByQuery(QueryWrapper.create().eq(com.cool.modules.workspace.entity.WorkspaceNoticeRecipientEntity::getNoticeId,noticeId));workspaceNotices.deleteById(noticeId);}
   warnings.deleteByQuery(QueryWrapper.create().eq(PlatformWarning::getRiskId,risk.getId()));audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,risk.getId()).eq(SearchRiskAudit::getAction,"WARNING_SEND"));records.deleteById(risk.getId());workspaceUsers.deleteById(recipient.getId());workspaceUsers.deleteById(sender.getId());merchants.deleteById(merchant.getId());
  }
 }
 @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CONTENT_RISK_LIVE_MODEL_TEST",matches="true")
 void oneLiveModelReviewUsesExistingConfiguration(){
  assertTrue(aiProperties.configured(),"本机百炼配置必须有效");
  var result=new ContentRiskModel(aiProperties).review("门店今天营业时间为上午九点到晚上九点，欢迎到店。");
  assertNotNull(result);assertNotNull(result.reason());
 }
 @Test void entryPolicyCanDisableAndConvertBlockToReview(){
  String key="policy-risk-"+java.util.UUID.randomUUID();var w=new SearchWord();w.setWord(key);w.setMode("BLOCK");w.setEnabled(true);words.insert(w);
  var old=policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,"PROFILE"));var policy=new ContentRiskPolicy();policy.setId(old==null?null:old.getId());policy.setScene("PROFILE");policy.setEnabled(false);policy.setAction("INHERIT");if(old==null)policies.insert(policy);else policies.update(policy);
  try{assertDoesNotThrow(()->service.checkContent(key,"PROFILE","TEST",null));assertEquals(0,records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key)));
   policy.setEnabled(true);policy.setAction("REVIEW");policies.update(policy);assertDoesNotThrow(()->service.checkContent(key,"PROFILE","TEST",null));assertEquals("REVIEW",records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key)).getMode());
  }finally{records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));words.deleteById(w.getId());if(old==null)policies.deleteById(policy.getId());else policies.update(old);}
 }
 @Test void boundedJobFailureAndRetryPreserveBudget() throws Exception {
  String key="scan-risk-"+java.util.UUID.randomUUID();var u=user(key);var w=new SearchWord();w.setWord(key);w.setMode("BLOCK");w.setEnabled(true);words.insert(w);Long jid=null;
  try{
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
   org.mockito.Mockito.when(model.review(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("test failure"));
   var result=post("/admin/content-risk/jobs/start",java.util.Map.of("source","PROFILE","afterId",u.getId()-1,"limitCount",1,"modelBudget",1));assertEquals(1000,result.path("code").asInt());jid=result.path("data").path("id").asLong();
   jobs.run(jid);var failed=jobs.status(jid);assertEquals("FAILED",failed.state());assertEquals(1,failed.task().getModelCalls());assertEquals(0,failed.task().getCheckedCount());
   assertEquals(1000,post("/admin/content-risk/jobs/retry",java.util.Map.of("id",jid)).path("code").asInt());jobs.run(jid);assertEquals("FAILED",jobs.status(jid).state());assertEquals(0,jobs.status(jid).task().getCheckedCount());assertEquals(1,jobs.status(jid).task().getModelCalls());
   assertEquals(1,records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key)));assertEquals(1,customers.selectOneById(u.getId()).getStatus());
  }finally{if(jid!=null){audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,jid).in(SearchRiskAudit::getAction,java.util.List.of("JOB_START","JOB_RETRY")));jobRows.deleteById(jid);}records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));words.deleteById(w.getId());customers.deleteById(u.getId());}
 }
 @AfterEach void clearIdentity(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 @Test void automaticFailuresStopAfterTwoRetries(){
  var saved=schedules.selectOneById(1L);var u=user("scheduler permanent failure");Long id=null;
  try{
   var s=schedules.selectOneById(1L);s.setEnabled(true);s.setSourcesJson("[\"PROFILE\"]");s.setCursorsJson("{\"PROFILE\":"+(u.getId()-1)+"}");s.setSourceIndex(0);s.setBatchSize(1);s.setDailyBudget(10);s.setUsedCalls(0);s.setActiveJobId(null);s.setNextRunAt(new java.util.Date(0));schedules.update(s,false);
   org.mockito.Mockito.when(model.review(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("permanent"));
   scheduler.tick();id=schedules.selectOneById(1L).getActiveJobId();
   for(int i=0;i<3;i++){jobs.run(id);assertEquals("FAILED",jobRows.selectOneById(id).getStatus());s=schedules.selectOneById(1L);s.setNextRunAt(new java.util.Date(0));schedules.update(s);scheduler.tick();}
   s=schedules.selectOneById(1L);assertFalse(s.getEnabled());assertNull(s.getActiveJobId());assertEquals("FAILED",s.getLastResult());assertEquals(3,s.getUsedCalls());scheduler.tick();assertEquals(3,schedules.selectOneById(1L).getUsedCalls());
  }finally{if(id!=null){jobRows.deleteById(id);audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,id).eq(SearchRiskAudit::getAction,"JOB_START"));}schedules.update(saved,false);customers.deleteById(u.getId());}
 }
 @Test void dailyBudgetCannotBeExceededByConcurrentConsumers() throws Exception {
  var saved=schedules.selectOneById(1L);var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
  try{var s=schedules.selectOneById(1L);s.setDailyBudget(2);s.setUsedCalls(0);s.setBudgetDate(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());schedules.update(s);
   var tasks=new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();for(int i=0;i<8;i++)tasks.add(()->scheduleState.consume());
   int accepted=0;for(var future:pool.invokeAll(tasks))if(future.get())accepted++;assertEquals(2,accepted);assertEquals(2,schedules.selectOneById(1L).getUsedCalls());
  }finally{pool.shutdownNow();schedules.update(saved,false);}
 }
 @Test void schedulerHasSingleActiveJobFiniteRetryBudgetAndResumableCursor(){
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  var saved=schedules.selectOneById(1L);var u=user("risk scheduler isolated");Long id=null;
  try{
   var s=schedules.selectOneById(1L);s.setEnabled(true);s.setSourcesJson("[\"PROFILE\"]");s.setCursorsJson("{\"PROFILE\":"+ (u.getId()-1)+"}");s.setSourceIndex(0);s.setBatchSize(1);s.setDailyBudget(3);s.setUsedCalls(0);s.setBudgetDate(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());s.setActiveJobId(null);s.setNextRunAt(new java.util.Date(0));schedules.update(s,false);
   scheduler.tick();id=schedules.selectOneById(1L).getActiveJobId();assertNotNull(id);scheduler.tick();assertEquals(id,schedules.selectOneById(1L).getActiveJobId());
   org.mockito.Mockito.when(model.review(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("transient")).thenAnswer(a->{assertEquals("AI_RUNNING",jobRows.selectOneById(schedules.selectOneById(1L).getActiveJobId()).getPhase());return new ContentRiskModel.Advice(false,"正常内容");});
   jobs.run(id);assertEquals("FAILED",jobRows.selectOneById(id).getStatus());assertEquals(1,schedules.selectOneById(1L).getUsedCalls());
   s=schedules.selectOneById(1L);s.setNextRunAt(new java.util.Date(0));schedules.update(s);scheduler.tick();assertEquals("QUEUED",jobRows.selectOneById(id).getStatus());jobs.run(id);scheduler.tick();
   s=schedules.selectOneById(1L);assertNull(s.getActiveJobId());assertEquals("COMPLETED",s.getLastResult());assertEquals(u.getId(),scheduleState.cursors(s).get("PROFILE"));assertEquals(2,s.getUsedCalls());
   assertTrue(scheduleState.consume());assertFalse(scheduleState.consume());assertEquals(3,schedules.selectOneById(1L).getUsedCalls());
   assertEquals(1,customers.selectOneById(u.getId()).getStatus());
  }finally{if(id!=null){jobRows.deleteById(id);audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,id).eq(SearchRiskAudit::getAction,"JOB_START"));}schedules.update(saved,false);customers.deleteById(u.getId());}
 }
 @Test void sameNumericIdDifferentSubjectsAreDistinctAndFalsePositiveDoesNotBan(){
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  long before=controller.accountCount("PENDING");var rows=new java.util.ArrayList<SearchRisk>();String key="collision-"+java.util.UUID.randomUUID();
  try{for(String type:java.util.List.of("APP","MERCHANT")){var row=new SearchRisk();row.setContent(key);row.setSubjectType(type);row.setSubjectId(9223372000000L);row.setMode("REVIEW");row.setStatus("PENDING");records.insert(row);rows.add(row);}
   assertEquals(before+2,controller.accountCount("PENDING"));
   controller.resolve(new SearchRiskController.Resolution(rows.get(0).getId(),"FALSE_POSITIVE","正常语境，词库误报"));assertEquals(before+1,controller.accountCount("PENDING"));
   assertThrows(com.cool.core.exception.CoolException.class,()->warningService.preview(rows.get(0).getId()));
   assertThrows(com.cool.core.exception.CoolException.class,()->controller.resolve(new SearchRiskController.Resolution(null,"IGNORED","不得错误处理第一条")));
  }finally{for(var row:rows){audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,row.getId()).eq(SearchRiskAudit::getAction,"RESOLVE"));records.deleteById(row.getId());}}
 }
 void identity(com.cool.core.enums.UserTypeEnum type,String... permissions){
  var principal=new com.cool.core.security.jwt.JwtUser(987654321L,"risk-test-operator","",java.util.Arrays.stream(permissions).map(p->(org.springframework.security.core.GrantedAuthority)new org.springframework.security.core.authority.SimpleGrantedAuthority(p)).toList(),true);
  principal.setUserTypeEnum(type);
  org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
 }
 @Test void permissionsRequirePlatformAndExplicitGrant(){
  identity(com.cool.core.enums.UserTypeEnum.APP,"search:risk:view","search:risk:manage");
  assertThrows(com.cool.core.exception.CoolException.class,()->controller.words());
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view");
  assertDoesNotThrow(()->controller.words());
  assertThrows(com.cool.core.exception.CoolException.class,()->controller.save(new SearchRiskController.WordInput(null,"test","REVIEW",true)));
 }
 @Test void reviewIdentityAndResolutionAreAudited(){
  String key="review-test-"+java.util.UUID.randomUUID();
  var w=new SearchWord();w.setWord(key);w.setMode("REVIEW");w.setEnabled(true);words.insert(w);
  Long recordId=null;
  try {
   identity(com.cool.core.enums.UserTypeEnum.APP);
   service.check(key,"TEST");
   var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));recordId=row.getId();
   assertEquals(987654321L,row.getUserId());assertEquals("REVIEW",row.getMode());
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:manage");
   controller.resolve(new SearchRiskController.Resolution(recordId,"CONFIRMED","测试复核"));
   assertEquals("CONFIRMED",records.selectOneById(recordId).getStatus());
   assertEquals(1,audits.selectListByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,recordId).eq(SearchRiskAudit::getAction,"RESOLVE")).size());
   Long id=recordId;assertThrows(com.cool.core.exception.CoolException.class,()->controller.resolve(new SearchRiskController.Resolution(id,"IGNORED","重复处理")));
  } finally {
   if(recordId!=null)audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,recordId).eq(SearchRiskAudit::getAction,"RESOLVE"));
   records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));words.deleteById(w.getId());
  }
 }
 @BeforeEach void migrate() throws Exception {
  try(var connection=dataSource.getConnection()) {
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/search-risk-20261112.sql"));
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/content-risk-20261112.sql"));
  }
 }
 @Test void blockedRecordSurvivesBusinessRollback(){
  String key="risk-test-"+java.util.UUID.randomUUID();var w=new SearchWord();w.setWord(key);w.setMode("BLOCK");w.setEnabled(true);words.insert(w);
  try {
   assertThrows(com.cool.core.exception.CoolException.class,()->tx.executeWithoutResult(t->service.check(key,"TEST")));
   var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));
   assertNotNull(row);assertEquals("PENDING",row.getStatus());assertNull(row.getUserId());
  } finally {records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));words.deleteById(w.getId());}
 }
}
