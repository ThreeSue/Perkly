package com.cool.modules.ai.management;
import org.junit.jupiter.api.*;
import com.cool.core.exception.CoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证管理助手模块边界登录隔离预览确认及模型失败结果保留
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
class ManagementAssistantServiceTest {
    ManagementAssistantAccess access=mock(ManagementAssistantAccess.class);
    ManagementAssistantModel model=mock(ManagementAssistantModel.class);
    ManagementAssistantTasks tasks=mock(ManagementAssistantTasks.class);
    ManagementAssistantTool category=mock(ManagementAssistantTool.class);
    ManagementCouponBridge coupons=mock(ManagementCouponBridge.class);
    ObjectMapper json=new ObjectMapper();
    ManagementAssistantService service;
    Actor actor=new Actor(7L,true);
    @BeforeEach void setup() {
        when(category.scope()).thenReturn(Scope.CATEGORY);
        service=new ManagementAssistantService(access,model,tasks,json,List.of(category),coupons);
        when(model.answer(any())).thenReturn("请核对结果。");
    }
    Request request(String id,String key,Scope scope,String text){return new Request(id,key,scope,text,null);}
    Step query(){return new Step(Scope.CATEGORY,Action.QUERY,null,"餐饮",null);}
    void onePlan(Step step){when(model.plan(any())).thenReturn(new Plan(null,false,List.of(step)),new Plan("查到了",false,List.of()));}
    @Test void sideScopeCannotInvokeOtherTools() {
        onePlan(query());
        Reply reply=service.chat(actor,"token",request(null,"request01",Scope.NOTICE,"查餐饮分类"));
        assertEquals("查餐饮分类",reply.handoffText());verify(category,never()).query(any(),any());verifyNoInteractions(tasks);
    }
    @Test void globalCanReadAndRetainFactsWhenNarrationFails() {
        onePlan(query());Result result=new Result(Scope.CATEGORY,"READ","数据",List.of("餐饮"));
        when(category.query(any(),any())).thenReturn(result);when(model.answer(any())).thenThrow(new CoolException("模型不可用",503));
        Reply reply=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"查餐饮分类"));
        assertTrue(reply.degraded());assertEquals(List.of(result),reply.results());
    }
    @Test void anotherAccountOrTokenCannotReuseConversation() {
        when(model.plan(any())).thenReturn(new Plan("需要什么帮助",false,List.of()));
        var r=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"你好"));
        assertThrows(CoolException.class,()->service.chat(new Actor(8L,true),"token",request(r.sessionId(),"request02",Scope.GLOBAL,"继续")));
        assertThrows(CoolException.class,()->service.chat(actor,"other-token",request(r.sessionId(),"request02",Scope.GLOBAL,"继续")));
    }
    @Test void changingScopeRequiresExplicitNewConversation() {
        when(model.plan(any())).thenReturn(new Plan("你好",false,List.of()));
        var r=service.chat(actor,"token",request(null,"request01",Scope.NOTICE,"你好"));
        assertThrows(CoolException.class,()->service.chat(actor,"token",request(r.sessionId(),"request02",Scope.GLOBAL,"继续")));
    }
    @Test void duplicateMessageIsReplayedButChangedPayloadIsRejected() {
        when(model.plan(any())).thenReturn(new Plan("你好",false,List.of()));
        var r=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"你好"));
        assertEquals(r,service.chat(actor,"token",request(r.sessionId(),"request01",Scope.GLOBAL,"你好")));
        assertThrows(CoolException.class,()->service.chat(actor,"token",request(r.sessionId(),"request01",Scope.GLOBAL,"换内容")));
        verify(model,times(1)).plan(any());
    }
    @Test void createOnlyPreviewsAndCancelInvalidatesConfirmation() {
        var step=new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode().put("name","测试"));
        when(model.plan(any())).thenReturn(new Plan(null,false,List.of(step)));
        Result preview=new Result(Scope.CATEGORY,"PREVIEW","尚未保存",null);
        when(category.preview(any(),any())).thenReturn(preview);
        when(tasks.create(any(),anyString(),anyString(),anyList(),anyList())).thenReturn(new Preview("preview",List.of(preview)));
        var r=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"新建分类"));
        verify(category,never()).execute(any(),any(),any());assertNotNull(r.preview());
        service.cancel(actor,"token",r.sessionId());
        assertThrows(CoolException.class,()->service.confirm(actor,"token",new Confirm(r.sessionId(),"preview")));
        verify(tasks).cancel(eq(actor),eq(r.sessionId()),anyString(),eq("preview"));
    }
    @Test void readOnlyToolRejectsWriteByDefault() {
        ManagementAssistantTool read=new ManagementAssistantTool() {
            public Scope scope(){return Scope.STORE;}
            public Result query(Actor a,Step s){return null;}
        };
        assertThrows(CoolException.class,()->read.preview(actor,new Step(Scope.STORE,Action.DELETE,1L,null,null)));
    }
    @Test void clarificationRetainsPreviewAndCancellationCannotQueueEarlierSteps() {
        var create=new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode().put("name","测试"));
        var pending=new Preview("preview",List.of(new Result(Scope.CATEGORY,"PREVIEW","尚未保存",null)));
        when(category.preview(any(),any())).thenReturn(pending.steps().get(0));
        when(tasks.create(any(),anyString(),anyString(),anyList(),anyList())).thenReturn(pending);
        when(model.plan(any())).thenReturn(new Plan(null,false,List.of(create)));
        var first=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"新建分类"));
        when(model.plan(any())).thenReturn(new Plan("确认前不会保存",false,List.of()));
        var explanation=service.chat(actor,"token",request(first.sessionId(),"request02",Scope.GLOBAL,"现在保存了吗"));
        assertEquals(pending,explanation.preview());
        when(model.plan(any())).thenReturn(new Plan(null,false,List.of(create,new Step(Scope.CATEGORY,Action.CANCEL,null,null,null))));
        var cancelled=service.chat(actor,"token",request(first.sessionId(),"request03",Scope.GLOBAL,"算了取消"));
        assertNull(cancelled.preview());
        verify(tasks,times(1)).create(any(),anyString(),anyString(),anyList(),anyList());
    }
    @Test void scopeGuardIsServerEnforced() {
        assertThrows(CoolException.class,()->ManagementAssistantAccess.scope(Scope.NOTICE,Scope.CATEGORY));
        assertDoesNotThrow(()->ManagementAssistantAccess.scope(Scope.GLOBAL,Scope.CATEGORY));
    }
    @Test void couponAndCategoryUseRealAdapterWithoutLosingOtherPreview() {
        var coupon=new Step(Scope.COUPON,Action.COUPON,null,null,json.createObjectNode().put("text","查询烧烤券"));
        var create=new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode().put("name","餐饮"));
        var response=new com.cool.modules.ai.service.CouponAssistantService.Reply("查到了券",List.of(),null,List.of(),null);
        when(coupons.chat(eq(actor),eq("查询烧烤券"),anyList(),isNull(),isNull())).thenReturn(response);
        when(model.plan(any())).thenReturn(new Plan(null,false,List.of(coupon,create)));
        var preview=new Result(Scope.CATEGORY,"PREVIEW","尚未保存",null);
        when(category.preview(any(),any())).thenReturn(preview);
        when(tasks.create(any(),anyString(),anyString(),anyList(),anyList())).thenReturn(new Preview("both",List.of(preview)));
        var reply=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"查询烧烤券，再新增餐饮分类"));
        assertEquals("COUPON_RESULT",reply.results().get(0).state());assertNotNull(reply.preview());
        verify(coupons,never()).cancel(any(),any());verify(category,never()).execute(any(),any(),any());
    }
    @Test void blockedPreviewCanReadFactsAndAskInsteadOfRepeatingFailure() {
        var write=new Step(Scope.CATEGORY,Action.UPDATE,1L,null,json.createObjectNode().put("sort",-1));
        when(model.plan(any())).thenReturn(new Plan(null,false,List.of(write)),new Plan("排序不能为负，请提供非负值。",false,List.of(query())),new Plan("要调整为多少？",false,List.of()));
        when(category.preview(any(),any())).thenThrow(new CoolException("排序必须非负",400));
        when(category.query(any(),any())).thenReturn(new Result(Scope.CATEGORY,"READ","当前资料",List.of()));
        var reply=service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"把排序改成-1"));
        assertEquals(2,reply.results().size());assertNull(reply.preview());verify(model,times(3)).plan(any());
    }
    @Test void changingSelectedTargetCannotReplaySameRequest() {
        when(model.plan(any())).thenReturn(new Plan("你好",false,List.of()));
        var first=service.chat(actor,"token",new Request(null,"request01",Scope.CATEGORY,"看看",1L));
        assertThrows(CoolException.class,()->service.chat(actor,"token",new Request(first.sessionId(),"request01",Scope.CATEGORY,"看看",2L)));
    }
    @Test void repeatedReadPlanRunsToolOnlyOnce() {
        when(model.plan(any())).thenReturn(new Plan(null,false,List.of(query())));
        when(category.query(any(),any())).thenReturn(new Result(Scope.CATEGORY,"READ","数据",List.of()));
        service.chat(actor,"token",request(null,"request01",Scope.GLOBAL,"查餐饮分类"));
        verify(category,times(1)).query(any(),any());
    }
}
