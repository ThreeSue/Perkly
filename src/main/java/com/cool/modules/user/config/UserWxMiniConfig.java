package com.cool.modules.user.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 小程序 SDK 直接接入；AppSecret 由本地环境变量提供。 */
@Configuration
public class UserWxMiniConfig {
    @Bean("userWxMiniService")
    public WxMaService userWxMiniService(
            @Value("${wx-mini.app-id:}") String appId,
            @Value("${wx-mini.app-secret:}") String secret) {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(appId.trim());
        config.setSecret(secret.trim());
        WxMaServiceImpl service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}
