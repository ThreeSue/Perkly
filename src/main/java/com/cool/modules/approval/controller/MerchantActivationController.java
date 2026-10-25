package com.cool.modules.approval.controller;

import com.cool.core.annotation.TokenIgnore;
import com.cool.core.request.R;
import com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.Activate;
import com.cool.modules.approval.service.GuestMerchantOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 允许凭已验证联系人手机号及营业码首次创建商家管理员账号
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:05:24
 */
@RestController
@RequestMapping("/merchant/auth")
@RequiredArgsConstructor
public class MerchantActivationController {
    private final GuestMerchantOnboardingService onboarding;
    @TokenIgnore
    @PostMapping("/activate") public R<?> activate(@Valid @RequestBody Activate input) {return R.ok(onboarding.activate(input));}
}
