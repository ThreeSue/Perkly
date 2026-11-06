package com.cool.modules.wallet.controller;

import com.cool.core.request.R;
import com.cool.modules.coupon.controller.app.AppOrderController;
import com.cool.modules.wallet.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供顾客储值余额模拟充值支付预览与账务查询接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:47:38
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/wallet")
public class AppWalletController {
    private final PlatformWalletService wallet;
    private final WalletActivityService activities;

    @GetMapping("/balance")
    public R<?> balance() {
        return R.ok(wallet.balance(AppOrderController.customer()));
    }

    @PostMapping("/recharge")
    public R<?> recharge(@RequestBody PlatformWalletService.Recharge p) {
        return R.ok(wallet.recharge(AppOrderController.customer(), p));
    }

    @GetMapping("/bill")
    public R<?> bill(@RequestParam String billNo) {
        return R.ok(wallet.bill(AppOrderController.customer(), billNo));
    }

    @PostMapping("/quote")
    public R<?> quote(@RequestBody PlatformWalletService.Pay p) {
        return R.ok(wallet.quote(AppOrderController.customer(), p));
    }

    @PostMapping("/pay")
    public R<?> pay(@RequestBody PlatformWalletService.Pay p) {
        return R.ok(wallet.pay(AppOrderController.customer(), p));
    }
    @PostMapping("/consumption/quote")
    public R<PlatformWalletService.ConsumptionQuote> consumptionQuote(@RequestBody PlatformWalletService.ConsumptionPay input) {
        return R.ok(wallet.consumptionQuote(AppOrderController.customer(),input));
    }
    @PostMapping("/consumption/pay")
    public R<?> consumptionPay(@RequestBody PlatformWalletService.ConsumptionPay input) {
        return R.ok(wallet.consumptionPay(AppOrderController.customer(),input));
    }
    @GetMapping("/consumption/page")
    public R<?> consumptionPage(@RequestParam(defaultValue="1") int pageNumber,
                               @RequestParam(defaultValue="20") int pageSize) {
        return R.ok(wallet.customerBills(AppOrderController.customer(),pageNumber,pageSize));
    }

    @GetMapping("/transactions")
    public R<?> transactions(@RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(wallet.transactions(AppOrderController.customer(), "APP", pageNumber, pageSize));
    }

    @GetMapping("/activities")
    public R<?> activities(@RequestParam(required = false) Long storeId) {
        AppOrderController.customer();
        return R.ok(activities.publicList(storeId));
    }
}
