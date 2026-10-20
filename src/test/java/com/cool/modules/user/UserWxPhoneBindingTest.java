package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.entity.UserWxEntity;
import com.cool.modules.user.proxy.WxProxy;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.service.UserWxService;
import com.cool.modules.user.service.impl.UserLoginServiceImpl;
import com.cool.modules.user.util.UserSmsUtil;
import com.cool.modules.user.util.UserSmsUtil.SendSceneEnum;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class UserWxPhoneBindingTest {
    private final CoolCache cache = mock(CoolCache.class);
    private final JwtTokenUtil tokens = mock(JwtTokenUtil.class);
    private final UserInfoService users = mock(UserInfoService.class);
    private final UserSmsUtil sms = mock(UserSmsUtil.class);
    private final UserWxService wxUsers = mock(UserWxService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final UserLoginServiceImpl login = new UserLoginServiceImpl(cache, tokens, users, sms,
        mock(BaseSysLoginService.class), wxUsers, mock(WxProxy.class));
    private final Map<String, Object> values = new HashMap<>();
    private final UserWxEntity wx = new UserWxEntity();
    private static final String PHONE = "13800000000";

    @BeforeEach void setup() {
        wx.setId(12L); wx.setOpenid("server-verified-openid");
        when(wxUsers.getMiniUserInfo(anyString(), any(), any())).thenReturn(wx);
        when(wxUsers.getById(12L)).thenReturn(wx);
        when(wxUsers.updateById(any(UserWxEntity.class))).thenReturn(true);
        when(cache.get(anyString(), eq(Dict.class))).thenAnswer(i -> values.get(i.getArgument(0)));
        doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(1)); return null; })
            .when(cache).set(anyString(), any(), anyLong());
        doAnswer(i -> { values.remove(((String[]) i.getRawArguments()[0])[0]); return null; })
            .when(cache).del(any(String[].class));
        when(tokens.generateToken(any(Dict.class))).thenReturn("test-token");
        when(tokens.generateRefreshToken(any(Dict.class))).thenReturn("test-refresh");
        ReflectionTestUtils.setField(login, "transactionManager", transactions);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    }

    private String ticket() {
        Dict result = (Dict) login.mini("one-time-code", null, null);
        assertTrue(result.getBool("bindingRequired"));
        assertNull(result.get("token"));
        return result.getStr("bindingToken");
    }

    @Test void firstWechatLoginOnlyIssuesShortLivedBindingTicket() {
        ticket();
        verify(users, never()).save(any(UserInfoEntity.class));
        verifyNoInteractions(tokens, sms);
    }

    @Test void verifiedPhoneAssociatesExistingAccountAndConsumesTicket() {
        String ticket = ticket();
        UserInfoEntity existing = new UserInfoEntity();
        existing.setId(42L); existing.setPhone(PHONE); existing.setStatus(1); existing.setPassword("unchanged-hash");
        when(users.getOne(any(QueryWrapper.class))).thenReturn(null, existing, null);
        when(users.getById(42L)).thenReturn(existing);
        Dict preview = (Dict) login.bindWechatPhone(ticket, PHONE, "123456");
        assertTrue(preview.getBool("confirmationRequired"));
        verify(wxUsers, never()).updateById(any(UserWxEntity.class));
        verifyNoInteractions(tokens);
        Dict result = (Dict) login.confirmWechatBinding(preview.getStr("confirmationToken"), "phone");
        assertEquals("test-token", result.getStr("token"));
        verify(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
        var link = ArgumentCaptor.forClass(UserWxEntity.class);
        verify(wxUsers).updateById(link.capture());
        assertEquals(42L, link.getValue().getUserId());
        assertEquals("unchanged-hash", existing.getPassword());
        verify(users, never()).save(any(UserInfoEntity.class));
        assertThrows(CoolException.class, () -> login.bindWechatPhone(ticket, PHONE, "123456"));
        assertThrows(CoolException.class, () -> login.confirmWechatBinding(preview.getStr("confirmationToken"), "phone"));
        verify(transactions, times(2)).commit(any());
    }

    @Test void choosingWechatProfileOnlyUpdatesNicknameAndAvatarOnPhoneAccount() {
        String ticket = ticket();
        UserInfoEntity source = new UserInfoEntity(); source.setId(11L); source.setStatus(1);
        source.setNickName("微信账号昵称"); source.setAvatarUrl("https://example.com/wx.png");
        UserInfoEntity existing = new UserInfoEntity(); existing.setId(42L); existing.setPhone(PHONE); existing.setStatus(1);
        existing.setNickName("手机号账号昵称"); existing.setPassword("preserved-password-hash");
        when(users.getOne(any(QueryWrapper.class))).thenReturn(source, existing, source);
        when(users.getById(42L)).thenReturn(existing);
        when(users.updateById(any(UserInfoEntity.class))).thenReturn(true);
        Dict preview = (Dict) login.bindWechatPhone(ticket, PHONE, "123456");
        assertTrue(preview.getBool("canUseWechatProfile"));
        login.confirmWechatBinding(preview.getStr("confirmationToken"), "wechat");
        var patch = ArgumentCaptor.forClass(UserInfoEntity.class);
        verify(users).updateById(patch.capture());
        assertEquals(42L, patch.getValue().getId());
        assertEquals("微信账号昵称", patch.getValue().getNickName());
        assertEquals("https://example.com/wx.png", patch.getValue().getAvatarUrl());
        assertNull(patch.getValue().getPassword());
        assertNull(patch.getValue().getPhone());
        assertEquals("preserved-password-hash", existing.getPassword());
        verify(users, never()).removeById(any());
    }

    @Test void confirmationRejectsMissingTicketAndUnsupportedProfileSource() {
        assertThrows(CoolException.class, () -> login.confirmWechatBinding(UUID.randomUUID().toString(), "phone"));
        assertThrows(CoolException.class, () -> login.confirmWechatBinding(UUID.randomUUID().toString(), "arbitrary"));
        verifyNoInteractions(tokens, transactions);
        verify(wxUsers, never()).updateById(any(UserWxEntity.class));
    }

    @Test void unregisteredPhoneCreatesOneAccountAfterSmsValidation() {
        String ticket = ticket();
        when(users.save(any(UserInfoEntity.class))).thenAnswer(i -> {
            ((UserInfoEntity) i.getArgument(0)).setId(55L); return true;
        });
        login.bindWechatPhone(ticket, PHONE, "123456");
        var account = ArgumentCaptor.forClass(UserInfoEntity.class);
        verify(users).save(account.capture());
        assertEquals(PHONE, account.getValue().getPhone());
        assertEquals(1, account.getValue().getStatus());
    }

    @Test void missingOrExpiredTicketCannotVerifySmsOrWriteData() {
        assertThrows(CoolException.class, () -> login.bindWechatPhone(UUID.randomUUID().toString(), PHONE, "123456"));
        String ticket = ticket();
        ((Dict) values.get("app:wx:binding:" + ticket)).set("expiresAt", 1L);
        assertThrows(CoolException.class, () -> login.bindWechatPhone(ticket, PHONE, "123456"));
        verifyNoInteractions(sms, tokens, transactions);
    }

    @Test void otherWechatBindingCannotBeOverwritten() {
        String ticket = ticket();
        UserInfoEntity existing = new UserInfoEntity(); existing.setId(42L); existing.setStatus(1);
        when(users.getOne(any(QueryWrapper.class))).thenReturn(null, existing);
        when(wxUsers.count(any(QueryWrapper.class))).thenReturn(1L);
        assertThrows(CoolException.class, () -> login.bindWechatPhone(ticket, PHONE, "123456"));
        verifyNoInteractions(sms, tokens);
        verify(wxUsers, never()).updateById(any(UserWxEntity.class));
        verify(transactions).rollback(any());
    }

    @Test void invalidSmsCannotCreateAccountOrLinkWechat() {
        String ticket = ticket();
        doThrow(new CoolException("验证码错误")).when(sms).checkVerifyCode(PHONE, "123456", SendSceneEnum.ALL);
        assertThrows(CoolException.class, () -> login.bindWechatPhone(ticket, PHONE, "123456"));
        verify(users, never()).save(any(UserInfoEntity.class));
        verify(wxUsers, never()).updateById(any(UserWxEntity.class));
        verifyNoInteractions(tokens);
    }

    @Test void boundWechatLoginUsesExistingAccountWithoutSms() {
        wx.setUserId(42L);
        UserInfoEntity existing = new UserInfoEntity(); existing.setId(42L); existing.setPhone(PHONE); existing.setStatus(1);
        when(users.getById(42L)).thenReturn(existing);
        Dict result = (Dict) login.mini("fresh-code", null, null);
        assertEquals("test-token", result.getStr("token"));
        assertNull(result.get("bindingRequired"));
        verifyNoInteractions(sms);
    }
}
