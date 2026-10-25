package com.cool.modules.approval;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.request.RequestParamsFilter;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.approval.entity.MerchantApplicationEntity;
import com.cool.modules.approval.entity.req.MerchantRegistrationReq;
import com.cool.modules.approval.mapper.MerchantApplicationMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.coupon.entity.CouponPublicationEntity;
import com.cool.modules.coupon.mapper.CouponPublicationMapper;
import com.cool.modules.coupon.service.CouponTradeService;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.mapper.MerchantCategoryMapper;
import com.cool.modules.merchant.mapper.MerchantStoreCategoryMapper;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 事务回滚验证真实接口入驻审批受限登录门店发券购券及单次核销全流程
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 23:38:43
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=onboarding-test-unused", "sms-auth.access-key-secret=onboarding-test-unused",
        "logging.level.com.cool=WARN", "spring.quartz.auto-startup=false"
})
@ActiveProfiles("local")
@Transactional
class MerchantOnboardingIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Autowired CoolCache cache;
    @Autowired JwtTokenUtil tokens;
    @Autowired BaseSysUserMapper users;
    @Autowired MerchantApplicationMapper applications;
    @Autowired MerchantCategoryMapper categories;
    @Autowired MerchantStoreCategoryMapper storeTypes;
    @Autowired CouponPublicationMapper publications;
    @Autowired MerchantAccountService accounts;
    @Autowired CouponTradeService trade;
    @Autowired UserInfoService customers;
    @Autowired com.cool.modules.approval.service.GuestMerchantOnboardingService onboarding;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.modules.user.util.UserSmsUtil sms;
    MockMvc mvc;
    String adminToken;
    BaseSysUserEntity operator;
    MerchantRegistrationReq request;
    final List<String> cacheKeys = new ArrayList<>();

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(security, new RequestParamsFilter()).build();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        operator = new BaseSysUserEntity(); operator.setUsername("itops_" + suffix);
        operator.setName("联调测试运营"); operator.setNickName("联调测试运营"); operator.setStatus(1);
        operator.setPassword(new BCryptPasswordEncoder().encode(UUID.randomUUID().toString())); operator.setPasswordV(1);
        users.insertSelective(operator);
        List<org.springframework.security.core.GrantedAuthority> permissions = List.of(new SimpleGrantedAuthority("approval:center:review"),
                new SimpleGrantedAuthority("approval:center:page"), new SimpleGrantedAuthority("approval:center:info"));
        JwtUser principal = new JwtUser(operator.getId(), operator.getUsername(), "", permissions, true);
        putCache("admin:userDetails:" + operator.getUsername(), principal);
        putCache("admin:passwordVersion:" + operator.getId(), 1);
        adminToken = tokens.generateToken(Dict.create().set("userType", "ADMIN").set("userId", operator.getId())
                .set("username", operator.getUsername()).set("passwordVersion", 1));
        request = new MerchantRegistrationReq();
        request.setUsername("ittest_" + suffix); request.setPassword("IntegrationTest123!");
        request.setMerchantName("全流程联调商家_事务回滚_" + suffix); request.setContactName("联调测试联系人");
        request.setContactPhone("13800000001"); request.setAddress("联调专用测试地址");
        var category = categories.selectOneByQuery(QueryWrapper.create().eq(MerchantCategoryEntity::getStatus, 1).limit(1));
        assertNotNull(category); request.setCategoryId(category.getId());
        captcha(request);
    }

    @AfterEach void cleanup() {
        cacheKeys.forEach(key -> cache.del(key));
        SecurityContextHolder.clearContext();
    }

    @Test void realRegistrationApprovalStoreCouponPurchaseAndRedemptionFlow() throws Exception {
        ok(post("/merchant/auth/register"), request, null);
        MerchantApplicationEntity first = current();
        assertEquals(1, first.getStatus());
        assertEquals(0, users.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, request.getUsername())));
        assertFalse(first.getSnapshot().contains(request.getPassword()));
        JSONObject applicant = login();
        String applicantToken = applicant.getStr("token");
        ok(get("/merchant/workspace/me"), null, applicantToken);
        mvc.perform(post("/merchant/workspace/store/page").header("Authorization", applicantToken)
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());

        review(first, false, "请补充经营地址说明");
        JSONObject rejected = ok(get("/merchant/auth/onboarding"), null, applicantToken).getJSONObject("data");
        assertEquals(3, rejected.getInt("status")); assertEquals("请补充经营地址说明", rejected.getStr("reason"));
        request.setAddress("已补充的联调经营地址"); captcha(request);
        ok(post("/merchant/auth/resubmit"), request, null);
        MerchantApplicationEntity second = current();
        assertEquals(2, second.getSubmissionNo()); assertEquals(first.getApplicationNo(), second.getApplicationNo());
        assertEquals(0, applications.selectOneById(first.getId()).getCurrentFlag());
        review(second, true, "联调资料完整，同意入驻");
        JSONObject approved = ok(get("/merchant/auth/onboarding"), null, applicantToken).getJSONObject("data");
        assertEquals(2, approved.getInt("status"));
        assertEquals("PENDING_ACTIVATION",approved.getStr("activationState"));
        assertNull(current().getMerchantId());
        String phoneTicket=onboarding.verify(request.getContactPhone(),"123456");
        String activationCode=onboarding.info(phoneTicket,second.getApplicationNo()).getActivationCode();
        onboarding.activate(new com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.Activate(phoneTicket,activationCode,request.getUsername(),request.getPassword()));
        cache.del("onboarding:"+cn.hutool.crypto.SecureUtil.sha256(phoneTicket));
        JSONObject session = login();
        String merchantToken = session.getStr("token");
        BaseSysUserEntity owner = accounts.validate(merchantToken, false);
        assertNotNull(owner.getMerchantId()); assertEquals("OWNER", owner.getMerchantRole());
        assertTrue(new BCryptPasswordEncoder().matches(request.getPassword(), owner.getPassword()));
        ok(get("/merchant/workspace/me"), null, merchantToken);
        var history = ok(get("/admin/approval/center/info").param("type", "MERCHANT").param("id", second.getId().toString()), null, adminToken)
                .getJSONObject("data").getJSONArray("history");
        assertEquals(List.of("SUBMIT", "REJECT", "SUBMIT", "APPROVE", "ACTIVATE"),
                history.toList(JSONObject.class).stream().map(e -> e.getStr("action")).toList());
        assertEquals(operator.getUsername(), history.getJSONObject(3).getStr("actorName"));

        var type = storeTypes.selectOneByQuery(QueryWrapper.create().eq(MerchantStoreCategoryEntity::getStatus, 1).limit(1));
        assertNotNull(type);
        Long store = ok(post("/merchant/workspace/store/save"), Map.of("name", "联调专用门店", "categoryId", type.getId(),
                "address", "联调门店地址", "phone", "13800000001", "status", 1), merchantToken).getLong("data");
        Map<String, Object> coupon = new LinkedHashMap<>();
        coupon.put("name", "联调20元代金券_事务回滚"); coupon.put("type", "CASH");
        coupon.put("deductAmount", 20); coupon.put("salePrice", new BigDecimal("9.90"));
        coupon.put("validityType", "AFTER_ISSUE"); coupon.put("validDays", 30); coupon.put("status", 1);
        coupon.put("scopeType", 1); coupon.put("storeIds", List.of(store)); coupon.put("issueLimit", 2); coupon.put("perCustomerLimit", 2);
        Long template = ok(post("/merchant/workspace/coupon/save"), coupon, merchantToken).getLong("data");
        ok(post("/merchant/workspace/coupon/publication"), Map.of("id", template, "version", 1, "action", "submitAudit"), merchantToken);
        var publication = publications.selectOneByQuery(QueryWrapper.create()
                .eq(CouponPublicationEntity::getCouponTemplateId, template).eq(CouponPublicationEntity::getChannel, "PLATFORM"));
        ok(post("/admin/approval/center/review"), Map.of("type", "COUPON", "id", publication.getId(), "version", 1, "approved", true), adminToken);
        assertEquals(0, publications.selectOneById(publication.getId()).getPublishStatus());
        ok(post("/merchant/workspace/coupon/publication"), Map.of("id", template, "version", 1, "action", "publish"), merchantToken);
        ok(get("/app/coupon/info").param("id", template.toString()), null, null);

        var customer = new UserInfoEntity(); customer.setStatus(1); customer.setNickName("联调测试顾客_事务回滚");
        String phone;
        do { phone = "198" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)); }
        while (customers.count(QueryWrapper.create().eq(UserInfoEntity::getPhone, phone)) > 0);
        customer.setPhone(phone); customers.save(customer);
        var order = trade.create(customer.getId(), template, 1, UUID.randomUUID().toString());
        var paid = trade.simulatePay(customer.getId(), ((Number) order.get("id")).longValue(), "SUCCESS");
        Long couponId = ((Number) ((List<?>) paid.get("couponIds")).get(0)).longValue();
        String code = (String) trade.walletInfo(customer.getId(), couponId).get("code");
        assertEquals(true, trade.redeem(owner.getId(), store, code, 5000L).get("confirmed"));
        assertEquals("USED", trade.walletInfo(customer.getId(), couponId).get("state"));
        assertThrows(CoolException.class, () -> trade.redeem(owner.getId(), store, code, 5000L));
    }

    @Test void invalidCaptchaCreatesNeitherApplicationNorBusinessAccount() throws Exception {
        request.setVerifyCode("wrong");
        var result = json(post("/merchant/auth/register"), request, null);
        assertNotEquals(1000, result.getInt("code")); assertNull(current());
        assertEquals(0, users.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, request.getUsername())));
    }

    @Test void unusableContactNumberIsRejectedBeforeEnteringApproval() throws Exception {
        request.setContactPhone("123456");
        assertNotEquals(1000, json(post("/merchant/auth/register"), request, null).getInt("code"));
        assertNull(current());
    }

    @Test void duplicateSubmissionAndReReviewCannotDuplicateMerchantAccounts() throws Exception {
        ok(post("/merchant/auth/register"), request, null);
        var application = current(); captcha(request);
        assertNotEquals(1000, json(post("/merchant/auth/register"), request, null).getInt("code"));
        review(application, true, "联调审核通过");
        assertNotEquals(1000, json(post("/admin/approval/center/review"), Map.of("type", "MERCHANT", "id", application.getId(),
                "version", 1, "approved", true), adminToken).getInt("code"));
        assertEquals(0, users.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, request.getUsername())));
    }

    @Test void wrongPasswordCannotResubmitRejectedApplication() throws Exception {
        ok(post("/merchant/auth/register"), request, null); var application = current();
        review(application, false, "联调退回完善");
        request.setPassword("WrongPassword123!"); captcha(request);
        assertNotEquals(1000, json(post("/merchant/auth/resubmit"), request, null).getInt("code"));
        assertEquals(application.getId(), current().getId()); assertEquals(3, current().getStatus());
    }

    private void captcha(MerchantRegistrationReq input) {
        String id = UUID.randomUUID().toString(); putCache("verify:img:" + id, "1234");
        input.setCaptchaId(id); input.setVerifyCode("1234");
    }
    private JSONObject login() throws Exception {
        captcha(request);
        JSONObject result = ok(post("/merchant/auth/login"), Map.of("username", request.getUsername(), "password", request.getPassword(),
                "captchaId", request.getCaptchaId(), "verifyCode", request.getVerifyCode()), null).getJSONObject("data");
        trackSession(result); return result;
    }
    private void trackSession(JSONObject result) {
        var jwt = tokens.getTokenInfo(result.getStr("token"));
        cacheKeys.add((Boolean.TRUE.equals(jwt.getPayload("applicant")) ? "merchant:applicant:" : "merchant:session:") + jwt.getPayload("sessionId"));
    }
    private void putCache(String key, Object value) { cache.set(key, value, 120); cacheKeys.add(key); }
    private MerchantApplicationEntity current() { return applications.selectOneByQuery(QueryWrapper.create()
            .eq(MerchantApplicationEntity::getUsername, request.getUsername()).eq(MerchantApplicationEntity::getCurrentFlag, 1)); }
    private void review(MerchantApplicationEntity application, boolean approved, String reason) throws Exception {
        ok(post("/admin/approval/center/review"), Map.of("type", "MERCHANT", "id", application.getId(),
                "version", application.getSubmissionNo(), "approved", approved, "reason", reason), adminToken);
    }
    private JSONObject ok(MockHttpServletRequestBuilder builder, Object body, String token) throws Exception {
        JSONObject response = json(builder, body, token);
        assertEquals(1000, response.getInt("code"), response.getStr("message")); return response;
    }
    private JSONObject json(MockHttpServletRequestBuilder builder, Object body, String token) throws Exception {
        SecurityContextHolder.clearContext();
        if (body != null) builder.contentType("application/json").content(JSONUtil.toJsonStr(body));
        if (token != null) builder.header("Authorization", token);
        var response = mvc.perform(builder).andExpect(status().isOk()).andReturn().getResponse();
        return JSONUtil.parseObj(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
