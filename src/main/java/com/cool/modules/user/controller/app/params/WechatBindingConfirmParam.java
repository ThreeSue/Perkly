package com.cool.modules.user.controller.app.params;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WechatBindingConfirmParam {
    private String confirmationToken;
    private String profileSource;
}
