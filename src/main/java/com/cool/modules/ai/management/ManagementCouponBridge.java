package com.cool.modules.ai.management;

import com.cool.modules.ai.service.CouponAssistantService;
import com.cool.core.cache.CoolCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 让全局工作台复用现有优惠券对话预览而不另写券业务规则
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:57:00
 */
@Service @RequiredArgsConstructor
public class ManagementCouponBridge {
    private final CouponAssistantService coupons;
    private final CoolCache cache;
    public record Exchange(String text,CouponAssistantService.Reply reply) {}
    public CouponAssistantService.Reply chat(Actor actor,String text,List<CouponAssistantService.Turn> history,
        CouponAssistantService.Reply previous,Long selectedId) {
        Long target=selectedId!=null?selectedId:previous!=null && previous.detail()!=null?previous.detail().getId():null;
        return coupons.chatWithinAdmittedRequest(actor.id(),actor.platform(),new CouponAssistantService.Chat(text,List.copyOf(history),null,null,target,null,
            previous!=null && previous.clarification()!=null?new CouponAssistantService.Answer(previous.clarification().token(),null):null,
            previous==null?null:previous.token(),previous==null?null:previous.queryToken()));
    }
    public void cancel(Actor actor,CouponAssistantService.Reply previous) {
        if(previous==null)return;
        if(previous.token()!=null)cache.del(CouponAssistantService.key(actor.id(),actor.platform(),previous.token()));
        if(previous.batch()!=null)for(var item:previous.batch())cache.del(CouponAssistantService.key(actor.id(),actor.platform(),item.token()));
    }
}
