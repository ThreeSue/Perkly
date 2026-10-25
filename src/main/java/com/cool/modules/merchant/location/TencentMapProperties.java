package com.cool.modules.merchant.location;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 从本地配置或环境变量读取腾讯地图后端凭证且避免生成密钥日志
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:11:44
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tencent-map")
public class TencentMapProperties {
    private String key;
    private String secretKey;
}
