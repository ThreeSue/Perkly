package com.cool.core.util;

import com.cool.core.exception.CoolException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一校验新设置密码的复杂度，不干预历史密码登录
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 09:25:51
 */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password, String... identities) {
        if (password == null || password.length() < 8 || password.length() > 64)
            throw new CoolException("密码须为8至64位");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new CoolException("密码UTF-8编码不能超过72字节，请减少中文等多字节字符");
        if (password.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)))
            throw new CoolException("密码不能包含空白或控制字符");
        int kinds = (password.matches(".*[A-Z].*") ? 1 : 0) + (password.matches(".*[a-z].*") ? 1 : 0)
                + (password.matches(".*[0-9].*") ? 1 : 0) + (password.matches(".*[^\\p{L}\\p{N}].*") ? 1 : 0);
        if (kinds < 3) throw new CoolException("密码须包含大写字母、小写字母、数字、特殊符号中的至少三类");
        String lower = password.toLowerCase(Locale.ROOT);
        for (String identity : identities) {
            if (identity != null && !identity.isBlank() && lower.equals(identity.toLowerCase(Locale.ROOT)))
                throw new CoolException("密码不能与账号或手机号相同");
        }
        for (String weak : new String[]{"password", "qwerty", "letmein", "welcome", "changeme", "iloveyou", "admin"})
            if (lower.contains(weak)) throw new CoolException("请勿使用常见弱密码及其简单变形");
        for (int i = 0; i <= lower.length() - 4; i++) {
            String part = lower.substring(i, i + 4);
            if (part.chars().distinct().count() == 1) throw new CoolException("密码不能包含连续4位相同字符");
            for (String sequence : new String[]{"0123456789", "abcdefghijklmnopqrstuvwxyz", "qwertyuiop", "asdfghjkl", "zxcvbnm"})
                if (sequence.contains(part) || new StringBuilder(sequence).reverse().toString().contains(part))
                    throw new CoolException("密码不能包含连续4位顺序、倒序或键盘排列字符");
        }
    }
}
