package com.cool.modules.user;

import com.cool.core.exception.CoolException;
import com.cool.core.util.PasswordPolicy;
import com.cool.modules.user.util.UserPasswordUtil;
import cn.hutool.crypto.digest.MD5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证密码设置规则与历史弱密码登录兼容
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 09:27:00
 */
class PasswordPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"123456", "abcdefgh", "Abc1234!", "Abc4321!", "Abcd28!x", "DcbA28!x",
            "Aaaa28!x", "Qwer28!x", "Rewq28!x", "Password123!", "Qwerty123!", "Ab 28!xy", "abcdefghij88"})
    void rejectsWeakPasswords(String password) {
        assertThrows(CoolException.class, () -> PasswordPolicy.validate(password));
    }

    @Test void acceptsStrongPasswordsAndChecksIdentityAndLength() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("River!72"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("River72x"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("Ri!7".repeat(16)));
        assertThrows(CoolException.class, () -> PasswordPolicy.validate("Ri!7".repeat(16) + "x"));
        assertThrows(CoolException.class, () -> PasswordPolicy.validate("River72x", "river72x"));
        assertThrows(CoolException.class, () -> PasswordPolicy.validate("River!72" + "中文".repeat(12)));
    }

    @Test void legacyWeakPasswordsStillMatchButCannotBeSetAgain() {
        assertTrue(UserPasswordUtil.matches("123456", MD5.create().digestHex("123456")));
        assertTrue(UserPasswordUtil.matches("123456", new BCryptPasswordEncoder().encode("123456")));
        assertThrows(CoolException.class, () -> UserPasswordUtil.encode("123456"));
    }
}
