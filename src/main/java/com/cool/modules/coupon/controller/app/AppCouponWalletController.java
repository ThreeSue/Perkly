package com.cool.modules.coupon.controller.app;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.request.R;
import com.cool.modules.coupon.service.CouponTradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 仅向持券本人提供券包详情券码及无缓存PNG二维码
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:17:51
 */
@CoolRestController(value = "/app/coupon/wallet")
@RequiredArgsConstructor
public class AppCouponWalletController {
    private final CouponTradeService trade;

    public record PageInput(Integer page, Integer size, String state) {
    }

    @PostMapping("/page")
    public R<?> page(@RequestBody PageInput input) {
        return R.ok(trade.wallet(AppOrderController.customer(), input.page(), input.size(), input.state()));
    }

    @GetMapping("/info")
    public R<?> info(@RequestParam Long id) {
        return R.ok(trade.walletInfo(AppOrderController.customer(), id));
    }

    @GetMapping("/qrcode")
    public ResponseEntity<byte[]> qr(@RequestParam Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(trade.walletQr(AppOrderController.customer(), id));
    }
}
