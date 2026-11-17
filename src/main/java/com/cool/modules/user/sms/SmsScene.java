package com.cool.modules.user.sms;

import com.cool.core.exception.CoolException;
import java.util.Set;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义发送统计可用的验证码业务用途
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
public enum SmsScene {
    UNCLASSIFIED, REGISTER, LOGIN, RESET_PASSWORD, WECHAT_BIND, BIND_PHONE, CHANGE_PASSWORD, MERCHANT_ONBOARDING;

    private static final Set<SmsScene> APP = Set.of(UNCLASSIFIED, REGISTER, LOGIN, RESET_PASSWORD,
        WECHAT_BIND, BIND_PHONE, CHANGE_PASSWORD);

    public static SmsScene fromApp(String input) {
        if (input == null || input.isBlank()) return UNCLASSIFIED;
        try {
            SmsScene scene = valueOf(input);
            if (APP.contains(scene)) return scene;
        } catch (IllegalArgumentException ignored) {}
        throw new CoolException("不支持的短信验证码用途",400);
    }
}
