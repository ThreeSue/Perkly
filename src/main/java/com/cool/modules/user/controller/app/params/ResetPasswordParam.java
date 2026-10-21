package com.cool.modules.user.controller.app.params;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收未登录用户重设密码所需的手机号、短信验证码和新密码
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 19:31:22
 */
@Getter
@Setter
public class ResetPasswordParam {
    private String phone;
    private String smsCode;
    private String password;
}
