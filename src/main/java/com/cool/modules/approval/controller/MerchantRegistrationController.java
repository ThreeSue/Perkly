package com.cool.modules.approval.controller;

import com.cool.core.annotation.TokenIgnore;
import com.cool.core.cache.CoolCache;
import com.cool.core.request.R;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.approval.entity.req.MerchantRegistrationReq;
import com.cool.modules.approval.service.MerchantRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 通过图片验证码接收商家注册入驻及重提申请
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@RestController
@RequestMapping("/merchant/auth")
@RequiredArgsConstructor
public class MerchantRegistrationController {
    private final MerchantRegistrationService registration;
    private final BaseSysLoginService login;
    private final CoolCache cache;

    @TokenIgnore
    @PostMapping("/register")
    public R<?> register(@Valid @RequestBody MerchantRegistrationReq input) {
        check(input);
        return R.ok(registration.submit(input, false));
    }

    @TokenIgnore
    @PostMapping("/resubmit")
    public R<?> resubmit(@Valid @RequestBody MerchantRegistrationReq input) {
        check(input);
        return R.ok(registration.submit(input, true));
    }

    private void check(MerchantRegistrationReq input) {
        try {
            login.captchaCheck(input.getCaptchaId(), input.getVerifyCode());
        } finally {
            cache.del("verify:img:" + input.getCaptchaId());
        }
    }
}
