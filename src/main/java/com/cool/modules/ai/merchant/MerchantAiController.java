package com.cool.modules.ai.merchant;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 暴露门店助手只读统计接口并依赖现有登录与路由授权
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:18:58
 */
@RestController @RequestMapping("/merchant/ai/assistant") @RequiredArgsConstructor
public class MerchantAiController {
    private final MerchantAiStatistics statistics;
    private final MerchantChatService chat;
    @PostMapping("/chat")
    public R<?> chat(@RequestBody MerchantChatModels.Chat input,HttpServletRequest request) {
        return R.ok(chat.chat(actor(request),request.getHeader("Authorization"),input));
    }
    @DeleteMapping("/session")
    public R<?> clear(@RequestParam String sessionId,HttpServletRequest request) {
        chat.clear(actor(request),request.getHeader("Authorization"),sessionId);return R.ok();
    }
    private final MerchantAiAccess access;
    private final com.cool.modules.workspace.service.WorkspaceAuthorization authorization;
    private final com.cool.modules.coupon.service.CouponTradeService trade;
    private final com.cool.modules.coupon.service.ConsumptionOrderService orders;
    private final com.cool.modules.merchant.service.MerchantWorkspaceService workspace;
    public record CouponInput(Long couponId,String keyword) {}
    public record InspectInput(String code) {}
    public record OrderInput(Long orderId) {}
    private Long actor(HttpServletRequest request) {
        if(request.getAttribute("merchantUserId") instanceof Long id)return id;
        throw new CoolException("请先登录门店账号",401);
    }
    @PostMapping("/coupons")
    public R<?> coupons(@RequestBody CouponInput input,HttpServletRequest request) {
        Long actor=actor(request);access.require(actor,null);authorization.require(actor,"coupon:view");
        return R.ok(input.couponId()==null?workspace.couponPage(actor,1,20,input.keyword()):workspace.couponInfo(actor,input.couponId()));
    }
    @PostMapping("/inspect")
    public R<?> inspect(@RequestBody InspectInput input,HttpServletRequest request) {
        Long actor=actor(request);var scope=access.require(actor,null);authorization.require(actor,"redeem:use");
        // 券码仅交由权威预检，不转交模型，不记录会话。
        return R.ok(trade.inspect(actor,scope.storeId(),input.code()));
    }
    @PostMapping("/order")
    public R<?> order(@RequestBody OrderInput input,HttpServletRequest request) {
        Long actor=actor(request);access.require(actor,null);return R.ok(orders.info(actor,input.orderId()));
    }
    @PostMapping("/records")
    public R<?> records(HttpServletRequest request) {
        Long actor=actor(request);var scope=access.require(actor,null);
        return R.ok(trade.redemptions(actor,1,20,scope.storeId(),scope.operatorId()==null?"store":"self"));
    }
    @PostMapping("/statistics")
    public R<?> statistics(@RequestBody MerchantAiStatistics.Input input,HttpServletRequest request) {
        Object actor=request.getAttribute("merchantUserId");
        if(!(actor instanceof Long id))throw new CoolException("请先登录门店账号",401);
        return R.ok(statistics.query(id,input));
    }
}
