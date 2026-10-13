package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.service.AliyunSmsAuthService;
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
import cn.hutool.crypto.digest.MD5;
import org.junit.jupiter.api.Test;

class UserLoginFlowTest {
    private static final String PHONE = "13800000000";
    private static final String KEY = "ALL_sms:" + PHONE;

    @Test void passwordEncodingSupportsNewAndExistingAccounts() {
        String encoded = UserPasswordUtil.encode("TestPass123");
        assertNotEquals("TestPass123", encoded);
        assertTrue(UserPasswordUtil.matches("TestPass123", encoded));
        assertFalse(UserPasswordUtil.matches("WrongPass123", encoded));
        assertTrue(UserPasswordUtil.matches("legacy", MD5.create().digestHex("legacy")));
        assertFalse(UserPasswordUtil.matches("password", null));
        assertThrows(CoolException.class, () -> UserPasswordUtil.encode("short"));
    }

    private UserLoginServiceImpl service(UserInfoEntity user) {
        UserInfoService users = mock(UserInfoService.class);
        when(users.getOne(any(QueryWrapper.class))).thenReturn(user);
        return new UserLoginServiceImpl(mock(CoolCache.class), mock(JwtTokenUtil.class), users,
            mock(UserSmsUtil.class), mock(BaseSysLoginService.class), mock(UserWxService.class), mock(WxProxy.class));
    }

    @Test void passwordLoginRejectsMissingWrongUnsetAndDisabledAccounts() {
        assertThrows(CoolException.class, () -> service(null).password(PHONE, "TestPass123"));
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1L); user.setPhone(PHONE); user.setStatus(1);
        assertThrows(CoolException.class, () -> service(user).password(PHONE, "TestPass123"));
        user.setPassword(UserPasswordUtil.encode("TestPass123"));
        assertThrows(CoolException.class, () -> service(user).password(PHONE, "WrongPass123"));
        user.setStatus(0);
        assertThrows(CoolException.class, () -> service(user).password(PHONE, "TestPass123"));
    }

    @Test void failedSmsRequestDoesNotCreateValidChallenge() {
        CoolCache cache = mock(CoolCache.class);
        AliyunSmsAuthService gateway = mock(AliyunSmsAuthService.class);
        doThrow(new CoolException("发送失败")).when(gateway).send(PHONE);
        UserSmsUtil sms = new UserSmsUtil(gateway, cache);
        assertThrows(CoolException.class, () -> sms.sendVerifyCode(PHONE, SendSceneEnum.ALL));
        verify(cache, never()).set(eq(KEY + ":expires"), any(), anyLong());
    }

    @Test void cooldownBlocksSendingBeforePluginInvocation() {
        CoolCache cache = mock(CoolCache.class);
        AliyunSmsAuthService plugins = mock(AliyunSmsAuthService.class);
        when(cache.get(KEY + ":nextSend", Long.class)).thenReturn(System.currentTimeMillis() + 60_000);
        assertThrows(CoolException.class, () -> new UserSmsUtil(plugins, cache).sendVerifyCode(PHONE, SendSceneEnum.ALL));
        verifyNoInteractions(plugins);
    }

    @Test void expiredCodeCannotBeUsedEvenWhenCacheStillContainsIt() {
        CoolCache cache = mock(CoolCache.class);
        when(cache.get(KEY + ":expires", Long.class)).thenReturn(System.currentTimeMillis() - 1);
        assertThrows(CoolException.class, () -> new UserSmsUtil(mock(AliyunSmsAuthService.class), cache)
            .checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL));
        verify(cache).del(KEY, KEY + ":expires", KEY + ":attempts");
    }

    @Test void validCodeIsConsumedAndFifthWrongAttemptInvalidatesCode() {
        CoolCache cache = mock(CoolCache.class);
        when(cache.get(KEY + ":expires", Long.class)).thenReturn(System.currentTimeMillis() + 300_000);
        AliyunSmsAuthService gateway = mock(AliyunSmsAuthService.class);
        when(gateway.check(PHONE, "123456")).thenReturn(true);
        UserSmsUtil sms = new UserSmsUtil(gateway, cache);
        sms.checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
        verify(cache).del(KEY, KEY + ":expires", KEY + ":attempts");
        clearInvocations(cache);
        when(cache.get(KEY + ":attempts", Integer.class)).thenReturn(4);
        assertThrows(CoolException.class, () -> sms.checkVerifyCode(PHONE, "654321", SendSceneEnum.ALL));
        verify(cache).del(KEY, KEY + ":expires", KEY + ":attempts");
    }
}
