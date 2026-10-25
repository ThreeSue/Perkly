package com.cool.modules.merchant.location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 显式启用后使用本地凭证进行腾讯地图只读联调且不输出凭证
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:16:39
 */
@EnabledIfSystemProperty(named = "tencent.map.live", matches = "true")
class TencentMapLiveTest {
    @Test void geocodeSuggestAndReverseWithLocalCredentials() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("config/tencent-map.local.yml"));
        var values = yaml.getObject();
        assertNotNull(values, "缺少本地地图配置");
        var properties = new TencentMapProperties();
        properties.setKey(values.getProperty("tencent-map.key"));
        properties.setSecretKey(values.getProperty("tencent-map.secret-key"));
        var maps = new TencentMapService(properties, RestClient.builder());
        var address = maps.geocode("福建省福州市闽侯县人民政府", "福州");
        assertNotNull(address.longitude(), "地址解析未返回经度");
        assertNotNull(address.latitude(), "地址解析未返回纬度");
        assertFalse(maps.suggest("闽侯县人民政府", "福州").list().isEmpty(), "地点搜索未返回结果");
        assertNotNull(maps.reverse(address.longitude(), address.latitude()).address(), "逆地址解析未返回地址");
    }
}
