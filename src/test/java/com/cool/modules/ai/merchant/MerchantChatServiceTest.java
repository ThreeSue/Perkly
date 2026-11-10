package com.cool.modules.ai.merchant;
import static com.cool.modules.ai.merchant.MerchantChatModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.cool.core.exception.CoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证门店聊天会话隔离继承只读拒绝与模型失败降级
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:29:18
 */
class MerchantChatServiceTest {
 final MerchantAiAccess access=mock(MerchantAiAccess.class);
 final MerchantChatModel model=mock(MerchantChatModel.class);
 final MerchantChatQueries queries=mock(MerchantChatQueries.class);
 final ObjectMapper json=new ObjectMapper();
 final MerchantChatService service=new MerchantChatService(access,new MerchantChatPolicy(true,30),model,queries,json);
 final Conditions conditions=Conditions.empty();
 MerchantChatServiceTest(){
  when(access.require(anyLong(),isNull())).thenAnswer(i->new MerchantAiAccess.Scope(i.getArgument(0),2L,3L,null));
  when(model.plan(anyString(),any(),anyLong())).thenReturn(new Plan(Action.STATISTICS,conditions,null));
  when(queries.query(anyLong(),any(),any(),isNull())).thenReturn(json.createObjectNode().put("successfulRedemptions",0));
  when(model.answer(any())).thenReturn("今天暂无成功核销记录。");
 }
 Reply start(){return service.chat(1L,"login-a",new Chat(null,"今天核销几张",null,null,null));}
 @Test void bindsActorAndLoginAndStore(){
  var r=start();
  assertThrows(CoolException.class,()->service.chat(2L,"login-a",new Chat(r.sessionId(),"继续",null,null,null)));
  assertThrows(CoolException.class,()->service.chat(1L,"login-b",new Chat(r.sessionId(),"继续",null,null,null)));
  when(access.require(1L,null)).thenReturn(new MerchantAiAccess.Scope(1L,2L,4L,null));
  assertThrows(CoolException.class,()->service.chat(1L,"login-a",new Chat(r.sessionId(),"继续",null,null,null)));
 }
 @Test void carriesConditionsAndClearInvalidates(){
  var c=new Conditions("LAST_7_DAYS",null,null,null,null,null,null);
  when(model.plan(anyString(),any(),anyLong())).thenReturn(new Plan(Action.STATISTICS,c,null));
  var r=start();service.chat(1L,"login-a",new Chat(r.sessionId(),"那今天呢",null,null,null));
  verify(model).plan("那今天呢",c,1L);
  service.clear(1L,"login-a",r.sessionId());
  assertThrows(CoolException.class,()->service.chat(1L,"login-a",new Chat(r.sessionId(),"继续",null,null,null)));
 }
 @Test void writesNeverDispatch(){
  when(model.plan(anyString(),any(),anyLong())).thenReturn(new Plan(Action.UNSUPPORTED,conditions,"已核销"));
  var r=start();assertEquals("NONE",r.operation());assertFalse(r.message().contains("已核销"));verifyNoInteractions(queries);
 }
 @Test void modelInterpretFailureIsRetryableAndDoesNotQuery(){
  when(model.plan(anyString(),any(),anyLong())).thenThrow(new IllegalStateException("timeout"));
  assertEquals(503,assertThrows(CoolException.class,this::start).getCode());verifyNoInteractions(queries);
 }
 @Test void narrativeFailurePreservesRealData(){
  when(model.answer(any())).thenThrow(new IllegalStateException());var r=start();
  assertTrue(r.degraded());assertEquals(0,r.cards().get("successfulRedemptions").asInt());
 }
 @Test void secretsNotSentToInterpreterAndDatesRemain(){
  service.chat(1L,"login-a",new Chat(null,"这张券 ABCDEF123456 在2026-11-01能用吗",null,null,"ABCDEF123456"));
  verify(model).plan("这张券 [标识已隐藏] 在2026-11-01能用吗",conditions,1L);
 }
 @Test void policyFailsClosedAndCapsRequests(){
  assertEquals(503,assertThrows(CoolException.class,()->new MerchantChatPolicy(false,10).consume(1L)).getCode());
  var p=new MerchantChatPolicy(true,1);p.consume(1L);assertEquals(429,assertThrows(CoolException.class,()->p.consume(1L)).getCode());
 }
 @Test void permissionFailureNeverCallsModel(){
  when(access.require(1L,null)).thenThrow(new CoolException("拒绝",403));
  assertThrows(CoolException.class,this::start);verifyNoInteractions(model,queries);
 }
}
