package com.cool.modules.merchant.controller.admin;

import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.merchant.service.MerchantAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 允许平台超级管理员创建绑定商家的独立管理账号
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 20:52:24
 */
@RestController
@RequestMapping("/admin/merchant/account")
@RequiredArgsConstructor
public class AdminMerchantAccountController {
    private final MerchantAccountService accounts;
    public record AccountInput(String username, String name, String password, Long merchantId) {}

    @PostMapping("/provision")
    public R<?> provision(@RequestBody AccountInput input) {
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.ADMIN || !CoolSecurityUtil.isSuperAdmin()) {
            throw new CoolException("仅平台超级管理员可开通商家账号", 403);
        }
        return R.ok(accounts.provision(input.username(), input.name(), input.password(), input.merchantId()));
    }
    public record ResetInput(Long merchantId) {}

    @PostMapping("/resetPassword")
    public R<?> resetPassword(@RequestBody ResetInput input) {
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.ADMIN || !CoolSecurityUtil.isSuperAdmin()) {
            throw new CoolException("仅平台超级管理员可重置商家密码", 403);
        }
        return R.ok(accounts.resetMerchantPassword(input.merchantId()));
    }

}
