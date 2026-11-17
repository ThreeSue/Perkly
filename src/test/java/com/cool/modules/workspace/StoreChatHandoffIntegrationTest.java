package com.cool.modules.workspace;
import com.cool.modules.support.chat.*;
import com.cool.modules.support.entity.*;
import com.cool.modules.support.mapper.*;
import com.cool.modules.support.service.StoreChatService;
import com.cool.core.enums.UserTypeEnum;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用隔离数据库验证客服多设备离线交接及真实接口
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 16:10:00
 */
class StoreChatHandoffIntegrationTest extends FeatureModulesIntegrationTest {
 @Autowired StoreChatPresenceService presence;
 @Autowired StoreChatPresenceMapper presenceRows;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Autowired javax.sql.DataSource dataSource;
 @Autowired com.cool.modules.searchrisk.mapper.SearchRiskMapper riskRecords;
 @org.springframework.boot.test.mock.mockito.MockBean StoreChatAiAvailability availability;
 @BeforeEach @Override void setup() {
  assertEquals("perkly_ai_regression_20261106",jdbc.queryForObject("SELECT DATABASE()",String.class));
  String sql;try{sql=java.nio.file.Files.readString(java.nio.file.Path.of("deploy/store-chat-handoff-20261113.sql")).replaceAll("(?m)^--.*$","");}catch(java.io.IOException e){throw new IllegalStateException(e);}
  for(String s:sql.split(";"))if(!s.isBlank())jdbc.execute(s);
  super.setup();
 }
 @Test void multiDeviceOfflineResumeAndHttpReturn() throws Exception {
  enableChatAi();String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
  String employee=token(staff,UserTypeEnum.MERCHANT);
  mvc.perform(post("/merchant/governance/chats/presence").header("Authorization",employee).contentType("application/json").content("{\"sessionId\":\""+a+"\",\"available\":true}"))
   .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.heartbeatIntervalSeconds").value(30));
  presence.heartbeat(staff,new StoreChatPresenceService.Input(b,true));
  Long id=chats.open(customer,store);
  reception.transition("WORKSPACE",staff,"TAKEOVER",new StoreChatModels.Transition(id,"handoff_takeover_001",0L));
  Long question=chats.send("APP",customer,new StoreChatService.Send(id,"handoff_question_001","几点营业",List.of()));
  presence.heartbeat(staff,new StoreChatPresenceService.Input(a,false));reception.expire(id);
  assertEquals("HUMAN",reception.detail("APP",customer,id).state());
  presence.heartbeat(staff,new StoreChatPresenceService.Input(b,false));reception.expire(id);
  var detail=reception.detail("APP",customer,id);assertEquals("AI",detail.state());assertTrue(detail.humanRequested());assertNull(detail.assigneeId());
  int size=chats.messages("APP",customer,id,null,null,50).size();reception.expire(id);assertEquals(size,chats.messages("APP",customer,id,null,null,50).size());
  when(chatModel.reply(any(),eq(false))).thenReturn(new StoreChatModels.ModelReply("请查看门店营业时间",List.of(),false));
  chatWorker.process(chatJob(question));chatWorker.process(chatJob(question));
  assertEquals(1,chats.messages("APP",customer,id,null,null,50).stream().filter(m->"AI".equals(m.getSenderKind())).count());
  detail=reception.detail("WORKSPACE",staff,id);
  var taken=reception.transition("WORKSPACE",staff,"TAKEOVER",new StoreChatModels.Transition(id,"handoff_takeover_002",detail.version()));
  String body="{\"chatId\":"+id+",\"requestId\":\"return_ai_http_001\",\"version\":"+taken.version()+"}";
  for(int i=0;i<2;i++)mvc.perform(post("/merchant/governance/chats/return-to-ai").header("Authorization",employee).contentType("application/json").content(body))
   .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.state").value("AI")).andExpect(jsonPath("$.data.humanRequested").value(false));
  verify(chatModel,times(1)).reply(any(),eq(false));
 }
 @Test void blockedChatRetainsAuthorizedConversationSnapshotWithoutSendingMessage() throws Exception {
  chatPermissions();
  try(var connection=dataSource.getConnection()){
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
    new org.springframework.core.io.FileSystemResource("deploy/risk-review-context-20261117.sql"));
  }
  String word="blocked-chat-"+UUID.randomUUID();
  String operator=token(admin,UserTypeEnum.ADMIN,"search:risk:view","search:risk:manage");
  mvc.perform(post("/admin/content-risk/words/save")
   .header("Authorization",operator).contentType("application/json")
   .content(com.alibaba.fastjson2.JSON.toJSONString(Map.of("word",word,"mode","BLOCK","enabled",true))))
   .andExpect(jsonPath("$.code").value(1000));
  var riskFilter=QueryWrapper.create().eq(com.cool.modules.searchrisk.SearchRisk::getContent,word);
  var committedRead=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  committedRead.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  try {
   Long id=chats.open(customer,store);
   var talking=reception.transition("WORKSPACE",staff,"TAKEOVER",new StoreChatModels.Transition(id,"blocked_chat_takeover",0L));
   int before=chats.messages("APP",customer,id,null,null,50).size();
   String body=com.alibaba.fastjson2.JSON.toJSONString(Map.of("chatId",id,"requestId","blocked_chat_001","content",word,"images",List.of()));
   mvc.perform(post("/app/support/chats/send").header("Authorization",token(otherCustomer,UserTypeEnum.APP)).contentType("application/json").content(body))
    .andExpect(jsonPath("$.code").value(403));
   long deniedCount=committedRead.execute(status->riskRecords.selectCountByQuery(riskFilter));
   assertEquals(0,deniedCount);
   mvc.perform(post("/app/support/chats/send").header("Authorization",token(customer,UserTypeEnum.APP)).contentType("application/json").content(body))
    .andExpect(jsonPath("$.code").value(422));
   assertEquals(before,chats.messages("APP",customer,id,null,null,50).size());
   var saved=committedRead.execute(status->riskRecords.selectOneByQuery(riskFilter));
   assertNotNull(saved);assertNull(saved.getOriginId());assertNull(saved.getBusinessId());
   assertEquals(customer,saved.getSubmittedById());assertEquals("BLOCKED",saved.getDetectionResult());
   assertEquals(id,saved.getChatContext().chatId());assertEquals(store,saved.getChatContext().storeId());
   assertEquals("HUMAN",saved.getChatContext().state());assertEquals(staff,saved.getChatContext().assigneeId());
   reception.transition("WORKSPACE",staff,"CLOSE",new StoreChatModels.Transition(id,"blocked_chat_close",talking.version()));
   // The enclosing fixture creates its admin in an uncommitted test transaction.
   // Use the trusted test principal for the isolated read of the committed risk record.
   var reviewer=principal(admin,UserTypeEnum.ADMIN,"search:risk:view");
   var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
   context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(reviewer,null,reviewer.getAuthorities()));
   var reviewMvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
   committedRead.executeWithoutResult(status->assertDoesNotThrow(()->reviewMvc.perform(get("/admin/content-risk/records/detail").param("id",saved.getId().toString()))
    .andExpect(jsonPath("$.code").value(1000))
    .andExpect(jsonPath("$.data.chatContext.state").value("HUMAN"))
    .andExpect(jsonPath("$.data.chatContext.customerName").isNotEmpty())
    .andExpect(jsonPath("$.data.chatContext.storeName").isNotEmpty())
    .andExpect(jsonPath("$.data.chatContext.assigneeName").isNotEmpty())
    .andExpect(jsonPath("$.data.status").value("PENDING"))));
   verifyNoInteractions(chatModel);
  } finally {
   committedRead.executeWithoutResult(status->riskRecords.deleteByQuery(riskFilter));
  }
 }
 @Test void expiredLeaseWithAutomationOffKeepsTodoAndCannotBeStolenByHeartbeat() {
  enableChatAi();chatSettings.save(staff,new StoreChatModels.SettingsInput(1,true,List.of(),false));
  Long id=chats.open(customer,store);reception.transition("WORKSPACE",staff,"TAKEOVER",new StoreChatModels.Transition(id,"lease_takeover_001",0L));
  for(var row:presenceRows.selectListByQuery(QueryWrapper.create().eq(StoreChatPresenceEntity::getActorId,staff))){row.setExpiresAt(System.currentTimeMillis()-1);presenceRows.update(row);}
  reception.expire(id);var detail=reception.detail("WORKSPACE",staff,id);
  assertEquals("WAITING",detail.state());assertTrue(detail.humanRequested());assertNull(detail.assigneeOnline());
  presence.heartbeat(staff,new StoreChatPresenceService.Input(UUID.randomUUID().toString(),true));
  assertEquals("WAITING",reception.detail("WORKSPACE",staff,id).state());
  reception.transition("WORKSPACE",staff,"CLOSE",new StoreChatModels.Transition(id,"lease_close_001",detail.version()));
  reception.expire(id);assertEquals("CLOSED",reception.detail("WORKSPACE",staff,id).state());assertFalse(reception.detail("WORKSPACE",staff,id).humanRequested());
 }
 @Test void inFlightReplyCannotCrossTakeoverAndReturnFence() {
  enableChatAi();Long id=chats.open(customer,store);
  Long question=chats.send("APP",customer,new StoreChatService.Send(id,"fence_question_01","营业时间",List.of()));
  when(chatModel.reply(any(),eq(false))).thenAnswer(call->{
   var taken=reception.transition("WORKSPACE",staff,"TAKEOVER",new StoreChatModels.Transition(id,"fence_takeover_01",1L));
   reception.transition("WORKSPACE",staff,"RETURN_TO_AI",new StoreChatModels.Transition(id,"fence_return_001",taken.version()));
   return new StoreChatModels.ModelReply("旧版本回复不可见",List.of(),false);
  });
  chatWorker.process(chatJob(question));
  assertTrue(chats.messages("APP",customer,id,null,null,50).stream().noneMatch(m->"AI".equals(m.getSenderKind())));
  assertEquals("PENDING",chatJobs.selectOneById(chatJob(question)).getState());
  doReturn(new StoreChatModels.ModelReply("新版本回复",List.of(),false)).when(chatModel).reply(any(),eq(false));
  chatWorker.process(chatJob(question));chatWorker.process(chatJob(question));
  assertEquals(1,chats.messages("APP",customer,id,null,null,50).stream().filter(m->"AI".equals(m.getSenderKind())).count());
 }
}
