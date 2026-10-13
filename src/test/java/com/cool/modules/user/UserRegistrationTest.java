package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.proxy.WxProxy;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.service.UserWxService;
import com.cool.modules.user.service.impl.UserLoginServiceImpl;
import com.cool.modules.user.util.UserPasswordUtil;
import com.cool.modules.user.util.UserSmsUtil;
import com.cool.modules.user.util.UserSmsUtil.SendSceneEnum;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class UserRegistrationTest {
    private final UserInfoService users = mock(UserInfoService.class);
    private final UserSmsUtil sms = mock(UserSmsUtil.class);
    private final JwtTokenUtil tokens = mock(JwtTokenUtil.class);
    private final UserLoginServiceImpl login = new UserLoginServiceImpl(mock(CoolCache.class), tokens,
        users, sms, mock(BaseSysLoginService.class), mock(UserWxService.class), mock(WxProxy.class));
    private static final String PHONE = "13800000000";

    @Test void verifiedRegistrationSavesEncodedPasswordAndIssuesUserToken() {
        when(users.save(any(UserInfoEntity.class))).thenAnswer(invocation -> {
            ((UserInfoEntity) invocation.getArgument(0)).setId(42L); return true;
        });
        when(tokens.generateToken(any(Dict.class))).thenReturn("test-token");
        when(tokens.generateRefreshToken(any(Dict.class))).thenReturn("test-refresh-token");
        Dict result = (Dict) login.register(PHONE, "123456", "TestPass123");
        verify(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
        var user = ArgumentCaptor.forClass(UserInfoEntity.class);
        verify(users).save(user.capture());
        assertTrue(UserPasswordUtil.matches("TestPass123", user.getValue().getPassword()));
        assertEquals(1, user.getValue().getStatus());
        assertEquals("test-token", result.getStr("token"));
        var payload = ArgumentCaptor.forClass(Dict.class);
        verify(tokens).generateToken(payload.capture());
        assertEquals("APP", payload.getValue().getStr("userType"));
        assertEquals(42L, payload.getValue().getLong("userId"));
    }

    @Test void existingAccountAndInvalidPasswordDoNotConsumeCodeOrOverwriteUser() {
        when(users.getOne(any(QueryWrapper.class))).thenReturn(new UserInfoEntity());
        assertThrows(CoolException.class, () -> login.register(PHONE, "123456", "TestPass123"));
        assertThrows(CoolException.class, () -> login.register(PHONE, "123456", "short"));
        verifyNoInteractions(sms, tokens);
        verify(users, never()).save(any(UserInfoEntity.class));
    }

    @Test void incorrectCodeNeverCreatesUserOrToken() {
        doThrow(new CoolException("验证码错误")).when(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
        assertThrows(CoolException.class, () -> login.register(PHONE, "123456", "TestPass123"));
        verify(users, never()).save(any(UserInfoEntity.class));
        verifyNoInteractions(tokens);
    }

    @Test void concurrentDuplicateDoesNotIssueToken() {
        when(users.save(any(UserInfoEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var error = assertThrows(CoolException.class, () -> login.register(PHONE, "123456", "TestPass123"));
        assertEquals("该手机号已注册，请直接登录", error.getMessage());
        verifyNoInteractions(tokens);
    }
}
