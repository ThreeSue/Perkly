package com.cool.modules.merchant.controller;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.coupon.service.CouponTradeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 依据商家可信登录身份提供扫码预检单次核销及流水查询
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:17:51
 */
@RestController
@RequestMapping("/merchant/redemption")
@RequiredArgsConstructor
public class MerchantRedemptionController {
    private final CouponTradeService trade;
    private final HttpServletRequest request;
    public record RedeemInput(Long storeId, String code, Long consumptionAmount) {}
    public record PageInput(Integer page, Integer size, Long storeId) {}
    private Long operator() {
        Object id = request.getAttribute("merchantUserId");
        if (!(id instanceof Long)) throw new CoolException("请使用商家账号登录",401);
        return (Long)id;
    }
    @PostMapping("/preview") public R<?> preview(@RequestBody RedeemInput input) {
        return R.ok(trade.preview(operator(),input.storeId(),input.code(),input.consumptionAmount()));
    }
    @PostMapping("/confirm") public R<?> confirm(@RequestBody RedeemInput input) {
        return R.ok(trade.redeem(operator(),input.storeId(),input.code(),input.consumptionAmount()));
    }
    @PostMapping("/page") public R<?> page(@RequestBody PageInput input) {
        return R.ok(trade.redemptions(operator(),input.page(),input.size(),input.storeId()));
    }
}
