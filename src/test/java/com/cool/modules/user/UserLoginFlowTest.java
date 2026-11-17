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
import com.cool.modules.user.sms.SmsScene;
import com.cool.modules.user.sms.SmsUsageService;
import com.cool.modules.user.sms.SmsUsageService.Outcome;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.crypto.digest.MD5;
import org.junit.jupiter.api.Test;

class UserLoginFlowTest {
    @Test void authenticatedWechatBindingPreservesAccountAndRejectsAnotherOwner() {
        UserInfoService users = mock(UserInfoService.class);
        UserWxService wechat = mock(UserWxService.class);
        UserSmsUtil sms = mock(UserSmsUtil.class);
        UserInfoEntity current = new UserInfoEntity();
        current.setId(42L); current.setPhone(PHONE); current.setStatus(1);
        current.setPassword("unchanged-hash");
        when(users.getById(42L)).thenReturn(current);
        var wx = new com.cool.modules.user.entity.UserWxEntity();
        wx.setId(9L); wx.setOpenid("verified-wechat");
        when(wechat.getMiniUserInfo("wx-code", null, null)).thenReturn(wx);
        when(wechat.getOne(any(QueryWrapper.class))).thenReturn(wx);
        when(wechat.updateById(any(com.cool.modules.user.entity.UserWxEntity.class))).thenReturn(true);
        var service = new UserLoginServiceImpl(mock(CoolCache.class), mock(JwtTokenUtil.class), users,
            sms, mock(BaseSysLoginService.class), wechat, mock(WxProxy.class));
        var tx = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "transactionManager", tx);
        service.bindCurrentWechat(42L, "wx-code", "123456");
        verify(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL, SmsScene.WECHAT_BIND);
        verify(wechat).updateById(argThat((com.cool.modules.user.entity.UserWxEntity link) ->
            Long.valueOf(42).equals(link.getUserId()) && Long.valueOf(9).equals(link.getId())));
        assertEquals("unchanged-hash", current.getPassword());
        verify(users, never()).save(any(UserInfoEntity.class));

        clearInvocations(wechat, sms);
        UserInfoEntity other = new UserInfoEntity(); other.setId(88L); other.setStatus(1);
        wx.setUserId(88L); when(users.getById(88L)).thenReturn(other);
        assertThrows(CoolException.class, () -> service.bindCurrentWechat(42L, "wx-code", "123456"));
        verify(wechat, never()).updateById(any(com.cool.modules.user.entity.UserWxEntity.class));
        verifyNoInteractions(sms);
    }

    @Test void authenticatedWechatBindingDoesNotWriteWhenSmsVerificationFails() {
        UserInfoService users = mock(UserInfoService.class);
        UserWxService wechat = mock(UserWxService.class);
        UserSmsUtil sms = mock(UserSmsUtil.class);
        UserInfoEntity current = new UserInfoEntity(); current.setId(42L); current.setPhone(PHONE); current.setStatus(1);
        when(users.getById(42L)).thenReturn(current);
        var wx = new com.cool.modules.user.entity.UserWxEntity(); wx.setId(9L); wx.setOpenid("verified-wechat");
        when(wechat.getMiniUserInfo("wx-code", null, null)).thenReturn(wx);
        when(wechat.getOne(any(QueryWrapper.class))).thenReturn(wx);
        when(sms.checkVerifyCode(PHONE, "wrong", SendSceneEnum.ALL, SmsScene.WECHAT_BIND)).thenThrow(new CoolException("验证码错误"));
        var service = new UserLoginServiceImpl(mock(CoolCache.class), mock(JwtTokenUtil.class), users,
            sms, mock(BaseSysLoginService.class), wechat, mock(WxProxy.class));
        var tx = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "transactionManager", tx);
        assertThrows(CoolException.class, () -> service.bindCurrentWechat(42L, "wx-code", "wrong"));
        verify(wechat, never()).updateById(any(com.cool.modules.user.entity.UserWxEntity.class));
    }
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
        when(gateway.send(eq(PHONE), anyString())).thenThrow(new CoolException("发送失败"));
        SmsUsageService usage = mock(SmsUsageService.class);
        when(usage.reserve(eq(PHONE), eq(SmsScene.UNCLASSIFIED), anyLong(), isNull()))
            .thenReturn(new SmsUsageService.Admission(1L, Outcome.RESERVED, 0));
        UserSmsUtil sms = new UserSmsUtil(gateway, cache, usage);
        assertThrows(CoolException.class, () -> sms.sendVerifyCode(PHONE, SendSceneEnum.ALL, SmsScene.UNCLASSIFIED));
        verify(cache, never()).set(eq(KEY + ":expires"), any(), anyLong());
    }

    @Test void cooldownBlocksSendingBeforePluginInvocation() {
        CoolCache cache = mock(CoolCache.class);
        AliyunSmsAuthService plugins = mock(AliyunSmsAuthService.class);
        when(cache.get(KEY + ":nextSend", Long.class)).thenReturn(System.currentTimeMillis() + 60_000);
        SmsUsageService usage = mock(SmsUsageService.class);
        when(usage.reserve(eq(PHONE), eq(SmsScene.UNCLASSIFIED), anyLong(), anyLong()))
            .thenReturn(new SmsUsageService.Admission(2L, Outcome.COOLDOWN, 60));
        var error = assertThrows(CoolException.class, () -> new UserSmsUtil(plugins, cache, usage)
            .sendVerifyCode(PHONE, SendSceneEnum.ALL, SmsScene.UNCLASSIFIED));
        assertEquals(429, error.getCode());
        verify(plugins, never()).send(anyString(), anyString());
    }

    @Test void expiredCodeCannotBeUsedEvenWhenCacheStillContainsIt() {
        CoolCache cache = mock(CoolCache.class);
        when(cache.get(KEY + ":expires", Long.class)).thenReturn(System.currentTimeMillis() - 1);
        assertThrows(CoolException.class, () -> new UserSmsUtil(mock(AliyunSmsAuthService.class), cache, mock(SmsUsageService.class))
            .checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL, SmsScene.LOGIN));
        verify(cache).del(KEY, KEY + ":expires", KEY + ":attempts", KEY + ":usageId");
    }

    @Test void validCodeIsConsumedAndFifthWrongAttemptInvalidatesCode() {
        CoolCache cache = mock(CoolCache.class);
        when(cache.get(KEY + ":expires", Long.class)).thenReturn(System.currentTimeMillis() + 300_000);
        AliyunSmsAuthService gateway = mock(AliyunSmsAuthService.class);
        when(gateway.check(PHONE, "123456")).thenReturn(true);
        UserSmsUtil sms = new UserSmsUtil(gateway, cache, mock(SmsUsageService.class));
        sms.checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL, SmsScene.LOGIN);
        verify(cache).del(KEY, KEY + ":expires", KEY + ":attempts", KEY + ":usageId");
        clearInvocations(cache);
        when(cache.get(KEY + ":attempts", Integer.class)).thenReturn(4);
        assertThrows(CoolException.class, () -> sms.checkVerifyCode(PHONE, "654321", SendSceneEnum.ALL, SmsScene.LOGIN));
        verify(cache).del(KEY, KEY + ":expires", KEY + ":attempts", KEY + ":usageId");
    }
}
