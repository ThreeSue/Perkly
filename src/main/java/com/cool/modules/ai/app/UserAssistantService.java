package com.cool.modules.ai.app;

import static com.cool.modules.ai.app.UserAssistantModels.*;
import com.cool.core.exception.CoolException;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.merchant.location.LocationModels;
import com.cool.modules.support.service.SupportAccess;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 强制登录及会话归属并协调用户助手只读查询和短期条件继承
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
@Service
public class UserAssistantService {
    private final UserAssistantModel model;
    private final UserAssistantQueries queries;
    private final SupportAccess access;
    private final ObjectProvider<UserAssistantPolicy> policy;
    private final Cache<String,Session> sessions=Caffeine.newBuilder().maximumSize(2000)
        .expireAfterAccess(Duration.ofMinutes(30)).build();
    public UserAssistantService(UserAssistantModel model,UserAssistantQueries queries,SupportAccess access,
                                ObjectProvider<UserAssistantPolicy> policy) {
        this.model=model;this.queries=queries;this.access=access;this.policy=policy;
    }
    private static final class Session {
        final Long customer;Conditions conditions=Conditions.empty();Location location;Target target;
        Action action;String state;final List<Turn> history=new ArrayList<>();
        Session(Long customer){this.customer=customer;}
    }
    private Long customer() {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof JwtUser user)
            || user.getUserTypeEnum()!=UserTypeEnum.APP || user.getUserId()==null)
            throw new CoolException("请使用用户账号登录",401);
        access.customer(user.getUserId());return user.getUserId();
    }

    public Reply chat(Chat input) {
        Long customer=customer();
        if(input==null || input.message()==null || input.message().isBlank() || input.message().length()>1500)
            throw new CoolException("请发送1至1500字的问题");
        if(input.location()!=null) LocationModels.coordinate(input.location().longitude(),input.location().latitude());
        if(input.target()!=null && (input.target().kind()==null || input.target().id()==null || input.target().id()<1))
            throw new CoolException("请选择有效的记录");
        String id=input.sessionId();Session session;
        if(id==null || id.isBlank()) {id=UUID.randomUUID().toString();session=new Session(customer);sessions.put(id,session);}
        else {session=ownedSession(id,customer);}
        // 独立策略由现有AI控制台注入；未注入时使用本批约定的启用且不限次数。
        UserAssistantPolicy gate=policy.getIfAvailable();if(gate!=null)gate.checkAndConsume(customer);
        synchronized(session) {
            Location location=input.location()!=null?input.location():session.location;
            Target selection=input.target()!=null?input.target():session.target;
            Plan plan;
            try {plan=model.interpret(input.message(),session.conditions,List.copyOf(session.history),selection,location!=null);validate(plan);}
            catch(Exception unavailable) {return new Reply(id,"暂时没能理解这条消息，请稍后重试；你也可以直接进入门店、优惠券或我的券页面。",List.of(),suggestions(),session.conditions,true,false);}
            Conditions conditions=merge(session.conditions,plan);
            if(Boolean.TRUE.equals(plan.reset()) && input.target()==null)selection=null;
            String state=plan.state()!=null?plan.state():!Boolean.TRUE.equals(plan.reset()) && plan.action()==session.action?session.state:null;
            Result result;String message;boolean degraded=false;
            if(plan.action()==Action.CHAT || plan.action()==Action.ASK || plan.action()==Action.UNSUPPORTED) {
                message=plan.action()==Action.UNSUPPORTED?"我可以帮你找券、查规则和记录。领取、下单或支付请进入相应详情页操作。":
                    plan.question()==null || plan.question().isBlank()?"可以告诉我想找的门店或优惠券，也可以查我的券和订单。":plan.question();
                result=new Result(List.of(),false,"");
            } else if(Boolean.TRUE.equals(conditions.nearby()) && location==null && conditions.area()==null) {
                message="告诉我所在城市或区域就能先帮你找，也可以提供位置查看10公里内的门店。";
                result=new Result(List.of(),false,"");
            } else {
                result=queries.query(customer,plan.action(),conditions,location,selection,state);
                try {message=model.answer(input.message(),conditions,result);}
                catch(Exception unavailable) {degraded=true;message=(result.cards().isEmpty()?"这次没有找到匹配结果。":"已查到以下真实记录，可点击卡片查看。")+result.note();}
            }
            if(result.cards().size()==1 && (result.cards().get(0).kind()==Kind.COUPON || result.cards().get(0).kind()==Kind.WALLET)) {
                Card only=result.cards().get(0);selection=new Target(only.kind(),only.id());
            }
            session.conditions=conditions;session.location=location;session.target=selection;session.action=plan.action();session.state=state;
            session.history.add(new Turn(input.message(),message));
            while(session.history.size()>6)session.history.remove(0);
            return new Reply(id,message,result.cards(),suggestions(),conditions,degraded,result.partial());
        }
    }
    public void close(String id) {Long customer=customer();ownedSession(id,customer);sessions.invalidate(id);}
    private Session ownedSession(String id,Long customer) {
        if(id==null || id.length()>64)throw new CoolException("会话不存在或已过期",404);
        Session s=sessions.getIfPresent(id);
        if(s==null || !s.customer.equals(customer))throw new CoolException("会话不存在或已过期",404);
        return s;
    }
    private List<String> suggestions(){return List.of("推荐便宜的券","看看我的券","查看我的订单");}

    static Conditions merge(Conditions before,Plan plan) {
        Conditions base=Boolean.TRUE.equals(plan.reset())?Conditions.empty():before;
        Conditions patch=plan.conditions()==null?new Conditions(null,null,null,null,null,null,null):plan.conditions();
        Set<String> clear=new HashSet<>(plan.clear()==null?List.of():plan.clear());
        return new Conditions(value("area",patch.area(),base.area(),clear),value("keyword",patch.keyword(),base.keyword(),clear),
            Optional.ofNullable(value("priceMode",patch.priceMode(),base.priceMode(),clear)).orElse(PriceMode.ANY),
            value("maxPriceCents",patch.maxPriceCents(),base.maxPriceCents(),clear),value("spendBudgetCents",patch.spendBudgetCents(),base.spendBudgetCents(),clear),
            value("cheap",patch.cheap(),base.cheap(),clear),value("nearby",patch.nearby(),base.nearby(),clear));
    }
    private static <T>T value(String key,T patch,T base,Set<String> clear){return patch!=null?patch:clear.contains(key)?null:base;}
    private void validate(Plan plan) {
        if(plan==null || plan.action()==null)throw new IllegalArgumentException("缺少意图");
        if(plan.question()!=null && plan.question().length()>500)throw new IllegalArgumentException("回复过长");
        if(plan.clear()!=null && !Set.of("area","keyword","priceMode","maxPriceCents","spendBudgetCents","cheap","nearby").containsAll(plan.clear()))
            throw new IllegalArgumentException("未知条件");
        Conditions c=plan.conditions();
        if(c!=null) {
            if(c.area()!=null && (c.area().isBlank() || c.area().length()>100) || c.keyword()!=null && (c.keyword().isBlank() || c.keyword().length()>100))throw new IllegalArgumentException("条件过长");
            for(Long amount:new Long[]{c.maxPriceCents(),c.spendBudgetCents()}) if(amount!=null && (amount<0 || amount>100000000))throw new IllegalArgumentException("金额无效");
        }
        if(plan.state()!=null) {
            Set<String> allowed=plan.action()==Action.WALLET?Set.of("UNUSED","USED","VOID","EXPIRED","NOT_STARTED"):
                plan.action()==Action.ORDERS?Set.of("PENDING","PAID","CANCELLED","FAILED","EXPIRED"):Set.of();
            if(!allowed.contains(plan.state()))throw new IllegalArgumentException("状态无效");
        }
    }
}
