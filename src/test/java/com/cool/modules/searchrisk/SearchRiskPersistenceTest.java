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
 @Autowired RiskWordController wordController;
 @Autowired RiskWordService managedWords;
 @Autowired ContentRiskDictionary dictionary;
 @Autowired RiskHistoryPreviewMapper historyPreviews;
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
 @Autowired com.cool.modules.merchant.service.MerchantInfoService merchantService;
 @Autowired com.cool.modules.merchant.service.MerchantCategoryService merchantCategories;
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
  String key="http-risk-"+java.util.UUID.randomUUID();long wordId=managedWords.save(null,key,"BLOCK",true);
  var u=user("risk customer");var other=user("other customer");Long rid=null;
  try{
   app(u.getId());var result=post("/app/support/feedback/submit",java.util.Map.of("requestId","risk_feedback_001","content",key,"images",java.util.List.of()));assertEquals(422,result.path("code").asInt());
   var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));assertNotNull(row);rid=row.getId();assertEquals(u.getId(),row.getSubjectId());assertEquals("APP",row.getSubjectType());
   assertEquals(0,feedback.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.support.entity.UserFeedbackEntity::getCustomerId,u.getId())));
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage","notice:send");
   assertEquals(1000,post("/admin/content-risk/records/resolve",java.util.Map.of("id",rid,"status","CONFIRMED","reason","测试人工核查")).path("code").asInt());
   var preview=get("/admin/content-risk/warnings/preview?riskId="+rid).path("data");
   assertEquals(u.getId().longValue(),preview.path("subjectId").asLong());
   assertEquals(u.getNickName(),preview.path("subjectName").asText());
   var send=java.util.Map.of("riskId",rid,"requestId","warning_"+java.util.UUID.randomUUID(),"title","平台提醒","content","请核对使用规则，确认仅代表知悉。","noticeType","STRONG");
   var sent=post("/admin/content-risk/warnings/send",send);assertEquals(1000,sent.path("code").asInt());long nid=sent.path("data").path("id").asLong();assertTrue(nid>0);
   assertEquals(nid,post("/admin/content-risk/warnings/send",send).path("data").path("id").asLong());
   app(other.getId());assertEquals(404,get("/app/platform/notices/detail?id="+nid).path("code").asInt());
   app(u.getId());assertEquals(1,get("/app/platform/notices/unread").path("data").asLong());assertEquals(1,get("/app/platform/notices/pending").path("data").path("total").asLong());
   assertEquals(1000,post("/app/platform/notices/read",java.util.Map.of("id",nid)).path("code").asInt());assertEquals(0,get("/app/platform/notices/unread").path("data").asLong());assertEquals(1,get("/app/platform/notices/pending").path("data").path("total").asLong());
   var confirmed=post("/app/platform/notices/confirm",java.util.Map.of("id",nid));assertEquals(1000,confirmed.path("code").asInt());assertFalse(confirmed.path("data").path("confirmedAt").isNull());
   assertEquals(0,get("/app/platform/notices/pending").path("data").path("total").asLong());assertEquals(1,customers.selectOneById(u.getId()).getStatus());
  }finally{if(rid!=null){warnings.deleteByQuery(QueryWrapper.create().eq(PlatformWarning::getRiskId,rid));audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,rid).in(SearchRiskAudit::getAction,java.util.List.of("RESOLVE","WARNING_SEND")));}records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));deleteTestWord(wordId);customers.deleteById(u.getId());customers.deleteById(other.getId());}
 }
 @Test void aiOutputIsSystemAndCannotReceiveAccountWarning(){
  String key="output-risk-"+java.util.UUID.randomUUID();long wordId=managedWords.save(null,key,"REVIEW",true);
  try{identity(com.cool.core.enums.UserTypeEnum.APP);service.checkContent(key,"AI_OUTPUT","TEST_AI",null);var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));assertEquals("SYSTEM",row.getSubjectType());assertNull(row.getSubjectId());assertNull(row.getUserId());identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:manage");assertThrows(com.cool.core.exception.CoolException.class,()->warningService.preview(row.getId()));}
  finally{records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));deleteTestWord(wordId);}
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
 @Test @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CONTENT_RISK_LIVE_MODEL_TEST",matches="true")
 void oneLivePreprocessingUsesSyntheticCouponCopy() {
  assertTrue(aiProperties.configured(),"本机 AI 配置必须有效");
  var sample=new SearchRisk();
  sample.setScene("COUPON");
  sample.setMatchedWords("[\"美甲\"]");
  sample.setContent("单次基础美甲体验券，包含甲型修整与单色涂饰。");
  var suggestion=new ContentRiskModel(aiProperties).preprocess(sample);
  assertNotNull(suggestion.assessment());
  assertFalse(suggestion.reason().isBlank());
 }
 @Test void preprocessingSendsRedactedContextAndParsesAssessment() throws Exception {
  var provider=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var body=new java.util.concurrent.atomic.AtomicReference<String>();
  provider.createContext("/chat/completions",exchange->{
   body.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   var message=java.util.Map.of("role","assistant","content",json.writeValueAsString(java.util.Map.of(
       "assessment","LIKELY_FALSE_POSITIVE","reason","美甲是正常的服务名称。")));
   var response=json.writeValueAsBytes(java.util.Map.of("id","test","object","chat.completion","created",1,
       "model","test-model","choices",java.util.List.of(java.util.Map.of("index",0,"message",message,"finish_reason","stop"))));
   exchange.getResponseHeaders().set("Content-Type","application/json");
   exchange.sendResponseHeaders(200,response.length);
   try (var output=exchange.getResponseBody()) {output.write(response);}
  });
  provider.start();
  try {
   var properties=new com.cool.modules.ai.config.AiProperties() {
    @Override public String getApiKey(){return "test-key";}
   };
   properties.setBaseUrl("http://127.0.0.1:"+provider.getAddress().getPort());
   properties.setTextModel("test-model");
   var sample=new SearchRisk();
   sample.setScene("COUPON");
   sample.setMatchedWords("[\"美甲\"]");
   sample.setContent("基础美甲体验券，咨询热线 13800138000。");
   var result=new ContentRiskModel(properties).preprocess(sample);
   assertEquals(ContentRiskModel.Assessment.LIKELY_FALSE_POSITIVE,result.assessment());
   assertTrue(body.get().contains("美甲"));
   assertTrue(body.get().contains("[已隐藏]"));
   assertFalse(body.get().contains("13800138000"));
  } finally {provider.stop(0);}
 }
 @Test void entryPolicyCanDisableAndConvertBlockToReview(){
  String key="policy-risk-"+java.util.UUID.randomUUID();long wordId=managedWords.save(null,key,"BLOCK",true);
  var old=policies.selectOneByQuery(QueryWrapper.create().eq(ContentRiskPolicy::getScene,"PROFILE"));var policy=new ContentRiskPolicy();policy.setId(old==null?null:old.getId());policy.setScene("PROFILE");policy.setEnabled(false);policy.setAction("INHERIT");if(old==null)policies.insert(policy);else policies.update(policy);
  try{assertDoesNotThrow(()->service.checkContent(key,"PROFILE","TEST",null));assertEquals(0,records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key)));
   policy.setEnabled(true);policy.setAction("REVIEW");policies.update(policy);assertDoesNotThrow(()->service.checkContent(key,"PROFILE","TEST",null));assertEquals("REVIEW",records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key)).getMode());
  }finally{records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));deleteTestWord(wordId);if(old==null)policies.deleteById(policy.getId());else policies.update(old);}
 }
 @Test void boundedJobFailureAndRetryPreserveBudget() throws Exception {
  String key="scan-risk-"+java.util.UUID.randomUUID();var u=user(key);long wordId=managedWords.save(null,key,"BLOCK",true);Long jid=null;
  try{
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
   org.mockito.Mockito.when(model.review(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("test failure"));
   var result=post("/admin/content-risk/jobs/start",java.util.Map.of("source","PROFILE","afterId",u.getId()-1,"limitCount",1,"modelBudget",1));assertEquals(1000,result.path("code").asInt());jid=result.path("data").path("id").asLong();
   jobs.run(jid);var failed=jobs.status(jid);assertEquals("FAILED",failed.state());assertEquals(1,failed.task().getModelCalls());assertEquals(0,failed.task().getCheckedCount());
   assertEquals(1000,post("/admin/content-risk/jobs/retry",java.util.Map.of("id",jid)).path("code").asInt());jobs.run(jid);assertEquals("FAILED",jobs.status(jid).state());assertEquals(0,jobs.status(jid).task().getCheckedCount());assertEquals(1,jobs.status(jid).task().getModelCalls());
   assertEquals(1,records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key)));assertEquals(1,customers.selectOneById(u.getId()).getStatus());
  }finally{if(jid!=null){audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,jid).in(SearchRiskAudit::getAction,java.util.List.of("JOB_START","JOB_RETRY")));jobRows.deleteById(jid);}records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));deleteTestWord(wordId);customers.deleteById(u.getId());}
 }
 @Test void aiPreprocessingAddsAdviceOnlyToUnreviewedKeywordHits() throws Exception {
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  var saved=schedules.selectOneById(1L);
  var rows=new java.util.ArrayList<SearchRisk>();
  Long jobId=null;
  try {
   var settings=schedules.selectOneById(1L);
   settings.setDailyBudget(4);
   settings.setUsedCalls(0);
   schedules.update(settings);

   for (int index=0;index<4;index++) {
    var row=new SearchRisk();
    row.setContent("普通美甲体验券");
    row.setScene("COUPON");
    row.setMatchedWords(index==3?"[]":"[\"美甲\"]");
    row.setStatus(index==2?"CONFIRMED":"PENDING");
    row.setMode("REVIEW");
    if (index==1) row.setAiAdvice("已有建议");
    records.insert(row);
    rows.add(row);
   }
   org.mockito.Mockito.when(model.preprocess(org.mockito.ArgumentMatchers.any(SearchRisk.class))).thenReturn(
       new ContentRiskModel.Preprocessing(ContentRiskModel.Assessment.LIKELY_FALSE_POSITIVE,"美甲是正常的服务名称。"));
   var result=post("/admin/content-risk/jobs/start",java.util.Map.of("source","RISKS","afterId",rows.get(0).getId()-1,"limitCount",4,"modelBudget",4));
   assertEquals(1000,result.path("code").asInt());
   jobId=result.path("data").path("id").asLong();
   jobs.run(jobId);
   assertEquals("COMPLETED",jobs.status(jobId).state());
   assertEquals(1,jobs.status(jobId).task().getModelCalls());
   var reviewed=records.selectOneById(rows.get(0).getId());
   assertEquals("PENDING",reviewed.getStatus());
   assertEquals("疑似误报 · 美甲是正常的服务名称。",reviewed.getAiAdvice());
   assertEquals("已有建议",records.selectOneById(rows.get(1).getId()).getAiAdvice());
   assertNull(records.selectOneById(rows.get(2).getId()).getAiAdvice());
   assertNull(records.selectOneById(rows.get(3).getId()).getAiAdvice());
  } finally {
   if (jobId!=null) {
    audits.deleteByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,jobId).eq(SearchRiskAudit::getAction,"JOB_START"));
    jobRows.deleteById(jobId);
   }
   rows.forEach(row->records.deleteById(row.getId()));
   schedules.update(saved,false);
  }
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
  assertThrows(com.cool.core.exception.CoolException.class,()->wordController.words());
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view");
  assertDoesNotThrow(()->wordController.words());
  assertThrows(com.cool.core.exception.CoolException.class,()->wordController.save(new RiskWordController.WordInput(null,"test","REVIEW",true)));
 }
 @Test void reviewIdentityAndResolutionAreAudited(){
  String key="review-test-"+java.util.UUID.randomUUID();
  long wordId=managedWords.save(null,key,"REVIEW",true);
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
   records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));deleteTestWord(wordId);
  }
 }
 @BeforeEach void migrate() throws Exception {
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  try(var connection=dataSource.getConnection()) {
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/search-risk-20261112.sql"));
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/content-risk-20261112.sql"));
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/risk-manual-20261114.sql"));
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/risk-submitter-20261115.sql"));
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource("deploy/risk-review-context-20261117.sql"));
  }
 }
 @Test void rulePreviewUsesLivePolicyWithoutCreatingRiskAndLinksExactWord() throws Exception {
  String key="rule-preview-"+java.util.UUID.randomUUID();
  long wordId=managedWords.save(null,key,"REVIEW",true);
  try {
   var input=java.util.Map.of("text",key,"scene","MERCHANT");
   var preview=post("/admin/content-risk/words/test",input);
   assertEquals("REVIEW",preview.path("data").path("action").asText());
   assertEquals(wordId,preview.path("data").path("hits").get(0).path("wordId").asLong());
   var filter=QueryWrapper.create().eq(SearchRisk::getContent,key);
   assertEquals(0,records.selectCountByQuery(filter));
   service.checkContent(key,"MERCHANT","MERCHANT_NAME",null);
   var allowed=records.selectOneByQuery(filter);
   assertEquals("ALLOWED_RECORDED",allowed.getDetectionResult());
   var scan=service.rescan(new RiskOriginalContent.Field("MERCHANT","MERCHANT_NAME",key,null),987654321L);
   assertEquals("SCAN_HIT",records.selectOneById(scan.id()).getDetectionResult());
   var filtered=get("/admin/content-risk/records?matchedWordId="+wordId+"&matchedWord="+key).path("data");
   assertEquals(2,filtered.path("total").asInt());
   assertEquals(0,get("/admin/content-risk/records?matchedWord=rule-preview").path("data").path("total").asInt());
   deleteTestWord(wordId);
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
   assertTrue(get("/admin/content-risk/words/detail?id="+wordId).path("data").path("deleted").asBoolean());
   assertEquals("PASS",post("/admin/content-risk/words/test",input).path("data").path("action").asText());
   assertEquals("PENDING",records.selectOneById(allowed.getId()).getStatus());
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view");
   assertEquals(403,post("/admin/content-risk/words/test",input).path("code").asInt());
   identity(com.cool.core.enums.UserTypeEnum.MERCHANT,"search:risk:view","search:risk:manage");
   assertEquals(403,get("/admin/content-risk/words/detail?id="+wordId).path("code").asInt());
  } finally {
   records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));
   if(!Boolean.TRUE.equals(words.selectOneById(wordId).getDeleted()))deleteTestWord(wordId);
  }
 }
 @Test void blockedCouponDraftPreservesNameAndMerchantWithoutInventingCouponId() throws Exception {
  String key="coupon-context-"+java.util.UUID.randomUUID();
  long wordId=managedWords.save(null,key,"BLOCK",true);
  var filter=QueryWrapper.create().eq(SearchRisk::getContent,key);
  try {
   assertThrows(com.cool.core.exception.CoolException.class,()->service.checkCouponChange(
    null,key,"COUPON_DESCRIPTION",null,new SearchRisk.CouponContext(42L,"验收商家","验收优惠券")));
   var record=records.selectOneByQuery(filter);
   assertNotNull(record);assertNull(record.getOriginId());
   var detail=get("/admin/content-risk/records/detail?id="+record.getId()).path("data");
   assertEquals("验收商家",detail.path("originMerchantName").asText());
   assertEquals("验收优惠券",detail.path("originName").asText());
   assertEquals("description",detail.path("originField").asText());
   assertEquals("BLOCKED",detail.path("detectionResult").asText());
  }finally{
   records.deleteByQuery(filter);
   deleteTestWord(wordId);
  }
 }
 @Test void manualBatchIsAtomicAndBlankReasonIsCleared() throws Exception {
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  var first=legacyRisk("批量第一条",null,null);
  var second=legacyRisk("批量第二条",null,null);
  var ids=java.util.List.of(first.getId(),second.getId());
  try {
   controller.resolve(new SearchRiskController.Resolution(second.getId(),"IGNORED",null));
   var rejected=post("/admin/content-risk/records/resolve-batch",java.util.Map.of("ids",ids,"status","CONFIRMED"));
   assertEquals(409,rejected.path("code").asInt());
   assertEquals("PENDING",records.selectOneById(first.getId()).getStatus());
   assertEquals(0,auditCount(first.getId(),"RESOLVE"));
   var reset=records.selectOneById(second.getId());reset.setStatus("PENDING");reset.setReason("旧原因");records.update(reset);
   var accepted=post("/admin/content-risk/records/resolve-batch",java.util.Map.of("ids",ids,"status","CONFIRMED","reason","  "));
   assertEquals(2,accepted.path("data").path("resolvedCount").asInt());
   for(Long id:ids){assertEquals("CONFIRMED",records.selectOneById(id).getStatus());assertNull(records.selectOneById(id).getReason());}
   assertEquals(1,auditCount(first.getId(),"RESOLVE"));
  }finally{
   audits.deleteByQuery(QueryWrapper.create().in(SearchRiskAudit::getTargetId,ids).eq(SearchRiskAudit::getAction,"RESOLVE"));
   records.deleteByQuery(QueryWrapper.create().in(SearchRisk::getId,ids));
  }
 }
 @Test void historyPreviewApplyAndRescanUseOriginalObjectAndVersion() throws Exception {
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  String key="manual-origin-"+java.util.UUID.randomUUID();
  long wordId=managedWords.save(null,key,"REVIEW",true);
  var profile=user(key);var other=user(key);
  var first=legacyRisk(key,"USER_NICKNAME",profile.getId());
  var duplicate=legacyRisk(key,"USER_NICKNAME",profile.getId());
  var unknown=legacyRisk(key,"MERCHANT",profile.getId());
  Long previewId=null;
  try {
   var preview=post("/admin/content-risk/history/preview",java.util.Map.of("afterId",first.getId()-1,"limit",3)).path("data");
   previewId=preview.path("previewId").asLong();
   assertEquals("UPDATE_ORIGIN",preview.path("items").get(0).path("action").asText());
   assertEquals("MERGE_DUPLICATE",preview.path("items").get(1).path("action").asText());
   assertEquals("REVIEW_REQUIRED",preview.path("items").get(2).path("action").asText());
   assertNull(records.selectOneById(first.getId()).getOriginType());
   var changed=records.selectOneById(duplicate.getId());changed.setReason("预览后变化");records.update(changed);
   var selected=java.util.List.of(first.getId(),duplicate.getId());
   var conflict=post("/admin/content-risk/history/apply",java.util.Map.of("previewId",previewId,"requestId","history_conflict_001","ids",selected));
   assertEquals(409,conflict.path("code").asInt());
   assertNull(records.selectOneById(first.getId()).getOriginType());
   historyPreviews.deleteById(previewId);
   previewId=post("/admin/content-risk/history/preview",java.util.Map.of("afterId",first.getId()-1,"limit",3)).path("data").path("previewId").asLong();
   var request=java.util.Map.of("previewId",previewId,"requestId","history_apply_001","ids",selected);
   var result=post("/admin/content-risk/history/apply",request).path("data");
   assertEquals(1,result.path("updated").asInt());assertEquals(1,result.path("merged").asInt());
   assertEquals(result,post("/admin/content-risk/history/apply",request).path("data"));
   var primary=records.selectOneById(first.getId());
   assertEquals("PROFILE",primary.getOriginType());assertEquals(profile.getId(),primary.getOriginId());
   assertNull(primary.getSubmittedById());
   assertEquals(first.getId(),records.selectOneById(duplicate.getId()).getDuplicateOfId());
   assertEquals("PENDING",records.selectOneById(duplicate.getId()).getStatus());
   assertEquals(1,auditCount(first.getId(),"HISTORY_UPDATE_ORIGIN"));assertEquals(1,auditCount(duplicate.getId(),"HISTORY_MERGE"));
   var pending=get("/admin/content-risk/records?status=PENDING&businessType=USER_NICKNAME&businessId="+profile.getId()).path("data");
   assertEquals(1,pending.path("total").asInt());
   var scan=post("/admin/content-risk/history/rescan",java.util.Map.of("source","PROFILE","afterId",profile.getId()-1,"limit",2)).path("data");
   assertEquals(2,scan.path("checked").asInt());assertEquals(1,scan.path("created").asInt());assertEquals(1,scan.path("existing").asInt());
   identity(com.cool.core.enums.UserTypeEnum.APP);
   service.checkContent(key+" 新版","PROFILE","USER_NICKNAME",profile.getId());
   assertEquals(2,records.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getOriginId,profile.getId()).isNull(SearchRisk::getDuplicateOfId)));
   org.mockito.Mockito.verifyNoInteractions(model);
  }finally{
   if(previewId!=null)historyPreviews.deleteById(previewId);
   audits.deleteByQuery(QueryWrapper.create().in(SearchRiskAudit::getTargetId,java.util.List.of(first.getId(),duplicate.getId()))
    .in(SearchRiskAudit::getAction,java.util.List.of("HISTORY_UPDATE_ORIGIN","HISTORY_MERGE")));
   records.deleteByQuery(QueryWrapper.create().in(SearchRisk::getContent,java.util.List.of(key,key+" 新版")));
   managedWords.delete(wordId);customers.deleteById(profile.getId());customers.deleteById(other.getId());
  }
 }
 @Test void appliesDifferentAiSuggestionsTogetherAndRejectsStaleAdvice() throws Exception {
  var falsePositive=legacyRisk("基础美甲体验券",null,null);
  var priority=legacyRisk("测试风险内容",null,null);
  var needsContext=legacyRisk("测试上下文不足",null,null);
  var ids=java.util.List.of(falsePositive.getId(),priority.getId(),needsContext.getId());
  try {
   falsePositive.setAiAdvice(new ContentRiskModel.Preprocessing(ContentRiskModel.Assessment.LIKELY_FALSE_POSITIVE,"正常服务名称").summary());
   priority.setAiAdvice(new ContentRiskModel.Preprocessing(ContentRiskModel.Assessment.PRIORITY_REVIEW,"用语需关注").summary());
   needsContext.setAiAdvice(new ContentRiskModel.Preprocessing(ContentRiskModel.Assessment.NEEDS_CONTEXT,"缺少上下文").summary());
   java.util.List.of(falsePositive,priority,needsContext).forEach(row -> records.update(row));
   var first=new SearchRiskController.AiResolutionItem(falsePositive.getId(),falsePositive.getAiAdvice());
   var second=new SearchRiskController.AiResolutionItem(priority.getId(),priority.getAiAdvice());
   var skipped=new SearchRiskController.AiResolutionItem(needsContext.getId(),needsContext.getAiAdvice());

   var detail=get("/admin/content-risk/records/detail?id="+falsePositive.getId()).path("data");
   assertEquals("FALSE_POSITIVE",detail.path("aiSuggestedStatus").asText());
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view");
   assertEquals(403,post("/admin/content-risk/records/resolve-by-ai",new SearchRiskController.AiBatchResolution(java.util.List.of(first))).path("code").asInt());
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");

   var stale=new SearchRiskController.AiResolutionItem(priority.getId(),"建议优先复核 · 旧理由");
   for (var rejected : java.util.List.of(stale,skipped)) {
    var result=post("/admin/content-risk/records/resolve-by-ai",new SearchRiskController.AiBatchResolution(java.util.List.of(first,rejected)));
    assertEquals(409,result.path("code").asInt());
    assertEquals("PENDING",records.selectOneById(first.id()).getStatus());
    assertEquals(0,auditCount(first.id(),"RESOLVE"));
   }

   var result=post("/admin/content-risk/records/resolve-by-ai",new SearchRiskController.AiBatchResolution(java.util.List.of(first,second)));
   assertEquals(2,result.path("data").path("resolvedCount").asInt());
   assertEquals("FALSE_POSITIVE",records.selectOneById(first.id()).getStatus());
   assertEquals("CONFIRMED",records.selectOneById(second.id()).getStatus());
   assertEquals("PENDING",records.selectOneById(skipped.id()).getStatus());
   var audit=audits.selectOneByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,first.id()).eq(SearchRiskAudit::getAction,"RESOLVE"));
   assertEquals(987654321L,audit.getOperatorId());
   assertEquals(first.expectedAdvice(),json.readTree(audit.getDetail()).path("aiAdvice").asText());
  } finally {
   audits.deleteByQuery(QueryWrapper.create().in(SearchRiskAudit::getTargetId,ids).eq(SearchRiskAudit::getAction,"RESOLVE"));
   records.deleteByQuery(QueryWrapper.create().in(SearchRisk::getId,ids));
  }
 }
 @Test void merchantFieldsDeduplicateSeparatelyAndReadReturnsRealNameWithoutRecording() throws Exception {
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  String text="merchant-origin-"+java.util.UUID.randomUUID();
  long wordId=managedWords.save(null,text,"REVIEW",true);
  var merchant=new com.cool.modules.merchant.entity.MerchantInfoEntity();
  merchant.setName(text);merchant.setIntroduction(text);merchant.setStatus(1);
  merchant.setCategoryId(1L);merchant.setContactName("fixture");merchant.setContactPhone("10000");merchants.insert(merchant);
  try {
   service.checkContent(text,"MERCHANT","MERCHANT_NAME",merchant.getId());
   var filters=QueryWrapper.create().eq(SearchRisk::getOriginType,"MERCHANT").eq(SearchRisk::getOriginId,merchant.getId());
   var nameRisk=records.selectOneByQuery(filters);
   var scan=post("/admin/content-risk/history/rescan",java.util.Map.of("source","MERCHANT","afterId",merchant.getId()-1,"limit",1)).path("data");
   assertEquals(1,scan.path("created").asInt());
   assertEquals(1,scan.path("existing").asInt());
   var detail=get("/admin/content-risk/records/detail?id="+nameRisk.getId()).path("data");
   assertEquals(text,detail.path("originName").asText());
   assertEquals("name",detail.path("originField").asText());
   assertEquals(987654321L,detail.path("submittedById").asLong());
   assertEquals(2,records.selectCountByQuery(filters));
   var intro=records.selectListByQuery(filters).stream().filter(row->"introduction".equals(row.getOriginField())).findFirst().orElseThrow();
   assertNull(intro.getSubmittedById());
   assertEquals("RULE_RESCAN",intro.getDetectionEntry());
   org.mockito.Mockito.verifyNoInteractions(model);
  }finally{
   records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,text));
   deleteTestWord(wordId);merchants.deleteById(merchant.getId());
  }
 }

 @Test void merchantEditAttributesOnlyChangedContent() throws Exception {
  String term="shop-origin-"+java.util.UUID.randomUUID();
  long wordId=managedWords.save(null,term,"REVIEW",true);
  var category=merchantCategories.list().stream().findFirst().orElseThrow();
  var merchant=new com.cool.modules.merchant.entity.MerchantInfoEntity();
  merchant.setName(term);
  merchant.setCategoryId(category.getId());
  merchant.setContactName("测试联系人");
  merchant.setContactPhone("010-12345678");
  merchant.setStatus(1);
  merchants.insertSelective(merchant);
  var scope=QueryWrapper.create().eq(SearchRisk::getOriginType,"MERCHANT")
   .eq(SearchRisk::getOriginId,merchant.getId()).eq(SearchRisk::getOriginField,"name");
  try {
   var scan=post("/admin/content-risk/history/rescan",java.util.Map.of(
    "source","MERCHANT","afterId",merchant.getId()-1,"limit",1));
   assertEquals(1,scan.path("data").path("created").asInt());
   var old=records.selectOneByQuery(scope);
   assertEquals("VERIFIED",old.getProvenanceStatus());
   assertNull(old.getSubmittedById());

   merchant.setContactName("另一个联系人");
   assertTrue(merchantService.update(merchant));
   assertNull(records.selectOneById(old.getId()).getSubmittedById());
   assertEquals(1,records.selectCountByQuery(scope));

   merchant.setName(term+" 新名称");
   assertTrue(merchantService.update(merchant));
   var changed=records.selectListByQuery(scope).stream()
    .filter(row->merchant.getName().equals(row.getContent())).findFirst().orElseThrow();
   assertEquals("ADMIN",changed.getSubmittedByType());
   assertEquals(987654321L,changed.getSubmittedById());
   assertEquals("WRITE",changed.getDetectionEntry());
   assertEquals(2,records.selectCountByQuery(scope));
  }finally{
   records.deleteByQuery(scope);
   merchants.deleteById(merchant.getId());
   deleteTestWord(wordId);
  }
 }

 @Test void rescanAfterWordWasAddedRecoversTheAccountThatSavedTheName() throws Exception {
  // This case verifies a later rule addition, so the initial name must be absent from the active dictionary.
  String term=java.util.stream.Stream.generate(()->"词库复扫验收"+Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong()))
      .limit(30).filter(candidate->dictionary.find(candidate).isEmpty()&&!dictionary.isAllowed(candidate)).findFirst().orElseThrow();
  var category=merchantCategories.list().stream().findFirst().orElseThrow();
  var merchant=new com.cool.modules.merchant.entity.MerchantInfoEntity();
  merchant.setName("原商家");
  merchant.setCategoryId(category.getId());
  merchant.setContactName("测试联系人");
  merchant.setContactPhone("010-12345678");
  merchant.setStatus(1);
  merchants.insertSelective(merchant);
  merchant.setName(term);
  Long wordId=null;
  var scope=QueryWrapper.create().eq(SearchRisk::getOriginType,"MERCHANT")
      .eq(SearchRisk::getOriginId,merchant.getId()).eq(SearchRisk::getOriginField,"name");
  try {
   identity(com.cool.core.enums.UserTypeEnum.MERCHANT);
   assertTrue(merchantService.update(merchant));
   assertEquals(0,records.selectCountByQuery(scope));
   identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
   wordId=managedWords.save(null,term,"REVIEW",true);
   var response=post("/admin/content-risk/history/rescan",java.util.Map.of(
      "source","MERCHANT","afterId",merchant.getId()-1,"limit",1));
   assertEquals(1,response.path("data").path("created").asInt());
   var row=records.selectOneByQuery(scope);
   assertEquals("MERCHANT",row.getSubmittedByType());
   assertEquals(987654321L,row.getSubmittedById());
   assertEquals("RULE_RESCAN",row.getDetectionEntry());
  }finally{
   records.deleteByQuery(scope);
   merchants.deleteById(merchant.getId());
   if(wordId!=null)deleteTestWord(wordId);
  }
 }

 private SearchRisk legacyRisk(String text,String businessType,Long businessId){
  var row=new SearchRisk();row.setScene("PROFILE");row.setContent(text);row.setMode("REVIEW");row.setStatus("PENDING");
  row.setBusinessType(businessType);row.setBusinessId(businessId);records.insert(row);return row;
 }
 private long auditCount(Long id,String action){
  return audits.selectCountByQuery(QueryWrapper.create().eq(SearchRiskAudit::getTargetId,id).eq(SearchRiskAudit::getAction,action));
 }
 @Test void blockedRecordSurvivesBusinessRollback(){
  String key="risk-test-"+java.util.UUID.randomUUID();long wordId=managedWords.save(null,key,"BLOCK",true);
  try {
   org.springframework.security.core.context.SecurityContextHolder.clearContext();
   assertThrows(com.cool.core.exception.CoolException.class,()->tx.executeWithoutResult(t->service.check(key,"TEST")));
   var row=records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));
   assertNotNull(row);assertEquals("PENDING",row.getStatus());assertNull(row.getUserId());
  } finally {records.deleteByQuery(QueryWrapper.create().eq(SearchRisk::getContent,key));deleteTestWord(wordId);}
 }
 private void deleteTestWord(long wordId){
  identity(com.cool.core.enums.UserTypeEnum.ADMIN,"search:risk:manage");
  managedWords.delete(wordId);
 }
}
