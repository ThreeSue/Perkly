package com.cool.modules.coupon.controller.app;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.annotation.TokenIgnore;
import com.cool.core.request.PageResult;
import com.cool.core.request.R;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.service.AppDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供平台已审核上架优惠券的公开列表与详情接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:04:27
 */
@RequiredArgsConstructor
@Tag(name = "用户端优惠券展示")
@CoolRestController(api = {"page", "info"})
public class AppCouponController {
    private final AppDiscoveryService discovery;

    @TokenIgnore
    @Operation(summary = "平台公开优惠券分页查询")
    @PostMapping("/page")
    public R<PageResult<Map<String, Object>>> page(@RequestBody(required = false) DiscoveryPageReq req) {
        return R.ok(discovery.coupons(req == null ? new DiscoveryPageReq() : req));
    }

    @TokenIgnore
    @Operation(summary = "平台公开优惠券详情及适用门店")
    @GetMapping("/info")
    public R<Map<String, Object>> info(@RequestParam Long id) {
        return R.ok(discovery.coupon(id));
    }
}
