package com.cool.modules.user.util;

import cn.hutool.crypto.digest.MD5;
import com.cool.core.exception.CoolException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 用户密码编码；兼容已有 MD5 数据，新密码使用 BCrypt。 */
public final class UserPasswordUtil {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private UserPasswordUtil() {}

    public static String encode(String password) {
        if (password == null || password.length() < 8 || password.length() > 32
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().anyMatch(Character::isWhitespace)) {
            throw new CoolException("密码须为8至32位且不能包含空白字符");
        }
        return ENCODER.encode(password);
    }

    public static boolean matches(String password, String encoded) {
        if (password == null || encoded == null || encoded.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        if (encoded.matches("(?i)[0-9a-f]{32}")) {
            return encoded.equalsIgnoreCase(MD5.create().digestHex(password));
        }
        try { return ENCODER.matches(password, encoded); }
        catch (IllegalArgumentException e) { return false; }
    }
}
