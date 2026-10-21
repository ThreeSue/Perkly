package com.cool.modules.approval.entity.req;

import jakarta.validation.constraints.*;
import lombok.Data;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收入驻申请资料和验证信息且不接收审批状态
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@Data
public class MerchantRegistrationReq {
    @NotBlank @Pattern(regexp="[A-Za-z0-9_]{4,40}") private String username;
    @NotBlank @Size(min=6,max=64) private String password;
    @NotBlank private String captchaId;
    @NotBlank private String verifyCode;
    @NotBlank @Size(max=100) private String merchantName;
    @NotNull @Positive private Long categoryId;
    @NotBlank @Size(max=50) private String contactName;
    @NotBlank @Pattern(regexp="[+0-9][0-9 ()\\-]{5,29}") private String contactPhone;
    @Size(max=500) private String address;
    @Size(max=500) private String licenseUrl;
    @Size(max=2000) private String introduction;
}
