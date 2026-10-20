package com.cool.modules.user.controller.app.params;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WechatBindPhoneParam {
    private String bindingToken;
    private String phone;
    private String smsCode;
}
