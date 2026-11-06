package com.cool.modules.wallet.controller;

import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.wallet.service.PlatformWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营平台储值账务监管接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:47:39
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/wallet")
public class AdminWalletController {
    private final PlatformWalletService wallet;

    @GetMapping("/accounts")
    public R<?> accounts(@RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(wallet.accounts(pageNumber, pageSize));
    }

    @GetMapping("/transactions")
    public R<?> transactions(@RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(wallet.transactions(CoolSecurityUtil.getCurrentUserId(), "PLATFORM", pageNumber, pageSize));
    }
}
