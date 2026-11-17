package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.user.service.AliyunSmsAuthService;
import com.cool.modules.user.sms.SmsCloudUsage;
import com.cool.modules.user.sms.SmsScene;
import com.cool.modules.user.sms.SmsUsageService;
import com.cool.modules.user.sms.SmsUsageService.Outcome;
import com.cool.modules.user.util.UserSmsUtil;
import com.cool.modules.user.util.UserSmsUtil.SendSceneEnum;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在隔离库验证短信并发额度、匿名流水及运营接口
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:33:00
 */
@SpringBootTest(properties = {
    "perkly.profile.worker-enabled=false", "perkly.stamp.scheduler-enabled=false",
    "perkly.support.ai-dispatch-enabled=false", "perkly.content-risk.dispatch-enabled=false",
    "perkly.ai.worker-enabled=false", "auto-table.enable=false",
    "perkly.content-risk.scheduler-enabled=false", "perkly.activity-notifications.scheduler-enabled=false",
    "spring.quartz.auto-startup=false", "logging.level.com.cool=INFO", "sms-auth.receipts.enabled=false"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc(addFilters = false)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/perkly_ai_regression_20261106\\?.+")
class SmsUsageIntegrationTest {
    @Autowired SmsUsageService usage;
    @Autowired UserSmsUtil sms;
    @Autowired MockMvc mvc;
    @Autowired com.cool.modules.user.sms.SmsReceiptStore receiptStore;
    @Autowired com.cool.modules.user.mapper.SmsReceiptMapper receipts;
    @Autowired com.cool.modules.user.mapper.SmsUsageEventMapper usageEvents;
    @Autowired com.cool.modules.user.mapper.SmsUsageGateMapper usageGates;
    @MockBean AliyunSmsAuthService provider;
    @MockBean SmsCloudUsage cloud;
    @MockBean BaseSysLoginService captcha;

    @BeforeAll
    static void migrate(@Autowired DataSource database) {
        DatabasePopulatorUtils.execute(new ResourceDatabasePopulator(
            new FileSystemResource("deploy/sms-usage-20261117.sql")), database);
        DatabasePopulatorUtils.execute(new ResourceDatabasePopulator(
            new FileSystemResource("deploy/sms-receipts-20261117.sql")), database);
    }

    @AfterEach void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentFirstDaySendsSpendAtMostFiveSlots() throws Exception {
        usage.saveLimit(5);
        String phone = phone();
        var ready = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(12);
        try {
            var results = new ArrayList<java.util.concurrent.Future<Outcome>>();
            for (int i = 0; i < 12; i++) {
                SmsScene scene = i % 2 == 0 ? SmsScene.LOGIN : SmsScene.MERCHANT_ONBOARDING;
                results.add(pool.submit(() -> {
                    ready.await();
                    return usage.reserve(phone, scene, 0, null).outcome();
                }));
            }
            ready.countDown();
            var outcomes = new ArrayList<Outcome>();
            for (var result : results) outcomes.add(result.get(20, TimeUnit.SECONDS));
            assertEquals(5, outcomes.stream().filter(x -> x == Outcome.RESERVED).count());
            assertEquals(7, outcomes.stream().filter(x -> x == Outcome.DAILY_LIMIT).count());
            verifyNoInteractions(provider);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void publicEntriesShareQuotaAndExistingChallengeCanStillBeVerified() throws Exception {
        usage.saveLimit(2);
        String phone = phone();
        when(provider.getValidTime()).thenReturn(300L);
        when(provider.getInterval()).thenReturn(60L);
        when(provider.send(eq(phone), anyString())).thenReturn(new AliyunSmsAuthService.Accepted("Biz-1", "Request-1"));
        when(provider.check(phone, "123456")).thenReturn(true);
        try {
            mvc.perform(post("/app/user/login/smsCode").contentType("application/json")
                    .content("{\"phone\":\"" + phone + "\",\"captchaId\":\"first\",\"code\":\"A1B2\"}"))
                .andExpect(jsonPath("$.code").value(1000));
            mvc.perform(post("/app/merchant/onboarding/smsCode").contentType("application/json")
                    .content("{\"phone\":\"" + phone + "\",\"captchaId\":\"second\",\"verifyCode\":\"A1B2\"}"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.data.reason").value("COOLDOWN"));
            usage.saveLimit(1);
            mvc.perform(post("/app/merchant/onboarding/smsCode").contentType("application/json")
                    .content("{\"phone\":\"" + phone + "\",\"captchaId\":\"third\",\"verifyCode\":\"A1B2\"}"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.data.reason").value("DAILY_LIMIT"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());
            verify(provider, times(1)).send(eq(phone), startsWith("SMS-"));
            assertNotNull(sms.checkVerifyCode(phone, "123456", SendSceneEnum.ALL, SmsScene.LOGIN));
            login("sms:usage:view");
            var response = mvc.perform(get("/admin/sms/usage/events").param("result", "ACCEPTED"))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.list[0].phoneMask").value(phone.substring(0, 3) + "****" + phone.substring(7)))
                .andExpect(jsonPath("$.data.list[0].actorName").value("未登录"))
                .andExpect(jsonPath("$.data.list[0].scene").value("LOGIN"))
                .andExpect(jsonPath("$.data.list[0].verifiedAccount").isEmpty())
                .andReturn().getResponse().getContentAsString();
            assertFalse(response.contains(phone));
            assertFalse(response.contains("123456"));
            mvc.perform(get("/admin/sms/usage/overview").param("days", "1"))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.groups[?(@.result == 'ACCEPTED')].total").isNotEmpty())
                .andExpect(jsonPath("$.data.groups[?(@.result == 'DAILY_LIMIT')].scene").value(org.hamcrest.Matchers.hasItem("MERCHANT_ONBOARDING")));
        } finally {
            SecurityContextHolder.clearContext();
            usage.saveLimit(5);
        }
    }

    @Test
    void onlyEditorsCanChangeLimitsAndCloudQueriesRequireReadPermission() throws Exception {
        mvc.perform(get("/admin/sms/usage/limits")).andExpect(jsonPath("$.code").value(403));
        login("sms:usage:view");
        mvc.perform(post("/admin/sms/usage/limits").contentType("application/json").content("{\"dailyLimit\":4}"))
            .andExpect(jsonPath("$.code").value(403));
        LocalDate today = LocalDate.now(SmsUsageService.ZONE);
        when(cloud.usage(today.minusDays(6), today)).thenReturn(new SmsCloudUsage.Usage(5L, 4L, 1L, 0L, List.of()));
        mvc.perform(get("/admin/sms/usage/cloud/usage")).andExpect(jsonPath("$.data.total").value(5));
        var month = YearMonth.from(today);
        when(cloud.billing(month)).thenReturn(new SmsCloudUsage.Billing(month.toString(), "0.20", List.of()));
        mvc.perform(get("/admin/sms/usage/cloud/billing").param("month", month.toString()))
            .andExpect(jsonPath("$.data.amount").value("0.20"));
        login("sms:usage:view", "sms:usage:manage");
        try {
            mvc.perform(post("/admin/sms/usage/limits").contentType("application/json").content("{\"dailyLimit\":4}"))
                .andExpect(jsonPath("$.data.dailyLimit").value(4));
            mvc.perform(get("/admin/sms/usage/events").param("kind", "POLICY"))
                .andExpect(jsonPath("$.data.list[0].nextLimit").value(4));
        } finally {
            SecurityContextHolder.clearContext();
            usage.saveLimit(5);
        }
    }

    @Test
    void receiptRedeliveryIsIdempotentAndStatsMatchOriginalSend() throws Exception {
        String phone = phone();
        var admission = usage.reserve(phone, SmsScene.LOGIN, 0, null);
        String biz = "receipt-test-" + java.util.UUID.randomUUID();
        usage.finish(admission.id(), Outcome.ACCEPTED, biz, "request", "OK");
        String body = """
            {"biz_id":"%s","phone_number":"%s","success":true,"err_code":"DELIVERED",
             "send_time":"2026-11-17 15:00:00","report_time":"2026-11-17 15:00:05"}
            """.formatted(biz, phone);
        try {
            receiptStore.accept(body);
            receiptStore.accept(body);
            assertEquals(1, receipts.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.user.sms.SmsReceipt::getBizId, biz)));
            usage.verification(admission.id(), phone, SmsScene.LOGIN, Outcome.VERIFIED);
            usage.verification(admission.id(), phone, SmsScene.LOGIN, Outcome.VERIFIED);
            assertEquals(1, usageEvents.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.user.sms.SmsUsageEvent::getSendId, admission.id())
                .eq(com.cool.modules.user.sms.SmsUsageEvent::getResult, "VERIFIED")));
            login("sms:usage:view");
            mvc.perform(get("/admin/sms/usage/receipts").param("days", "1"))
                .andExpect(jsonPath("$.data.delivered").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.sync.state").value("DISABLED"));
        } finally {
            receipts.deleteByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.user.sms.SmsReceipt::getBizId, biz));
            usageEvents.deleteByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.user.sms.SmsUsageEvent::getSendId, admission.id()));
            usageEvents.deleteById(admission.id());
        }
    }

    @Test
    void rejectedAndUnknownSendsStillSpendQuotaAndNextDayResetsIt() throws Exception {
        usage.saveLimit(2);
        String phone = phone();
        try {
            var first = usage.reserve(phone, SmsScene.LOGIN, 0, null);
            usage.finish(first.id(), Outcome.REJECTED, null, "failure-request", "isv.BUSINESS_LIMIT_CONTROL");
            var second = usage.reserve(phone, SmsScene.REGISTER, 0, null);
            usage.finish(second.id(), Outcome.UNKNOWN, null, null, null);
            assertEquals(Outcome.DAILY_LIMIT, usage.reserve(phone, SmsScene.RESET_PASSWORD, 0, null).outcome());
            // Resolve this test phone's gate without storing its raw number in the database.
            var fingerprint = SmsUsageService.class.getDeclaredMethod("fingerprint", String.class);
            fingerprint.setAccessible(true);
            var target = org.springframework.test.util.AopTestUtils.getTargetObject(usage);
            String key = (String) fingerprint.invoke(target, phone);
            var gate = usageGates.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.user.sms.SmsUsageGate::getPhoneKey, key));
            gate.setDayKey(LocalDate.now(SmsUsageService.ZONE).minusDays(1).toString());
            usageGates.update(gate);
            assertEquals(Outcome.RESERVED, usage.reserve(phone, SmsScene.LOGIN, 0, null).outcome());
            login("sms:usage:view");
            mvc.perform(get("/admin/sms/usage/overview"))
                .andExpect(jsonPath("$.data.dailyLimit").value(2))
                .andExpect(jsonPath("$.data.intervalSeconds").value(org.hamcrest.Matchers.greaterThanOrEqualTo(60)))
                .andExpect(jsonPath("$.data.recordingSince").isNotEmpty());
            verifyNoInteractions(provider, cloud);
        } finally { usage.saveLimit(5); }
    }

    @Test
    void invalidLimitsCannotChangeActivePolicy() throws Exception {
        usage.saveLimit(5);
        login("sms:usage:view", "sms:usage:manage");
        for (String body : List.of("{\"dailyLimit\":0}", "{\"dailyLimit\":31}", "{}")) {
            mvc.perform(post("/admin/sms/usage/limits").contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(400));
            assertEquals(5, usage.limits().dailyLimit());
        }
    }

    @Test
    void receiptEndpointRequiresPermission() throws Exception {
        login("sms:usage:manage");
        mvc.perform(get("/admin/sms/usage/receipts")).andExpect(jsonPath("$.code").value(403));
    }

    private static void login(String... keys) {
        List<GrantedAuthority> permissions = java.util.Arrays.stream(keys)
            .map(key -> (GrantedAuthority) new SimpleGrantedAuthority(key)).toList();
        var jwt = new JwtUser(1L, "sms-test", "", permissions, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, permissions));
    }

    private static String phone() {
        return "199" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
}
