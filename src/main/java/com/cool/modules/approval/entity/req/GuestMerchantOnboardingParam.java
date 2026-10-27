package com.cool.modules.approval.entity.req;

import jakarta.validation.constraints.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义无需普通用户登录的手机号验证入驻申请及商家激活参数
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:03:08
 */
public final class GuestMerchantOnboardingParam {
    private GuestMerchantOnboardingParam() {}
    public record Sms(@NotBlank String phone, @NotBlank String captchaId, @NotBlank String verifyCode) {}
    public record Verify(@NotBlank String phone, @NotBlank String smsCode) {}
    public record Session(@NotBlank String onboardingToken) {}
    public record Detail(@NotBlank String onboardingToken, @NotBlank String applicationNo) {}
    // 兼容旧前端多传的地址，但不将其绑定、校验或保存为商家/门店资料。
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("address")
    public record Submit(@NotBlank String onboardingToken,
                         @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestId,
                         String applicationNo,
                         @NotBlank @Size(max=100) String merchantName,
                         @NotNull @Positive Long categoryId,
                         @NotBlank @Size(max=50) String contactName,
                         @Size(max=2000) String introduction) {}
    public record Activate(@NotBlank String onboardingToken, @NotBlank String code,
                           @NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,40}") String username,
                           @NotBlank @Size(min=8,max=64) String password) {}
}
