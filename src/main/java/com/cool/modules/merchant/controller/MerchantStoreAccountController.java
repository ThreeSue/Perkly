package com.cool.modules.merchant.controller;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.merchant.service.MerchantStoreAccountService;
import com.cool.modules.merchant.service.MerchantStoreAccountService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供商家OWNER在自家门店范围管理店长员工的接口
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 11:55:52
 */
@RestController @RequestMapping("/merchant/workspace/storeAccount") @RequiredArgsConstructor
public class MerchantStoreAccountController {
    private final MerchantStoreAccountService accounts;
    private final HttpServletRequest request;
    private Long actor() {
        Object id = request.getAttribute("merchantUserId");
        if (!(id instanceof Long)) throw new CoolException("请使用商家管理员账号登录", 401);
        return (Long)id;
    }
    @PostMapping("/page") public R<?> page(@RequestBody PageInput input) { return R.ok(accounts.page(actor(), input)); }
    @GetMapping("/info") public R<?> info(@RequestParam Long storeId, @RequestParam Long id) { return R.ok(accounts.info(actor(), storeId, id)); }
    @GetMapping("/options") public R<?> options(@RequestParam Long storeId) { return R.ok(accounts.options(actor(), storeId)); }
    @PostMapping("/save") public R<?> save(@RequestBody SaveInput input) { return R.ok(accounts.save(actor(), input)); }
    @PostMapping("/status") public R<?> status(@RequestBody ActionInput input) { accounts.status(actor(), input); return R.ok(); }
    @PostMapping("/resetPassword") public R<?> reset(@RequestBody ActionInput input) { accounts.resetPassword(actor(), input); return R.ok(); }
}
