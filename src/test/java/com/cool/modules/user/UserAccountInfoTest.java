package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.hutool.core.lang.Dict;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserWxService;
import com.cool.modules.user.service.impl.UserInfoServiceImpl;
import com.cool.modules.user.util.UserSmsUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;

class UserAccountInfoTest {
    @Test void accountResponseMasksPhoneAndNeverExposesPasswordHashOrWechatIdentity() {
        UserWxService wx = mock(UserWxService.class);
        UserInfoServiceImpl service = spy(new UserInfoServiceImpl(mock(UserSmsUtil.class), wx));
        UserInfoEntity user = new UserInfoEntity(); user.setId(42L); user.setStatus(1);
        user.setPhone("13800000000"); user.setPassword("secret-hash");
        doReturn(user).when(service).getById(42L);
        when(wx.count(any(QueryWrapper.class))).thenReturn(1L);
        Dict result = (Dict) service.accountInfo(42L);
        assertEquals("138****0000", result.getStr("phoneMasked"));
        assertTrue(result.getBool("phoneBound"));
        assertTrue(result.getBool("wechatBound"));
        assertTrue(result.getBool("passwordSet"));
        assertFalse(result.containsKey("password"));
        assertFalse(result.containsKey("openid"));
        assertFalse(result.containsKey("unionid"));
    }

    @Test void unboundAccountReportsActualMissingBindings() {
        UserWxService wx = mock(UserWxService.class);
        UserInfoServiceImpl service = spy(new UserInfoServiceImpl(mock(UserSmsUtil.class), wx));
        UserInfoEntity user = new UserInfoEntity(); user.setId(42L); user.setStatus(1);
        doReturn(user).when(service).getById(42L);
        Dict result = (Dict) service.accountInfo(42L);
        assertFalse(result.getBool("phoneBound"));
        assertFalse(result.getBool("wechatBound"));
        assertFalse(result.getBool("passwordSet"));
    }
}
