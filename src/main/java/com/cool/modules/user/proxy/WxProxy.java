package com.cool.modules.user.proxy;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.binarywang.wx.miniapp.bean.WxMaUserInfo;
import com.cool.core.util.CoolPluginInvokers;
import com.cool.core.exception.CoolException;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class WxProxy {
    private final WxMaService miniService;

    public WxProxy(@Qualifier("userWxMiniService") WxMaService miniService) {
        this.miniService = miniService;
    }

    public WxMaService getWxMaService() {
        var config = miniService.getWxMaConfig();
        if (config == null || config.getAppid() == null || config.getAppid().isBlank()
                || config.getSecret() == null || config.getSecret().isBlank()) {
            throw new CoolException("请在后端运行配置中设置 WECHAT_MINI_APP_SECRET，小程序 AppID 必须与前端一致");
        }
        return miniService;
    }
    public WxMpService getWxMpService() {
        return  (WxMpService)CoolPluginInvokers.invoke("wx", "getWxMpService");
    }

    public WxMaJscode2SessionResult getSessionInfo(String jsCode) throws WxErrorException {
        return getWxMaService().getUserService()
            .getSessionInfo(jsCode);
    }

    public WxMaPhoneNumberInfo getPhoneNumber(String jsCode) throws WxErrorException {
        return getWxMaService().getUserService()
            .getPhoneNumber(jsCode);
    }

    public WxMaUserInfo getUserInfo(String sessionKey, String encryptedData, String ivStr) throws WxErrorException {
        return getWxMaService().getUserService()
            .getUserInfo(sessionKey, encryptedData, ivStr);
    }

}
