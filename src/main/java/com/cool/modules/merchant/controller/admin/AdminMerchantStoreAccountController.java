package com.cool.modules.merchant.controller.admin;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.merchant.service.MerchantStoreAccountService;
import com.cool.modules.merchant.service.MerchantStoreAccountService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.core.enums.UserTypeEnum;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供平台运营管理门店账号接口并校验平台管理员权限
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 11:55:52
 */
@RestController @RequestMapping("/admin/merchant/storeAccount") @RequiredArgsConstructor
public class AdminMerchantStoreAccountController {
    private final MerchantStoreAccountService accounts;
    private Long actor() {
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.ADMIN || !CoolSecurityUtil.isSuperAdmin())
            throw new CoolException("仅平台超级管理员可管理门店账号", 403);
        return null;
    }
    @PostMapping("/page") public R<?> page(@RequestBody PageInput input) { return R.ok(accounts.page(actor(), input)); }
    @GetMapping("/info") public R<?> info(@RequestParam Long storeId, @RequestParam Long id) { return R.ok(accounts.info(actor(), storeId, id)); }
    @GetMapping("/options") public R<?> options(@RequestParam Long storeId) { return R.ok(accounts.options(actor(), storeId)); }
    @PostMapping("/save") public R<?> save(@RequestBody SaveInput input) { return R.ok(accounts.save(actor(), input)); }
    @PostMapping("/status") public R<?> status(@RequestBody ActionInput input) { accounts.status(actor(), input); return R.ok(); }
    @PostMapping("/resetPassword") public R<?> reset(@RequestBody ActionInput input) { accounts.resetPassword(actor(), input); return R.ok(); }
}
