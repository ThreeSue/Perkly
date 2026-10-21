package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.core.lang.Dict;
import cn.hutool.jwt.JWT;
import com.cool.core.annotation.TokenIgnore;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.security.IgnoredUrlsProperties;
import com.cool.core.security.JwtAuthenticationTokenFilter;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.user.controller.app.AppUserLoginController;
import com.cool.modules.user.controller.app.params.ResetPasswordParam;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.proxy.WxProxy;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.service.UserWxService;
import com.cool.modules.user.service.impl.UserLoginServiceImpl;
import com.cool.modules.user.util.UserPasswordUtil;
import com.cool.modules.user.util.UserSmsUtil;
import com.cool.modules.user.util.UserSmsUtil.SendSceneEnum;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证短信重设密码、账号保护和旧访问及刷新凭证失效
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 19:55:23
 */
class UserPasswordResetTest {
    private static final String PHONE = "13800000000";
    private final CoolCache cache = mock(CoolCache.class);
    private final JwtTokenUtil tokens = mock(JwtTokenUtil.class);
    private final UserInfoService users = mock(UserInfoService.class);
    private final UserSmsUtil sms = mock(UserSmsUtil.class);
    private final UserLoginServiceImpl login = new UserLoginServiceImpl(cache, tokens, users, sms,
            mock(BaseSysLoginService.class), mock(UserWxService.class), mock(WxProxy.class));

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private UserInfoEntity account() {
        var user = new UserInfoEntity(); user.setId(42L); user.setPhone(PHONE); user.setStatus(1);
        user.setPassword(UserPasswordUtil.encode("OldPass123"));
        when(users.getOne(any(QueryWrapper.class))).thenReturn(user);
        when(users.getById(42L)).thenReturn(user);
        return user;
    }

    @Test void smsVerifiedResetChangesOnlyPasswordAndDoesNotLogin() {
        UserInfoEntity user = account();
        when(users.update(any(UserInfoEntity.class), any(QueryWrapper.class))).thenReturn(true);
        login.resetPassword(PHONE, "123456", "NewPass456");
        verify(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
        var changes = ArgumentCaptor.forClass(UserInfoEntity.class);
        var condition = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(users).update(changes.capture(), condition.capture());
        assertTrue(UserPasswordUtil.matches("NewPass456", changes.getValue().getPassword()));
        assertFalse(UserPasswordUtil.matches("OldPass123", changes.getValue().getPassword()));
        assertNull(changes.getValue().getPhone()); assertNull(changes.getValue().getStatus());
        assertNull(changes.getValue().getNickName());
        String sql = condition.getValue().toSQL();
        assertTrue(sql.contains("phone") && sql.contains("status") && sql.contains("id"));
        verify(cache).del("app:userDetails:" + user.getId());
        verifyNoInteractions(tokens);
        verify(users, never()).save(any(UserInfoEntity.class));
    }

    @Test void invalidPhoneAndPasswordNeverConsumeSms() {
        assertThrows(CoolException.class, () -> login.resetPassword("123", "123456", "NewPass456"));
        assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "short"));
        assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "Contains Space"));
        assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "中".repeat(25)));
        verifyNoInteractions(sms, users, cache, tokens);
    }

    @Test void wrongExpiredOrReplayedCodeCannotReadOrUpdateAccount() {
        for (String message : List.of("验证码错误", "验证码已过期，请重新获取", "验证码错误次数过多，请重新获取")) {
            doThrow(new CoolException(message)).when(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
            assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "NewPass456"));
        }
        verifyNoInteractions(users, cache, tokens);
    }

    @Test void missingDisabledAndDeletedAccountsAreNotCreatedOrReactivated() {
        assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "NewPass456"));
        var user = account();
        for (int status : List.of(0, 2)) {
            user.setStatus(status);
            assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "NewPass456"));
        }
        verify(users, never()).update(any(UserInfoEntity.class), any(QueryWrapper.class));
        verify(users, never()).save(any(UserInfoEntity.class));
        verifyNoInteractions(cache, tokens);
    }

    @Test void smsOnlyAccountCanSetFirstPassword() {
        var user = account(); user.setPassword(null);
        when(users.update(any(UserInfoEntity.class), any(QueryWrapper.class))).thenReturn(true);
        assertDoesNotThrow(() -> login.resetPassword(PHONE, "123456", "NewPass456"));
    }

    @Test void failedDatabaseUpdateReportsFailureWithoutClaimingSuccess() {
        account();
        assertThrows(CoolException.class, () -> login.resetPassword(PHONE, "123456", "NewPass456"));
        verifyNoInteractions(cache, tokens);
    }

    @Test void oldPasswordStopsWorkingAndNewPasswordIssuesVersionedTokens() {
        var user = account();
        when(users.update(any(UserInfoEntity.class), any(QueryWrapper.class))).thenAnswer(call -> {
            user.setPassword(((UserInfoEntity)call.getArgument(0)).getPassword()); return true;
        });
        login.resetPassword(PHONE, "123456", "NewPass456");
        assertThrows(CoolException.class, () -> login.password(PHONE, "OldPass123"));
        when(tokens.generateToken(anyMap())).thenReturn("new-access");
        when(tokens.generateRefreshToken(anyMap())).thenReturn("new-refresh");
        Dict result = (Dict)login.password(PHONE, "NewPass456");
        assertEquals("new-access", result.getStr("token"));
        var payload = ArgumentCaptor.forClass(Dict.class);
        verify(tokens).generateToken(payload.capture());
        assertEquals(UserPasswordUtil.credentialVersion(user), payload.getValue().get("credentialVersion"));
        assertNotEquals(user.getPassword(), payload.getValue().get("credentialVersion"));
    }

    private JWT refreshJwt(UserInfoEntity user) {
        return JWT.create().setPayload("userId", 42L).setPayload("userType", "APP").setPayload("isRefresh", true)
                .setPayload("credentialVersion", UserPasswordUtil.credentialVersion(user));
    }

    @Test void refreshFromBeforeResetStaysInvalidEvenAfterUserLogsInAgain() {
        var user = account(); JWT old = refreshJwt(user);
        user.setPassword(UserPasswordUtil.encode("NewPass456"));
        when(tokens.validateRefreshToken("old-refresh")).thenReturn(true);
        when(tokens.getTokenInfo("old-refresh")).thenReturn(old);
        assertThrows(CoolException.class, () -> login.refreshToken("old-refresh"));
        verify(tokens, never()).generateToken(anyMap());
    }

    @Test void currentRefreshWorksButAdminAccessAndLegacyTokensAreRejected() {
        var user = account(); JWT jwt = refreshJwt(user);
        when(tokens.validateRefreshToken("refresh")).thenReturn(true);
        when(tokens.getTokenInfo("refresh")).thenReturn(jwt);
        assertNotNull(login.refreshToken("refresh"));
        jwt.setPayload("userType", "ADMIN");
        assertThrows(CoolException.class, () -> login.refreshToken("refresh"));
        jwt.setPayload("userType", "APP").setPayload("isRefresh", false);
        assertThrows(CoolException.class, () -> login.refreshToken("refresh"));
        jwt.setPayload("isRefresh", true).setPayload("credentialVersion", null);
        assertThrows(CoolException.class, () -> login.refreshToken("refresh"));
    }

    private boolean authenticated(UserInfoEntity user, Object version, boolean isRefresh) throws Exception {
        var mapper = mock(UserInfoMapper.class);
        when(mapper.selectOneById("42")).thenReturn(user);
        var ignored = mock(IgnoredUrlsProperties.class);
        when(ignored.getAdminAuthUrls()).thenReturn(List.of());
        var details = new JwtUser(42L, List.of(), true);
        when(cache.get("app:userDetails:42", JwtUser.class)).thenReturn(details);
        when(tokens.validateToken("access")).thenReturn(true);
        when(tokens.getTokenInfo("access")).thenReturn(JWT.create().setPayload("userId", 42L)
                .setPayload("userType", "APP").setPayload("isRefresh", isRefresh).setPayload("credentialVersion", version));
        var filter = new JwtAuthenticationTokenFilter(tokens, cache, ignored, mapper);
        var request = new MockHttpServletRequest("GET", "/app/user/info/person"); request.addHeader("Authorization", "access");
        SecurityContextHolder.clearContext();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Test void oldAccessCannotReviveWhenLoginRepopulatesCache() throws Exception {
        var user = account(); String old = UserPasswordUtil.credentialVersion(user);
        assertTrue(authenticated(user, old, false));
        user.setPassword(UserPasswordUtil.encode("NewPass456"));
        assertFalse(authenticated(user, old, false));
        assertTrue(authenticated(user, UserPasswordUtil.credentialVersion(user), false));
        assertFalse(authenticated(user, null, false));
        assertFalse(authenticated(user, UserPasswordUtil.credentialVersion(user), true));
        user.setStatus(0);
        assertFalse(authenticated(user, UserPasswordUtil.credentialVersion(user), false));
    }

    @Test void publicControllerReturnsSuccessWithoutIssuingToken() throws Exception {
        var service = mock(com.cool.modules.user.service.UserLoginService.class);
        var controller = new AppUserLoginController(service, mock(BaseSysLoginService.class));
        var req = new ResetPasswordParam(); req.setPhone(PHONE); req.setSmsCode("123456"); req.setPassword("NewPass456");
        assertEquals(1000, controller.resetPassword(req).getCode());
        verify(service).resetPassword(PHONE, "123456", "NewPass456");
        assertNotNull(AppUserLoginController.class.getMethod("resetPassword", ResetPasswordParam.class).getAnnotation(TokenIgnore.class));
    }
}
