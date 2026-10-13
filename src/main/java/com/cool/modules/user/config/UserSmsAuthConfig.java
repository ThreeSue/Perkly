package com.cool.modules.user.config;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import com.cool.core.exception.CoolException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云短信认证客户端配置。
 * 密钥从配置引用的环境变量读取，不在源码中保存。
 */
@Configuration
public class UserSmsAuthConfig {

    @Value("${sms-auth.access-key-id:}")
    private String accessKeyId;

    @Value("${sms-auth.access-key-secret:}")
    private String accessKeySecret;

    @Bean("aliyunSmsAuthClient")
    public Client aliyunSmsAuthClient() throws Exception {
        if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
            throw new CoolException("请配置短信认证的 AccessKey 环境变量");
        }

        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint("dypnsapi.aliyuncs.com");

        return new Client(config);
    }
}
