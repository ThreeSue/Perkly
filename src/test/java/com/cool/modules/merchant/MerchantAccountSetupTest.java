package com.cool.modules.merchant;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.merchant.service.MerchantAccountService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 仅显式启用时通过商家服务开通账号并验证密码登录后提交保存
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 23:13:19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=merchant-setup-unused", "sms-auth.access-key-secret=merchant-setup-unused"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "merchant.setup", matches = "true")
@Transactional
@Commit
class MerchantAccountSetupTest {
    @Autowired MerchantAccountService accounts;
    @Autowired CoolCache cache;

    @Test void provisionAndVerifyLogin() {
        String password = System.getenv("MERCHANT_SETUP_PASSWORD");
        assertNotNull(password, "请通过环境变量设置开通密码");
        String username = System.getProperty("merchant.setup.username", "merchant_owner");
        Long merchantId = Long.valueOf(System.getProperty("merchant.setup.merchantId", "1"));
        Long id = accounts.provision(username, "商家管理员", password, merchantId);
        var input = new BaseSysLoginDto();
        input.setUsername(username); input.setPassword(password);
        input.setCaptchaId(UUID.randomUUID().toString()); input.setVerifyCode("1234");
        cache.set("verify:img:" + input.getCaptchaId(), "1234", 60);
        Dict session = (Dict) accounts.login(input);
        assertEquals(id, accounts.validate(session.getStr("token"), false).getId());
        assertEquals(merchantId, accounts.validate(session.getStr("token"), false).getMerchantId());
        accounts.logout(session.getStr("token"));
    }
}
