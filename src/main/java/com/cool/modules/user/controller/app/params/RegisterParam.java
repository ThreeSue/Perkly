package com.cool.modules.user.controller.app.params;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterParam {

    private String phone;

    private String smsCode;

    private String password;
}
