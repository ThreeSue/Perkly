package com.cool.modules.merchant.controller.admin;

import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.merchant.controller.admin.params.BackendAccountParam.*;
import com.cool.modules.merchant.service.BackendAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 限制平台超级管理员统一查询和维护商家后台账号
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 22:10:23
 */
@RestController
@RequestMapping("/admin/merchant/backendAccount")
@RequiredArgsConstructor
public class AdminMerchantBackendAccountController {
    private final BackendAccountService service;
    private void authorize() {
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.ADMIN || !CoolSecurityUtil.isSuperAdmin())
            throw new CoolException("仅平台超级管理员可管理商家后台账号", 403);
    }
    @GetMapping("/groups") public R<?> groups() { authorize(); return R.ok(service.groups()); }
    @PostMapping("/page") public R<?> page(@RequestBody PageInput input) { authorize(); return R.ok(service.page(input)); }
    @PostMapping("/saveOwner") public R<?> save(@RequestBody OwnerInput input) { authorize(); service.saveOwner(input); return R.ok(); }
    @PostMapping("/resetOwner") public R<?> reset(@RequestBody OwnerInput input) { authorize(); service.resetOwner(input.id()); return R.ok(); }
}
