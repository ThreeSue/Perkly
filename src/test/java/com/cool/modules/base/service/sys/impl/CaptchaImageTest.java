package com.cool.modules.base.service.sys.impl;

import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.user.controller.app.params.CaptchaParam;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证各身份验证码透明静态图片与旧参数及校验流程兼容
 * @Version: v1.1
 * @Date: 2026-11-13
 * @Time: 00:30:00
 */
class CaptchaImageTest {
    private final CoolCache cache = mock(CoolCache.class);
    private final BaseSysLoginServiceImpl service = new BaseSysLoginServiceImpl(cache, null, null, null, null);

    @ParameterizedTest
    @CsvSource({"ADMIN,base64", "ADMIN,transparent", "ADMIN,svg", "APP,base64", "APP,transparent", "MERCHANT,base64"})
    void allClientsReceiveTransparentPngAndMatchingCachedCode(UserTypeEnum identity, String type) throws Exception {
        Map<?, ?> response = (Map<?, ?>) service.captcha(identity, type, 150, 45);
        assertEquals(2, response.size());
        String id = (String) response.get("captchaId");
        String data = (String) response.get("data");
        assertNotNull(id);
        assertTrue(data.startsWith("data:image/png;base64,"));
        byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
        assertEquals(0x89, bytes[0] & 255);
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(300, image.getWidth());
        assertEquals(90, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        int transparent = 0, visible = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) == 0) transparent++; else visible++;
        }
        assertTrue(transparent > image.getWidth() * image.getHeight() / 2);
        assertTrue(visible > 0);
        var answer = ArgumentCaptor.forClass(Object.class);
        verify(cache).set(eq("verify:img:" + id), answer.capture(), eq(1800L));
        String code = (String) answer.getValue();
        assertTrue(code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}"));
        when(cache.get("verify:img:" + id, String.class)).thenReturn(code);
        assertDoesNotThrow(() -> service.captchaCheck(id, code.toLowerCase(java.util.Locale.ROOT)));
    }

    @Test void invalidOrMissingCodeStillFailsAndInvalidatesChallenge() {
        when(cache.get("verify:img:test", String.class)).thenReturn("AB23");
        assertThrows(RuntimeException.class, () -> service.captchaCheck("test", "wrong"));
        verify(cache).del("verify:img:test");
        assertThrows(RuntimeException.class, () -> service.captchaCheck("expired", "AB23"));
    }

    @Test void omittedAppTypeDefaultsToBase64() {
        assertEquals("base64", new CaptchaParam().getType());
    }

    @Test void dimensionsAreBoundedBeforeAllocatingImage() throws Exception {
        Map<?, ?> response = (Map<?, ?>) service.captcha(UserTypeEnum.APP, "base64", Integer.MAX_VALUE, -1);
        String data = (String) response.get("data");
        var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data.split(",")[1])));
        assertEquals(600, image.getWidth());
        assertEquals(72, image.getHeight());
    }
}
