package com.cool.modules.wallet.controller;

import com.cool.core.request.R;
import com.cool.core.exception.CoolException;
import com.cool.modules.wallet.service.PlatformWalletService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供商家门店开具无券消费账单及查阅授权门店储值流水接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:47:39
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/merchant/governance/wallet")
public class WorkspaceWalletController {
    private final PlatformWalletService wallet;
    private final HttpServletRequest request;

    public record Id(Long id) {
    }

    @GetMapping("/bills/stores")
    public R<?> stores() {
        return R.ok(wallet.storeOptions(actor()));
    }

    private Long actor() {
        if (request.getAttribute("merchantUserId") instanceof Long id) return id;
        throw new CoolException("请先登录", 401);
    }

    @PostMapping("/bills/create")
    public R<?> create(@RequestBody PlatformWalletService.BillInput p) {
        return R.ok(wallet.createBill(actor(), p));
    }

    @PostMapping("/bills/cancel")
    public R<?> cancel(@RequestBody Id p) {
        wallet.cancelBill(actor(), p.id());
        return R.ok();
    }
    @PostMapping("/bills/coupon")
    public R<?> coupon(@RequestBody PlatformWalletService.ApplyCoupon input) {
        return R.ok(wallet.applyCoupon(actor(),input));
    }
    @PostMapping("/bills/import")
    public R<?> importConsumption(@RequestBody PlatformWalletService.ImportConsumption input) {
        return R.ok(wallet.importConsumption(actor(),input.orderId()));
    }

    @GetMapping("/bills/page")
    public R<?> page(@RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(wallet.bills(actor(), pageNumber, pageSize));
    }

    @GetMapping("/transactions")
    public R<?> transactions(@RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(wallet.transactions(actor(), "WORKSPACE", pageNumber, pageSize));
    }
    @GetMapping("/bills/info")
    public R<?> billInfo(@RequestParam Long id) {
        return R.ok(wallet.staffBillInfo(actor(),id));
    }
}
