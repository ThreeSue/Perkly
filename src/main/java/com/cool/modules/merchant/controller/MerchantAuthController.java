package com.cool.modules.merchant.controller;

import com.cool.core.annotation.TokenIgnore;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.request.R;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.merchant.service.MerchantAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供独立商家登录验证码刷新和退出接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 20:52:24
 */
@RestController
@RequestMapping("/merchant/auth")
@RequiredArgsConstructor
public class MerchantAuthController {
    private final MerchantAccountService accounts;
    private final BaseSysLoginService login;

    @TokenIgnore
    @GetMapping("/captcha")
    public R<?> captcha() { return R.ok(login.captcha(UserTypeEnum.ADMIN, "base64", 150, 45)); }

    @TokenIgnore
    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody BaseSysLoginDto input) { return R.ok(accounts.login(input)); }

    @TokenIgnore
    @GetMapping("/registrationCategories")
    public R<?> registrationCategories() { return R.ok(accounts.registrationCategories()); }

    public record RefreshInput(String refreshToken, String accountType) {}

    @TokenIgnore
    @PostMapping("/refreshToken")
    public R<?> refresh(@RequestBody RefreshInput input) { if(input.accountType()!=null){var user=accounts.validate(input.refreshToken(),true);if(!input.accountType().equals(com.cool.modules.workspace.service.WorkspaceAuthorization.type(user)))throw new com.cool.core.exception.CoolException("账号与所选身份不一致",403);}
        return R.ok(accounts.refresh(input.refreshToken())); }

    @GetMapping("/onboarding")
    public R<?> onboarding(@RequestHeader("Authorization") String token) {
        return R.ok(accounts.applicantInfo(accounts.validateApplicant(token, false)));
    }

    public record PasswordInput(String oldPassword, String newPassword) {}

    @PostMapping("/changePassword")
    public R<?> changePassword(@RequestBody PasswordInput input,
                               jakarta.servlet.http.HttpServletRequest request) {
        Object id = request.getAttribute("merchantUserId");
        if (!(id instanceof Long)) throw new com.cool.core.exception.CoolException("请使用商家账号登录", 401);
        accounts.changePassword((Long) id, input.oldPassword(), input.newPassword());
        return R.ok();
    }

    @PostMapping("/logout")
    public R<?> logout(@RequestHeader("Authorization") String token) { accounts.logout(token); return R.ok(); }
}
