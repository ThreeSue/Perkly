package com.cool.modules.coupon.controller.app;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.coupon.service.CouponTradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 从当前顾客身份提供购券订单免费领取及模拟支付接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:17:51
 */
@CoolRestController(value = "/app/order")
@RequiredArgsConstructor
public class AppOrderController {
    private final CouponTradeService trade;

    public record CreateInput(Long couponTemplateId, Integer quantity, String requestId) {
    }

    public record PayInput(Long id, String result) {
    }

    public record IdInput(Long id) {
    }

    public record PageInput(Integer page, Integer size, String status) {
    }

    @PostMapping("/create")
    public R<?> create(@RequestBody CreateInput input) {
        return R.ok(trade.create(customer(), input.couponTemplateId(), input.quantity(), input.requestId()));
    }

    @PostMapping("/claim")
    public R<?> claim(@RequestBody CreateInput input) {
        return R.ok(trade.claim(customer(), input.couponTemplateId(), input.quantity(), input.requestId()));
    }

    @PostMapping("/simulatePay")
    public R<?> pay(@RequestBody PayInput input) {
        return R.ok(trade.simulatePay(customer(), input.id(), input.result()));
    }

    @PostMapping("/cancel")
    public R<?> cancel(@RequestBody IdInput input) {
        return R.ok(trade.cancel(customer(), input.id()));
    }

    @GetMapping("/info")
    public R<?> info(@RequestParam Long id) {
        return R.ok(trade.orderInfo(customer(), id));
    }

    @PostMapping("/page")
    public R<?> page(@RequestBody PageInput input) {
        return R.ok(trade.orders(customer(), input.page(), input.size(), input.status()));
    }

    public static Long customer() {
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.APP)
            throw new CoolException("请使用用户账号登录", 401);
        return CoolSecurityUtil.getCurrentUserId();
    }
}
