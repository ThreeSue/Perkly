package com.cool.modules.approval.controller;

import com.cool.core.annotation.TokenIgnore;
import com.cool.core.request.R;
import com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.*;
import com.cool.modules.approval.service.GuestMerchantOnboardingService;
import com.cool.modules.user.service.UserLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 允许游客验证手机号提交入驻及查询本人申请而不创建普通用户
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:05:24
 */
@RestController
@TokenIgnore
@RequestMapping("/app/merchant/onboarding")
@RequiredArgsConstructor
public class GuestMerchantOnboardingController {
    private final GuestMerchantOnboardingService onboarding;
    private final UserLoginService login;

    @PostMapping("/smsCode")
    public R<?> sms(@Valid @RequestBody Sms input) {
        login.smsCode(input.phone(), input.captchaId(), input.verifyCode());
        return R.ok();
    }

    @PostMapping("/verify")
    public R<?> verify(@Valid @RequestBody Verify input) {
        return R.ok(java.util.Map.of("onboardingToken", onboarding.verify(input.phone(), input.smsCode()), "expiresIn", 1800));
    }

    @PostMapping("/submit")
    public R<?> submit(@Valid @RequestBody Submit input) {
        return R.ok(onboarding.submit(input, false));
    }

    @PostMapping("/resubmit")
    public R<?> resubmit(@Valid @RequestBody Submit input) {
        return R.ok(onboarding.submit(input, true));
    }

    @PostMapping("/list")
    public R<?> list(@Valid @RequestBody Session input) {
        return R.ok(onboarding.list(input.onboardingToken()));
    }

    @PostMapping("/info")
    public R<?> info(@Valid @RequestBody Detail input) {
        return R.ok(onboarding.info(input.onboardingToken(), input.applicationNo()));
    }

    @PostMapping("/renewCode")
    public R<?> renew(@Valid @RequestBody Detail input) {
        return R.ok(onboarding.renew(input.onboardingToken(), input.applicationNo()));
    }
}
