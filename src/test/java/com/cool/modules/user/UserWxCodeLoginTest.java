package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.entity.UserWxEntity;
import com.cool.modules.user.proxy.WxProxy;
import com.cool.modules.user.config.UserWxMiniConfig;
import com.cool.modules.user.service.impl.UserWxServiceImpl;
import org.junit.jupiter.api.Test;

class UserWxCodeLoginTest {
    @Test void directSdkRejectsMissingSecretButNeedsNoPluginWhenConfigured() {
        UserWxMiniConfig config = new UserWxMiniConfig();
        var missingSecret = config.userWxMiniService("wx-test-app", "");
        assertThrows(CoolException.class, () -> new WxProxy(missingSecret).getWxMaService());
        var configured = config.userWxMiniService("wx-test-app", "test-only-secret");
        assertSame(configured, new WxProxy(configured).getWxMaService());
    }

    @Test void codeOnlyLoginUsesIdentityVerifiedByWechatWithoutProfileDecryption() throws Exception {
        WxProxy proxy = mock(WxProxy.class);
        WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
        session.setOpenid("verified-openid");
        session.setUnionid("verified-unionid");
        when(proxy.getSessionInfo("temporary-code")).thenReturn(session);
        UserWxServiceImpl service = spy(new UserWxServiceImpl(proxy));
        doAnswer(invocation -> invocation.getArgument(0)).when(service).getBySave(any(), eq(0));

        UserWxEntity result = service.getMiniUserInfo("temporary-code", null, null);

        assertEquals("verified-openid", result.getOpenid());
        assertEquals("verified-unionid", result.getUnionid());
        verify(proxy).getSessionInfo("temporary-code");
        verifyNoMoreInteractions(proxy);
    }

    @Test void missingVerifiedIdentityCannotCreateWechatAccount() throws Exception {
        WxProxy proxy = mock(WxProxy.class);
        when(proxy.getSessionInfo("bad-code")).thenReturn(new WxMaJscode2SessionResult());
        UserWxServiceImpl service = spy(new UserWxServiceImpl(proxy));

        assertThrows(CoolException.class, () -> service.getMiniUserInfo("bad-code", null, null));
        verify(service, never()).getBySave(any(), anyInt());
    }
}
