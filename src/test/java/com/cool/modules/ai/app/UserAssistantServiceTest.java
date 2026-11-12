package com.cool.modules.ai.app;

import static com.cool.modules.ai.app.UserAssistantModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.support.service.SupportAccess;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证用户助手鉴权会话隔离条件继承及模型故障降级
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
class UserAssistantServiceTest {
    UserAssistantModel model=mock(UserAssistantModel.class);
    UserAssistantQueries queries=mock(UserAssistantQueries.class);
    SupportAccess access=mock(SupportAccess.class);
    @SuppressWarnings("unchecked") ObjectProvider<UserAssistantPolicy> policies=mock(ObjectProvider.class);
    UserAssistantService service=new UserAssistantService(model,queries,access,policies);
    @BeforeEach void setup(){login(7L,UserTypeEnum.APP);when(model.answer(anyString(),any(),any())).thenReturn("查到了，请看卡片。");}
    @AfterEach void clean(){SecurityContextHolder.clearContext();}
    private void login(long id,UserTypeEnum type){var u=new JwtUser(id,List.of(),true);u.setUserTypeEnum(type);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of()));}
    private Plan plan(Action a,Conditions c){return new Plan(a,false,c,List.of(),null,null);}
    private void intent(Plan plan){when(model.interpret(anyString(),any(),anyList(),nullable(Target.class),anyBoolean())).thenReturn(plan);}
    private Chat chat(String session,String text){return new Chat(session,text,null,null);}

    @Test void guestsAndMerchantAccountsCannotUseAnyAi(){
        SecurityContextHolder.clearContext();assertThrows(CoolException.class,()->service.chat(chat(null,"你好")));
        login(7,UserTypeEnum.MERCHANT);assertThrows(CoolException.class,()->service.chat(chat(null,"你好")));
        verifyNoInteractions(model,queries);
    }
    @Test void selectedStoreBypassesStaleNearbyConditionAndSurvivesFollowup() {
        var target=new Target(Kind.STORE,9L);
        intent(plan(Action.STORES,new Conditions(null,null,null,null,null,null,true)));
        var card=new Card(Kind.STORE,9L,"指定店","商家","","营业",null,false,"STORE_DETAIL",List.of());
        when(queries.query(eq(7L),eq(Action.STORES),any(),isNull(),eq(target),isNull())).thenReturn(new Result(List.of(card),false,"指定门店"));
        var first=service.chat(new Chat(null,"这家店在哪",null,target));
        assertEquals(9L,first.cards().get(0).id());
        var second=service.chat(chat(first.sessionId(),"营业时间呢"));
        assertEquals(9L,second.cards().get(0).id());
        verify(model).interpret(eq("营业时间呢"),any(),anyList(),eq(target),eq(false));
        intent(new Plan(Action.STORES,true,Conditions.empty(),List.of(),null,null));
        when(queries.query(eq(7L),eq(Action.STORES),any(),isNull(),isNull(),isNull())).thenReturn(new Result(List.of(),false,"新搜索"));
        service.chat(chat(first.sessionId(),"重新找其他门店"));
        verify(queries).query(eq(7L),eq(Action.STORES),any(),isNull(),isNull(),isNull());
    }
    @Test void replacingExplicitStoreKeepsNewTargetEvenWhenModelResets() {
        intent(new Plan(Action.STORES,true,Conditions.empty(),List.of(),null,null));
        when(queries.query(eq(7L),eq(Action.STORES),any(),isNull(),any(Target.class),isNull()))
            .thenAnswer(call->{Target target=call.getArgument(4);return new Result(List.of(new Card(Kind.STORE,target.id(),"目标门店","商家","","营业",null,false,"STORE_DETAIL",List.of())),false,"指定门店");});
        var first=service.chat(new Chat(null,"查看这家店",null,new Target(Kind.STORE,9L)));
        var second=service.chat(new Chat(first.sessionId(),"换成这家",null,new Target(Kind.STORE,10L)));
        assertEquals(10L,second.cards().get(0).id());
        intent(plan(Action.STORES,null));
        assertEquals(10L,service.chat(chat(first.sessionId(),"地址呢")).cards().get(0).id());
    }
    @Test void inaccessibleSelectedStoreDoesNotProduceAnAnswerOrReplacement() {
        intent(plan(Action.STORES,null));
        var target=new Target(Kind.STORE,99L);
        when(queries.query(eq(7L),eq(Action.STORES),any(),isNull(),eq(target),isNull()))
            .thenThrow(new CoolException("门店不存在或暂不可展示",404));
        assertEquals(404,assertThrows(CoolException.class,()->service.chat(new Chat(null,"这家店在哪",null,target))).getCode());
        verify(model,never()).answer(anyString(),any(),any());
        verify(queries,times(1)).query(anyLong(),any(),any(),nullable(Location.class),nullable(Target.class),nullable(String.class));
    }
    @Test void disabledCustomerIsRejectedBeforeModel(){
        doThrow(new CoolException("用户已失效",403)).when(access).customer(7L);
        assertThrows(CoolException.class,()->service.chat(chat(null,"你好")));verifyNoInteractions(model);
    }
    @Test void sessionCannotBeReadOrClosedByOtherUser(){
        intent(plan(Action.CHAT,null));Reply first=service.chat(chat(null,"你好"));
        login(8,UserTypeEnum.APP);
        assertEquals(404,assertThrows(CoolException.class,()->service.chat(chat(first.sessionId(),"继续"))).getCode());
        assertThrows(CoolException.class,()->service.close(first.sessionId()));
        login(7,UserTypeEnum.APP);service.close(first.sessionId());
        assertThrows(CoolException.class,()->service.chat(chat(first.sessionId(),"继续")));
    }
    @Test void nearbyWithoutLocationAsksOnlyForEssentialArea(){
        intent(plan(Action.STORES,new Conditions(null,null,null,null,null,null,true)));
        Reply reply=service.chat(chat(null,"附近有什么店"));assertTrue(reply.message().contains("城市"));verifyNoInteractions(queries);
    }
    @Test void areaWorksWithoutGpsAndCheapNeedsNoFollowup(){
        Conditions c=new Conditions("福州",null,null,null,null,true,true);intent(plan(Action.COUPONS,c));
        when(queries.query(eq(7L),eq(Action.COUPONS),any(),isNull(),isNull(),isNull())).thenReturn(new Result(List.of(),false,"按区域查询"));
        Reply reply=service.chat(chat(null,"福州便宜的券"));assertFalse(reply.degraded());verify(queries).query(eq(7L),eq(Action.COUPONS),any(),isNull(),isNull(),isNull());
    }
    @Test void modelFailureDoesNotRunQueriesAndWriterFailurePreservesRealCards(){
        when(model.interpret(anyString(),any(),anyList(),nullable(Target.class),anyBoolean())).thenThrow(new IllegalStateException("secret"));
        Reply failed=service.chat(chat(null,"找券"));assertTrue(failed.degraded());assertFalse(failed.message().contains("secret"));verifyNoInteractions(queries);
        reset(model);intent(plan(Action.COUPONS,Conditions.empty()));
        var card=new Card(Kind.COUPON,1L,"真实券","真实商家","","可领购",0L,true,"COUPON_DETAIL",List.of());
        when(queries.query(anyLong(),any(),any(),nullable(Location.class),nullable(Target.class),nullable(String.class))).thenReturn(new Result(List.of(card),false,"以详情为准"));
        when(model.answer(anyString(),any(),any())).thenThrow(new IllegalStateException());
        Reply fallback=service.chat(chat(null,"找券"));assertTrue(fallback.degraded());assertEquals(List.of(card),fallback.cards());
    }
    @Test void policyDenialPrecedesModelAndUnlimitedDefaultWorks(){
        var gate=mock(UserAssistantPolicy.class);when(policies.getIfAvailable()).thenReturn(gate);
        doThrow(new CoolException("今日额度用完",429)).when(gate).checkAndConsume(7L);
        assertEquals(429,assertThrows(CoolException.class,()->service.chat(chat(null,"找券"))).getCode());verifyNoInteractions(model);
        when(policies.getIfAvailable()).thenReturn(null);intent(plan(Action.CHAT,null));assertFalse(service.chat(chat(null,"你好")).degraded());
    }
    @Test void explicitCorrectionReplacesOnlyNamedConditionsAndKeepsBudgetsSeparate(){
        Conditions old=new Conditions("福州","咖啡",PriceMode.FREE,0L,5000L,true,false);
        Plan correction=new Plan(Action.COUPONS,false,new Conditions("厦门",null,PriceMode.PAID,null,null,null,null),List.of("maxPriceCents"),null,null);
        Conditions next=UserAssistantService.merge(old,correction);
        assertEquals("厦门",next.area());assertEquals("咖啡",next.keyword());assertEquals(PriceMode.PAID,next.priceMode());assertNull(next.maxPriceCents());assertEquals(5000L,next.spendBudgetCents());
        assertNull(UserAssistantService.merge(old,new Plan(Action.COUPONS,true,null,List.of(),null,null)).area());
    }
    @Test void writeRequestNeverCallsBusinessQuery(){intent(plan(Action.UNSUPPORTED,null));assertTrue(service.chat(chat(null,"帮我支付订单")).message().contains("详情页"));verifyNoInteractions(queries);}
}
