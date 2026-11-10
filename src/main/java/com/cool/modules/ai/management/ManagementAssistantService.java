package com.cool.modules.ai.management;

import com.cool.core.exception.CoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 协调管理助手模块边界对话继承预览确认与失败恢复
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@Service
public class ManagementAssistantService {
    private final ManagementAssistantAccess access;
    private final ManagementAssistantModel model;
    private final ManagementAssistantTasks tasks;
    private final ObjectMapper json;
    private final ManagementCouponBridge coupons;
    private final Map<Scope,ManagementAssistantTool> tools=new EnumMap<>(Scope.class);
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private static class Session {
        final Actor actor;final String login;final Scope scope;long touched=System.currentTimeMillis();
        final List<Object> history=new ArrayList<>();final LinkedHashMap<String,Reply> replies=new LinkedHashMap<>();
        final Map<String,String> requestTexts=new HashMap<>();
        String pending;Preview pendingPreview;List<Result> facts=List.of();
        final List<com.cool.modules.ai.service.CouponAssistantService.Turn> couponHistory=new ArrayList<>();
        com.cool.modules.ai.service.CouponAssistantService.Reply couponReply;
        Session(Actor actor,String login,Scope scope){this.actor=actor;this.login=login;this.scope=scope;}
    }
    public record Context(String text,Long selectedId,Scope scope,List<Object> history,List<Result> facts,String tools) {}
    public record Capability(Scope module, boolean read, List<Action> actions) {}
    public List<Capability> capabilities(Actor actor) {
        access.identity(actor);List<Capability> list=new ArrayList<>();
        for(Scope scope:Scope.values()) {
            if(scope==Scope.GLOBAL)continue;
            String p,w;
            switch(scope) {
                case CATEGORY -> {p="merchant:category:page";w=null;}
                case STORE_TYPE -> {p="merchant:storeCategory:page";w=null;}
                case COUPON -> {p="coupon:template:page";w="coupon:view";}
                case MERCHANT -> {p="merchant:info:page";w="profile:view";}
                case STORE -> {p="merchant:store:page";w="store:view";}
                case ORDER -> {p="coupon:purchaseOrder:page";w="purchase:view";}
                case CONSUMPTION,REDEMPTION -> {p="coupon:consumptionOrder:page";w="bill:view";}
                case STAFF -> {p="workspace:administration:manage";w="account:manage";}
                case APPROVAL -> {p="approval:center:page";w="coupon:view";}
                case ACTIVITY -> {p="activity:manage";w="activity:participate";}
                default -> {p=null;w=null;}
            }
            boolean read=scope==Scope.NOTICE || access.permitted(actor,p,w);
            if(scope==Scope.APPROVAL && actor.platform())read|=access.permitted(actor,"approval:center:review",null);
            if(scope==Scope.STAFF && !actor.platform())read|=access.permitted(actor,null,"role:manage");
            if(scope==Scope.STAFF && !actor.platform())read|=access.permitted(actor,null,"grant:manage");
            List<Action> actions=new ArrayList<>();if(read)actions.add(Action.QUERY);
            if(scope==Scope.COUPON && read)actions.add(Action.COUPON);
            if(scope==Scope.CATEGORY || scope==Scope.STORE_TYPE) {
                String prefix=scope==Scope.CATEGORY?"merchant:category:":"merchant:storeCategory:";
                if(access.permitted(actor,prefix+"add",null))actions.add(Action.CREATE);
                if(access.permitted(actor,prefix+"update",null))actions.add(Action.UPDATE);
                if(access.permitted(actor,prefix+"delete",null))actions.add(Action.DELETE);
            }
            if(scope==Scope.NOTICE && access.permitted(actor,"notice:send","notice:send"))actions.add(Action.DRAFT);
            if(!actions.isEmpty())list.add(new Capability(scope,read,List.copyOf(actions)));
        }
        return list;
    }
    public ManagementAssistantService(ManagementAssistantAccess access,ManagementAssistantModel model,ManagementAssistantTasks tasks,
        ObjectMapper json,List<ManagementAssistantTool> tools,ManagementCouponBridge coupons) {
        this.access=access;this.model=model;this.tasks=tasks;this.json=json;
        this.coupons=coupons;
        tools.forEach(t->this.tools.put(t.scope(),t));
    }
    public String catalog() {
        return """
            CATEGORY 查询keyword/id；新增编辑字段name,status(0/1),sort,parentId,icon,showOnFront，仅运营有原权限可用。
            STORE_TYPE 查询keyword/id；新增编辑name,status(0/1),sort，仅运营有原权限可用。删除必须明确目标，不改关联门店。
            MERCHANT QUERY keyword/id，商家仅自身profile，门店受原profile:view权限。STORE QUERY keyword/id，前20条，原授权门店范围。
            运营MERCHANT支持fields status(0停用1启用),categoryId,statistics(true全量总数与状态分布)。运营STORE支持fields merchantId,categoryId,status(0停用1营业2休息),statistics(true全量总数与状态分布)。商家/门店不支持上述额外fields。
            ORDER QUERY：运营fields可用merchantId,status(PENDING待支付、PAID已完成、CANCELLED已取消、FAILED支付失败、EXPIRED已超时),orderNo,startTime,endTime；商家仅id或首20条，不支持其他过滤。条数用分页total；无完整金额汇总时不能将本页金额相加称全量合计。
            CONSUMPTION QUERY id或fields merchantId,storeId,status(PENDING待结算、COMPLETED已完成、CANCELLED已取消),orderNo,customerCouponId,startTime,endTime；前20条。金额字段单位分，条数以分页total为准；不提供全量金额汇总时不能根据本页推断。
            REDEMPTION QUERY：商家fields storeId,scope(self/store)返回授权列表；门店员工复用独立报表，fields period(TODAY/LAST_7_DAYS/LAST_30_DAYS/CUSTOM),startDate,endDate,operatorId,orderNo,couponName，默认今天。
            门店报表成功次数、已知消费额（元）、实际优惠（元）来自完整范围，未知金额单独标记；不能编造收入、利润或失败次数。运营REDEMPTION支持fields merchantId,storeId,period,startDate,endDate；返回完整范围成功次数与前20条核销记录，不提供收入/利润。
            STAFF QUERY fields merchantId,storeId,accountType,roleId,roles(boolean)，按原人员/岗位管理权限返回完整授权范围列表，不是分页。不允许写入。查询授权上限用fields grantSubjectType(MERCHANT/STORE/USER),grantSubjectId，必须从真实查询结果定位主体，复用原grant:manage权限，不能与其他筛选混用。
            APPROVAL QUERY fields type(MERCHANT/COUPON),status(0已撤回/未提交、1待审核、2已通过、3已驳回),publishStatus(仅COUPON：0未上架、1已上架、2已下架)及keyword/id；审核通过不等于已上架。总数用total，不能用page.list长度。商家仅指定优惠券审批详情。
            ACTIVITY QUERY前20条，商家可指定活动id，运营不支持id；无keyword或其他筛选。“支付优惠活动”是本模块名称，不是关键词条件；直接QUERY即可，只读。
            NOTICE QUERY fields sent,receipts,unread；receipts需本人已发通知id；DRAFT仅候选title/content，绝不保存发送。
            COUPON action=COUPON，fields.text=用户的优惠券需求（组合任务时仅保留券子任务，准确保留所有数值和限定），直接调用现有券助手返回查询/草稿/预览，不直接写入。可与分类/类型预览同时返回；分别核对确认，不声称整个组合已经执行。
            未列出的筛选/动作不支持，必须说明。需要按名称定位先QUERY，下一轮用真实id；同名问用户。
            """;
    }
    public Reply chat(Actor actor,String token,Request input) {
        access.identity(actor);
        if(input==null || input.scope()==null || input.text()==null || input.text().isBlank() || input.text().length()>1500
            || input.requestId()==null || !input.requestId().matches("[A-Za-z0-9_-]{8,64}"))throw new CoolException("请求参数无效",400);
        String login=login(token);String id=input.sessionId();Session s;
        synchronized(sessions) {
            sessions.entrySet().removeIf(e->e.getValue().touched+1800000<System.currentTimeMillis());
            if(id==null || id.isBlank()) {
                if(sessions.size()>=2000)throw new CoolException("会话繁忙，请稍后重试",503);
                id=UUID.randomUUID().toString();s=new Session(actor,login,input.scope());sessions.put(id,s);
            } else s=owned(actor,login,id,input.scope());
        }
        synchronized(s) {
            if(s.replies.containsKey(input.requestId())) {
                if(!Objects.equals(s.requestTexts.get(input.requestId()),input.text()+"\n#selection="+input.selectedId()))throw new CoolException("同一请求标识不能用于不同消息或目标",409);
                return s.replies.get(input.requestId());
            }
            s.touched=System.currentTimeMillis();
            List<Result> results=new ArrayList<>();List<Step> writes=new ArrayList<>();List<Result> previews=new ArrayList<>();
            Set<Step> attempted=new HashSet<>();
            String question=null;String handoff=null;
            for(int round=0;round<3;round++) {
                Plan plan=model.plan(new Context(input.text(),input.selectedId(),s.scope,List.copyOf(s.history),
                    results.isEmpty()?s.facts:List.copyOf(results),catalog()+pageMeaning(input.pageContext())+"当前身份："+access.accountType(actor)+"；当前权限能力："+capabilities(actor)));
                if(plan==null || plan.steps()==null || plan.steps().size()>6)throw new CoolException("AI任务无法解析，请换一种说法",503);
                question=plan.question();
                if(plan.reset()){cancelPending(s,id);s.history.clear();s.facts=List.of();}
                if(plan.steps().isEmpty())break;
                boolean queried=false;
                for(Step step:plan.steps()) {
                    if(step==null || step.module()==null || step.action()==null)throw new CoolException("AI任务字段不完整",503);
                    if(step.action()==Action.CANCEL){cancelPending(s,id);writes.clear();previews.clear();queried=false;question="待办已取消，已完成的操作不回滚。";break;}
                    if(s.scope!=Scope.GLOBAL && s.scope!=step.module()){handoff=input.text();break;}
                    ManagementAssistantAccess.scope(s.scope,step.module());
                    if(step.action()==Action.ASK)continue;
                    if(!attempted.add(step))continue;
                    if(step.module()==Scope.COUPON) {
                        try {
                            access.require(actor,"coupon:template:page","coupon:view");
                            String demand=step.fields()!=null && step.fields().path("text").isTextual()?step.fields().path("text").asText():input.text();
                            var reply=coupons.chat(actor,demand,s.couponHistory,s.couponReply,input.scope()==Scope.COUPON?input.selectedId():null);
                            s.couponReply=reply;s.couponHistory.add(new com.cool.modules.ai.service.CouponAssistantService.Turn("user",demand));
                            s.couponHistory.add(new com.cool.modules.ai.service.CouponAssistantService.Turn("assistant",Objects.toString(reply.text(),"")));
                            while(s.couponHistory.size()>12)s.couponHistory.remove(0);
                            results.add(new Result(Scope.COUPON,"COUPON_RESULT","优惠券真实查询或待核对结果；尚未执行写入",new ManagementCouponBridge.Exchange(demand,reply)));
                        }catch(CoolException blocked){results.add(new Result(Scope.COUPON,"FAILED",blocked.getMessage(),null));}
                        continue;
                    }
                    if(step.module()==Scope.NOTICE && step.action()==Action.DRAFT) {
                        access.require(actor,"notice:send","notice:send");
                        var draft=model.notice(new Context(input.text(),null,s.scope,s.history,s.facts,"仅title/content"));
                        results.add(new Result(Scope.NOTICE,"DRAFT","候选稿未保存、未发送，采用后仍可手改",draft));continue;
                    }
                    ManagementAssistantTool tool=tools.get(step.module());if(tool==null)throw new CoolException("此模块暂未接入",400);
                    try {
                        if(step.action()==Action.QUERY) {results.add(tool.query(actor,step));queried=true;}
                        else {previews.add(tool.preview(actor,step));writes.add(step);}
                    } catch(CoolException blocked) {
                        results.add(new Result(step.module(),"FAILED",blocked.getMessage(),null));
                        writes.clear();previews.clear();queried=true;break;
                    }
                }
                if(handoff!=null || !writes.isEmpty() || !queried || round==2)break;
                // Only another model planning turn may choose a next read or preview; no automatic writes.
            }
            Preview preview=s.pendingPreview;
            if(handoff==null && !writes.isEmpty()) {
                if(s.pending!=null)tasks.cancel(s.actor,id,s.login,s.pending);
                preview=tasks.create(actor,id,login,writes,previews);s.pending=preview.token();s.pendingPreview=preview;
            }
            String text;boolean degraded=false;
            try {text=model.answer(Map.of("user",input.text(),"results",results,"previews",previews,
                "question",Objects.toString(question,""),"tools",catalog(),"handoff",handoff==null?"":"当前侧栏不执行跨模块需求，请用户点击前往AI工作台继续"));}
            catch(CoolException e){text="";degraded=true;}
            Reply reply=new Reply(id,text,results,preview,degraded,handoff);
            s.facts=List.copyOf(results);s.history.add(Map.of("user",input.text(),"reply",reply));
            while(s.history.size()>6)s.history.remove(0);
            s.replies.put(input.requestId(),reply);s.requestTexts.put(input.requestId(),input.text()+"\n#selection="+input.selectedId());
            while(s.replies.size()>16){String oldest=s.replies.keySet().iterator().next();s.replies.remove(oldest);s.requestTexts.remove(oldest);}
            return reply;
        }
    }
    public Reply confirm(Actor actor,String token,Confirm input) {
        var s=owned(actor,login(token),input.sessionId(),null);
        synchronized(s) {
            if(!Objects.equals(s.pending,input.token()))throw new CoolException("预览已被替换或取消，请重新核对",409);
            List<Result> results;boolean failed=false;
            try {results=tasks.confirm(actor,input.sessionId(),s.login,input.token());}
            catch(CoolException blocked) {
                results=new ArrayList<>(tasks.progress(actor,input.sessionId(),s.login,input.token()));
                results.add(new Result(s.scope,"FAILED",blocked.getMessage(),null));failed=true;
            }
            String text;boolean degraded=false;
            try{text=model.answer(results);}catch(CoolException e){text="";degraded=true;}
            s.pendingPreview=failed?s.pendingPreview:null;
            s.facts=List.copyOf(results);
            Reply reply=new Reply(input.sessionId(),text,results,s.pendingPreview,degraded,null);
            s.history.add(Map.of("confirmation",reply));
            while(s.history.size()>6)s.history.remove(0);
            s.touched=System.currentTimeMillis();
            return reply;
        }
    }
    public void cancel(Actor actor,String token,String id) {
        Session s=owned(actor,login(token),id,null);synchronized(s){cancelPending(s,id);}
    }
    private void cancelPending(Session s,String id){if(s.pending!=null){tasks.cancel(s.actor,id,s.login,s.pending);s.pending=null;}s.pendingPreview=null;coupons.cancel(s.actor,s.couponReply);s.couponReply=null;s.couponHistory.clear();}
    private Session owned(Actor actor,String login,String id,Scope scope) {
        access.identity(actor);Session s=id==null?null:sessions.get(id);
        if(s==null || !s.actor.equals(actor) || !s.login.equals(login) || s.touched+1800000<System.currentTimeMillis()
            || scope!=null && scope!=s.scope)throw new CoolException("会话身份或模块范围已变化，请重新开始",409);
        return s;
    }
    private String login(String token) {
        if(token==null || token.isBlank())throw new CoolException("请先登录",401);
        return cn.hutool.crypto.SecureUtil.sha256(token);
    }
    static String pageMeaning(String page) {
        if(page==null)return "";
        return switch(page) {
            case "/approval/coupon" -> "当前页面为优惠券发布审核，未明确切换业务时APPROVAL查询type必须为COUPON。";
            case "/approval/merchant" -> "当前页面为商家入驻审核，未明确切换业务时APPROVAL查询type必须为MERCHANT。";
            default -> "";
        };
    }
}
