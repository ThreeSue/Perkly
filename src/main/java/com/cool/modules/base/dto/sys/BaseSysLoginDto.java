package com.cool.modules.base.dto.sys;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录
 */
@Data
@Schema(description = "登录参数")
public class BaseSysLoginDto {
    @Schema(description = "网页登录空间MERCHANT或STORE；旧移动端可省略")
    private String accountType;

    @Schema(description = "用户名")
    @NotBlank
    private String username;

    @Schema(description = "密码")
    @NotBlank
    private String password;

    @Schema(description = "验证码ID")
    @NotBlank
    private String captchaId;

    @Schema(description = "验证码")
    @NotBlank
    private String verifyCode;
}
