package com.cool.modules.merchant.location;

import com.cool.core.exception.CoolException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 模拟腾讯响应验证签名原文与URL编码一致性及错误脱敏
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:14:18
 */
class TencentMapServiceTest {
    TencentMapProperties properties;
    TencentMapService service;
    MockRestServiceServer server;

    @BeforeEach void setup() {
        properties = new TencentMapProperties();
        properties.setKey("test-key"); properties.setSecretKey("test-secret");
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TencentMapService(properties, builder.build());
    }
    @AfterEach void verify() { server.verify(); }

    @Test void signsRawChineseAndSpecialCharactersButEncodesTransport() {
        String raw = "/ws/geocoder/v1?address=福州 A&B+店&key=test-keytest-secret";
        String expected = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        server.expect(request -> {
            assertEquals("apis.map.qq.com", request.getURI().getHost());
            String query = request.getURI().getRawQuery();
            assertTrue(query.contains("%20A%26B%2B"));
            assertTrue(query.contains("sig=" + expected));
            assertFalse(query.contains("test-secret"));
        }).andRespond(withSuccess("{\"status\":0,\"result\":{\"location\":{\"lat\":26.1,\"lng\":119.2},\"address_components\":{\"province\":\"福建省\",\"city\":\"福州市\"}}}", MediaType.APPLICATION_JSON));
        var result = service.geocode("福州 A&B+店", null);
        assertEquals(new BigDecimal("119.2"), result.longitude());
        assertEquals("GCJ-02", result.coordinateSystem());
        assertEquals("福州市", result.city());
    }

    @Test void canonicalParametersAreSorted() {
        var params = new TreeMap<String, String>(); params.put("location", "26,119"); params.put("key", "test-key");
        assertEquals(DigestUtils.md5DigestAsHex("/ws/geocoder/v1?key=test-key&location=26,119test-secret".getBytes(StandardCharsets.UTF_8)),
                TencentMapService.signature("/ws/geocoder/v1", params, "test-secret"));
    }

    @Test void reverseUsesLatitudeBeforeLongitudeAndPreservesCoordinates() {
        server.expect(request -> assertTrue(request.getURI().getRawQuery().contains("location=26.1%2C119.2")))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"address\":\"测试地址\",\"address_component\":{\"district\":\"闽侯县\"}}}", MediaType.APPLICATION_JSON));
        var result = service.reverse(new BigDecimal("119.2"), new BigDecimal("26.1"));
        assertEquals("测试地址", result.address()); assertEquals("闽侯县", result.district());
        assertEquals(new BigDecimal("26.1"), result.latitude());
    }

    @Test void suggestionReturnsNormalizedPlacesAndRestrictsCity() {
        server.expect(request -> assertTrue(request.getURI().getQuery().contains("region_fix=1")))
                .andRespond(withSuccess("{\"status\":0,\"count\":1,\"data\":[{\"id\":\"poi-1\",\"title\":\"测试门店\",\"address\":\"测试路1号\",\"location\":{\"lat\":26,\"lng\":119}}]}", MediaType.APPLICATION_JSON));
        var result = service.suggest("测试", "福州");
        assertEquals(1, result.total()); assertEquals("poi-1", result.list().get(0).id());
    }

    @Test void upstreamMessagesAndRequestUrlsAreNeverExposed() {
        server.expect(request -> {}).andRespond(withSuccess("{\"status\":110,\"message\":\"leak test-key test-secret\"}", MediaType.APPLICATION_JSON));
        var error = assertThrows(CoolException.class, () -> service.suggest("店", null));
        assertFalse(error.getMessage().contains("test-key"));
        assertFalse(error.getMessage().contains("test-secret"));
        assertTrue(error.getMessage().contains("110"));
    }

    @Test void transportFailureIsSanitized() {
        server.expect(request -> {}).andRespond(request -> { throw new java.net.SocketTimeoutException("https://example?key=test-key&sig=private"); });
        var error = assertThrows(CoolException.class, () -> service.geocode("测试地址", null));
        assertEquals("地图服务暂不可用，请稍后重试", error.getMessage());
        assertNull(error.getCause());
    }

    @Test void missingCredentialsAndInvalidInputFailBeforeCallingTencent() {
        properties.setSecretKey("");
        assertThrows(CoolException.class, () -> service.suggest("店", null));
        assertThrows(CoolException.class, () -> service.reverse(new BigDecimal("181"), BigDecimal.ZERO));
        assertThrows(CoolException.class, () -> service.suggest(" ", null));
    }

    @Test void gpsCoordinatesAreConvertedBeforeUsingTencentMap(){
        server.expect(request -> {assertEquals("/ws/coord/v1/translate",request.getURI().getPath());assertTrue(request.getURI().getQuery().contains("type=1"));assertTrue(request.getURI().getQuery().contains("locations=26.1,119.2"));})
            .andRespond(withSuccess("{\"status\":0,\"locations\":[{\"lat\":26.103,\"lng\":119.205}]}",MediaType.APPLICATION_JSON));
        var result=service.translateGps(new BigDecimal("119.2"),new BigDecimal("26.1"));assertEquals("GCJ-02",result.coordinateSystem());assertEquals(new BigDecimal("119.205"),result.longitude());
    }
}
