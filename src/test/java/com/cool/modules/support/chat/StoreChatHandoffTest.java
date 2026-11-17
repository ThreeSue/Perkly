package com.cool.modules.support.chat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.cool.modules.support.entity.*;
import com.cool.modules.support.mapper.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.core.exception.CoolException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证人工交接权限待办状态及未答任务恢复
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 16:10:00
 */
class StoreChatHandoffTest {
 StoreChatAccess access=mock(StoreChatAccess.class);StoreChatSettingsService settings=mock(StoreChatSettingsService.class);
 StoreChatMapper chats=mock(StoreChatMapper.class);StoreChatMessageMapper messages=mock(StoreChatMessageMapper.class);
 StoreChatPresenceService presence=mock(StoreChatPresenceService.class);StoreChatAiAvailability availability=mock(StoreChatAiAvailability.class);
 StoreChatAiJobMapper jobs=mock(StoreChatAiJobMapper.class);
 StoreChatReception service=spy(new StoreChatReception(access,settings,chats,messages,mock(BaseSysUserMapper.class),mock(MerchantStoreMapper.class)));
 StoreChatEntity chat=new StoreChatEntity();
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(service,"presence",presence);ReflectionTestUtils.setField(service,"availability",availability);ReflectionTestUtils.setField(service,"jobs",jobs);
  chat.setId(1L);chat.setStoreId(3L);chat.setCustomerId(9L);chat.setState("HUMAN");chat.setAssigneeId(2L);chat.setRound(1);chat.setVersion(4L);
  when(access.own(anyString(),anyLong(),eq(1L),anyString(),anyBoolean())).thenReturn(chat);
  when(access.permitted(anyLong(),anyString())).thenReturn(true);
  when(chats.selectOneByQuery(any())).thenReturn(chat);
  when(settings.read(eq(3L),anyBoolean())).thenReturn(new StoreChatModels.Settings(0,true,List.of(),true));
  doNothing().when(service).save(any());
 }
 StoreChatModels.Transition input(){return new StoreChatModels.Transition(1L,"test-request-12345",4L);}
 @Test void returnOnlyByAssigneeAndUnavailablePreservesState(){
  assertThrows(CoolException.class,()->service.transition("WORKSPACE",7L,"RETURN_TO_AI",input()));
  when(availability.reason(3L)).thenReturn("QUOTA_EXHAUSTED");
  assertThrows(CoolException.class,()->service.transition("WORKSPACE",2L,"RETURN_TO_AI",input()));
  assertEquals("HUMAN",chat.getState());assertEquals(4L,chat.getVersion());verify(messages,never()).insert(any(StoreChatMessageEntity.class));
 }
 @Test void returnClearsPendingAndResumesUnansweredQuestion(){
  var question=new StoreChatMessageEntity();question.setId(88L);
  when(messages.selectOneByQuery(any())).thenReturn(null,question);
  chat.setHumanRequested(true);
  var result=service.transition("WORKSPACE",2L,"RETURN_TO_AI",input());
  assertEquals("AI",result.state());assertFalse(result.humanRequested());assertNull(result.assigneeId());assertEquals(1,result.round());
  assertEquals(88L,chat.getLatestQuestionId());verify(jobs).insert(any(StoreChatAiJobEntity.class));
 }
 @Test void handoffKeepsAiButPreservesHumanTodo(){
  chat.setState("AI");chat.setAssigneeId(null);
  var result=service.transition("APP",9L,"HANDOFF",input());assertEquals("AI",result.state());assertTrue(result.humanRequested());assertFalse(result.canReturnToAi());assertFalse(result.canManageSettings());
 }
 @Test void offlineDisabledAutomationLeavesWaitingTodo(){
  when(settings.read(eq(3L),anyBoolean())).thenReturn(new StoreChatModels.Settings(0,true,List.of(),false));
  service.expire(1L);assertEquals("WAITING",chat.getState());assertTrue(chat.getHumanRequested());assertNull(chat.getAssigneeId());assertEquals(5L,chat.getVersion());verifyNoInteractions(jobs);
  service.expire(1L);verify(messages,times(1)).insert(any(StoreChatMessageEntity.class));
 }
 @Test void anotherDeviceOnlinePreventsExpiry(){when(presence.online(2L,3L)).thenReturn(true);service.expire(1L);assertEquals("HUMAN",chat.getState());verify(messages,never()).insert(any(StoreChatMessageEntity.class));}
 @Test void runningOldJobCannotBeReusedBeforeItsReplyIsDiscarded(){
  chat.setState("AI");var question=new StoreChatMessageEntity();question.setId(88L);when(messages.selectOneByQuery(any())).thenReturn(question);
  var job=new StoreChatAiJobEntity();job.setState("RUNNING");when(jobs.selectOneByQuery(any())).thenReturn(job);
  service.resume(chat);verify(jobs,never()).update(any(StoreChatAiJobEntity.class),anyBoolean());assertNull(chat.getLatestQuestionId());
 }
 @Test void alreadyAnsweredQuestionDoesNotProduceAnotherAiJob(){chat.setState("AI");var q=new StoreChatMessageEntity();q.setId(88L);when(messages.selectOneByQuery(any())).thenReturn(q);when(messages.selectCountByQuery(any())).thenReturn(1L);service.resume(chat);verifyNoInteractions(jobs);}

 @Test void replyIndicatorUsesPersistedJobAndExpires(){
  chat.setState("AI");chat.setLatestQuestionId(88L);
  var job=new StoreChatAiJobEntity();job.setState("PENDING");job.setCreateTime(new java.util.Date());
  when(jobs.selectOneByQuery(any())).thenReturn(job);
  assertEquals("PENDING",service.aiReplyStatus(chat));
  job.setState("RUNNING");job.setLeaseUntil(System.currentTimeMillis()+60000);
  assertEquals("RUNNING",service.aiReplyStatus(chat));
  job.setLeaseUntil(System.currentTimeMillis()-1);
  assertEquals("TIMEOUT",service.aiReplyStatus(chat));
  job.setState("DONE");assertEquals("IDLE",service.aiReplyStatus(chat));
  chat.setState("HUMAN");assertEquals("IDLE",service.aiReplyStatus(chat));
 }
}
