package com.cool.modules.merchant.service.impl;

import com.cool.core.exception.CoolException;

final class MerchantValidation {
    private MerchantValidation() {}
    static void phone(String value, String label) {
        // Accept business mobile, landline and service numbers; reject arbitrary text.
        if (value == null || !value.matches("[+0-9][0-9 ()\\-]{5,29}"))
            throw new CoolException(label + "格式不正确，请填写有效的手机号或固定电话");
        long digits = value.chars().filter(Character::isDigit).count();
        if (digits < 7 || digits > 20) throw new CoolException(label + "需包含7至20位数字");
    }
}
