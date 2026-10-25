package com.cool.modules.merchant.controller;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.coupon.service.ConsumptionOrderService;
import com.cool.modules.coupon.service.ConsumptionOrderService.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供扫码后按需开单结算自动兑换及门店消费账单查询接口
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 10:35:36
 */
@RestController @RequestMapping("/merchant/consumption") @RequiredArgsConstructor
public class MerchantConsumptionController {
    private final ConsumptionOrderService orders;
    private final HttpServletRequest request;
    private Long user() { if(request.getAttribute("merchantUserId") instanceof Long id) return id; throw new CoolException("请使用商家账号登录",401); }
    public record IdInput(Long orderId) {}
    @PostMapping("/prepare") public R<?> prepare(@RequestBody PrepareInput p) {return R.ok(orders.prepare(user(),p));}
    @PostMapping("/revise") public R<?> revise(@RequestBody ReviseInput p) {return R.ok(orders.revise(user(),p));}
    @PostMapping("/confirm") public R<?> confirm(@RequestBody ConfirmInput p) {return R.ok(orders.confirm(user(),p));}
    @PostMapping("/redeemContent") public R<?> content(@RequestBody PrepareInput p) {return R.ok(orders.redeemContent(user(),p));}
    @PostMapping("/cancel") public R<?> cancel(@RequestBody IdInput p) {return R.ok(orders.cancel(user(),p.orderId()));}
    @PostMapping("/page") public R<?> page(@RequestBody PageInput p) {return R.ok(orders.page(user(),p));}
    @GetMapping("/info") public R<?> info(@RequestParam Long id) {return R.ok(orders.info(user(),id));}
}
