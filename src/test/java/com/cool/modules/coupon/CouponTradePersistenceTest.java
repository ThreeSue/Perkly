package com.cool.modules.coupon;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import com.cool.core.cache.CoolCache;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.*;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.service.UserLoginService;
import com.cool.modules.user.util.UserPasswordUtil;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.Filter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在事务回滚的数据库中验证订单发券二维码隔离与单次核销
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:21:49
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"sms-auth.access-key-id=trade-test-unused","sms-auth.access-key-secret=trade-test-unused"})
@ActiveProfiles("local")
@Transactional
class CouponTradePersistenceTest {
    @Autowired CouponTradeService trade;
    @Autowired ConsumptionOrderService bills;
    @Autowired CouponTemplateService templates;
    @Autowired CouponPublicationService publications;
    @Autowired MerchantAccountService accounts;
    @Autowired UserInfoService users;
    @Autowired UserLoginService login;
    @Autowired CoolCache cache;
    @Autowired org.mybatis.spring.SqlSessionTemplate session;
    @Autowired WebApplicationContext web;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    JdbcTemplate jdbc;
    Long merchant,store,otherStore,operator,customer,otherCustomer,templateId;
    String username;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(session.getConnection(),true));
        merchant = merchant("自动购券测试商家_回滚");
        Long category = jdbc.queryForObject("SELECT id FROM store_type WHERE name='直营店'", Long.class);
        store = insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,'自动购券测试门店','测试地址','13800000001',1)",merchant,category);
        otherStore = insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,'自动测试其他门店','测试地址','13800000001',1)",merchant,category);
        username = "trade_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        operator = accounts.provision(username,"自动核销测试管理员","MerchantTest123!",merchant);
        customer = user(); otherCustomer = user();
        var c = new CouponTemplateEntity(); c.setMerchantId(merchant); c.setName("自动购券测试代金券_回滚");
        c.setType("CASH"); c.setDeductAmount(new BigDecimal("20")); c.setSalePrice(new BigDecimal("9.90"));
        c.setScopeType(1); c.setStoreIds(List.of(store)); c.setStatus(1); c.setValidityType("AFTER_ISSUE");
        c.setValidDays(30); c.setIssueLimit(10L); c.setPerCustomerLimit(5);
        templateId = templates.add(c); publish(templateId);
    }
    @AfterEach void cleanContext() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); cache.del("app:userDetails:"+customer,"app:userDetails:"+otherCustomer); }
    Long user() {
        String phone;
        do { phone = "199"+String.format("%08d",java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000)); }
        while (jdbc.queryForObject("SELECT COUNT(*) FROM user_info WHERE phone=?",Integer.class,phone)>0);
        var user = new UserInfoEntity(); user.setPhone(phone); user.setStatus(1); user.setNickName("购券自动测试_回滚");
        user.setPassword(UserPasswordUtil.encode("CustomerTest123!")); users.save(user); return user.getId();
    }
    Long insert(String sql,Object... args) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection->{ var statement=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); return statement; },keys);
        return keys.getKey().longValue();
    }
    Long merchant(String name) {
        Long category=jdbc.queryForObject("SELECT id FROM merchant_category LIMIT 1",Long.class);
        return insert("INSERT INTO merchant_info(name,category_id,contact_name,contact_phone,status) VALUES(?,?,'自动测试联系人','13800000001',1)",name,category);
    }
    void publish(Long id) { var c=templates.getById(id); publications.submitAudit(id,c.getRuleVersion()); publications.audit(id,c.getRuleVersion(),true); publications.publish(id,c.getRuleVersion()); }
    Map<String,Object> order(int quantity) { return trade.create(customer,templateId,quantity,UUID.randomUUID().toString()); }
    Long id(Map<String,Object> value) { return ((Number)value.get("id")).longValue(); }
    Long pay(Map<String,Object> order) { var paid=trade.simulatePay(customer,id(order),"SUCCESS"); return ((Number)((List<?>)paid.get("couponIds")).get(0)).longValue(); }

    @Test void paidOrderIssuesUniqueCodesAndPngQrAndCanBeRedeemedOnce() {
        var order=order(2); assertEquals(1980L,order.get("totalAmount"));
        assertEquals(0L,trade.wallet(customer,1,20,null).getPagination().getTotal());
        Long coupon=pay(order);
        assertEquals(2L,trade.wallet(customer,1,20,"UNUSED").getPagination().getTotal());
        var detail=trade.walletInfo(customer,coupon); assertTrue(((String)detail.get("code")).matches("[a-f0-9]{64}"));
        assertTrue(((String)detail.get("qrCodeImage")).startsWith("data:image/png;base64,"));
        assertArrayEquals(CouponTradeRules.qrPng((String)detail.get("code")),trade.walletQr(customer,coupon));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT code) FROM customer_coupon WHERE customer_id=?",Integer.class,customer));
        var preview=trade.preview(operator,store,(String)detail.get("code"),5000L);
        assertEquals(2000L,preview.get("discountAmount"));
        assertEquals("UNUSED",trade.walletInfo(customer,coupon).get("state"));
        var redeemed=trade.redeem(operator,store,(String)detail.get("code"),5000L);
        assertEquals(true,redeemed.get("confirmed")); assertEquals("USED",trade.walletInfo(customer,coupon).get("state"));
        assertThrows(CoolException.class,()->trade.redeem(operator,store,(String)detail.get("code"),5000L));
        assertEquals(1L,trade.redemptions(operator,1,20,null).getPagination().getTotal());
    }
    @Test void retryingCreateAndPayDoesNotDuplicateOrdersOrCoupons() {
        String key=UUID.randomUUID().toString(); var order=trade.create(customer,templateId,1,key);
        assertEquals(id(order),id(trade.create(customer,templateId,1,key)));
        assertThrows(CoolException.class,()->trade.create(customer,templateId,2,key));
        pay(order); publications.unpublish(templateId,1);
        assertEquals("PAID",trade.simulatePay(customer,id(order),"SUCCESS").get("orderStatus"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM customer_coupon WHERE customer_id=?",Integer.class,customer));
        assertEquals(1L,templates.getById(templateId).getIssuedCount());
    }
    @Test void usersCannotViewPayCancelOrReadOtherUsersCouponCodes() {
        var order=order(1); Long coupon=pay(order);
        assertThrows(CoolException.class,()->trade.orderInfo(otherCustomer,id(order)));
        assertThrows(CoolException.class,()->trade.simulatePay(otherCustomer,id(order),"SUCCESS"));
        assertThrows(CoolException.class,()->trade.cancel(otherCustomer,id(order)));
        assertThrows(CoolException.class,()->trade.walletInfo(otherCustomer,coupon));
        assertThrows(CoolException.class,()->trade.walletQr(otherCustomer,coupon));
        assertEquals(0L,trade.orders(otherCustomer,1,20,null).getPagination().getTotal());
        assertEquals(0L,trade.wallet(otherCustomer,1,20,null).getPagination().getTotal());
    }
    @Test void quantityStockAndLifetimePerUserLimitAreRecheckedAtPayment() {
        assertThrows(CoolException.class,()->order(0)); assertThrows(CoolException.class,()->order(21));
        var first=order(3); var second=order(3); pay(first);
        assertThrows(CoolException.class,()->trade.simulatePay(customer,id(second),"SUCCESS"));
        var remaining=trade.create(otherCustomer,templateId,5,UUID.randomUUID().toString());
        jdbc.update("UPDATE coupon_template SET issue_limit=4 WHERE id=?",templateId);
        assertThrows(CoolException.class,()->trade.simulatePay(otherCustomer,id(remaining),"SUCCESS"));
        assertEquals(3L,templates.getById(templateId).getIssuedCount());
    }
    @Test void expiredCancelledAndFailedOrdersNeverIssueCoupons() {
        var expired=order(1); jdbc.update("UPDATE purchase_order SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?",id(expired));
        session.clearCache();
        assertEquals("EXPIRED",trade.orderInfo(customer,id(expired)).get("orderStatus"));
        assertThrows(CoolException.class,()->trade.simulatePay(customer,id(expired),"SUCCESS"));
        var cancelled=order(1); trade.cancel(customer,id(cancelled));
        assertEquals("CANCELLED",trade.cancel(customer,id(cancelled)).get("orderStatus"));
        assertThrows(CoolException.class,()->trade.simulatePay(customer,id(cancelled),"SUCCESS"));
        var failed=order(1); assertEquals("FAILED",trade.simulatePay(customer,id(failed),"FAILED").get("orderStatus"));
        assertThrows(CoolException.class,()->trade.simulatePay(customer,id(failed),"SUCCESS"));
        assertEquals(0L,trade.wallet(customer,1,20,null).getPagination().getTotal());
    }
    @Test void ruleChangesInvalidatePendingOrderButIssuedSnapshotSurvivesLaterChanges() {
        var issuedOrder=order(1); Long coupon=pay(issuedOrder); var pending=order(1);
        publications.unpublish(templateId,1);
        var changed=(CouponTemplateEntity)templates.info(templateId); changed.setDeductAmount(new BigDecimal("5"));
        changed.setStoreIds(List.of(otherStore)); templates.update(changed); publish(templateId);
        assertThrows(CoolException.class,()->trade.simulatePay(customer,id(pending),"SUCCESS"));
        String code=(String)trade.walletInfo(customer,coupon).get("code");
        assertEquals(2000L,trade.preview(operator,store,code,5000L).get("discountAmount"));
        assertThrows(CoolException.class,()->trade.preview(operator,otherStore,code,5000L));
    }
    @Test void expiredVoidedAndNotYetValidCouponsAreNotRedeemable() {
        Long coupon=pay(order(1)); String code=(String)trade.walletInfo(customer,coupon).get("code");
        jdbc.update("UPDATE customer_coupon SET valid_to=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",coupon);
        session.clearCache();
        assertEquals("EXPIRED",trade.walletInfo(customer,coupon).get("state"));
        assertThrows(CoolException.class,()->trade.redeem(operator,store,code,5000L));
        jdbc.update("UPDATE customer_coupon SET valid_from=DATE_ADD(NOW(),INTERVAL 1 DAY),valid_to=DATE_ADD(NOW(),INTERVAL 2 DAY) WHERE id=?",coupon);
        session.clearCache();
        assertEquals("NOT_STARTED",trade.walletInfo(customer,coupon).get("state"));
        assertThrows(CoolException.class,()->trade.redeem(operator,store,code,5000L));
        jdbc.update("UPDATE customer_coupon SET status=2 WHERE id=?",coupon);
        session.clearCache();
        assertEquals("VOID",trade.walletInfo(customer,coupon).get("state"));
        assertThrows(CoolException.class,()->trade.redeem(operator,store,code,5000L));
    }
    @Test void freeClaimIsImmediateAndPaidCouponsCannotUseFreeEndpoint() {
        assertThrows(CoolException.class,()->trade.claim(customer,templateId,1,UUID.randomUUID().toString()));
        publications.unpublish(templateId,1); var free=(CouponTemplateEntity)templates.info(templateId);
        free.setSalePrice(BigDecimal.ZERO); templates.update(free); publish(templateId);
        String key=UUID.randomUUID().toString(); var claimed=trade.claim(customer,templateId,1,key);
        assertEquals("PAID",claimed.get("orderStatus")); assertEquals(0L,claimed.get("totalAmount"));
        assertEquals(id(claimed),id(trade.claim(customer,templateId,1,key)));
        assertEquals("CLAIM",trade.wallet(customer,1,20,null).getList().get(0).get("sourceType"));
    }
    @Test void crossMerchantAndInactiveStoreCannotRedeem() {
        Long coupon=pay(order(1)); String code=(String)trade.walletInfo(customer,coupon).get("code");
        Long secondMerchant=merchant("自动其他商家");
        Long otherOperator=accounts.provision("other_"+UUID.randomUUID().toString().substring(0,8),"自动其他管理员","MerchantTest123!",secondMerchant);
        assertThrows(CoolException.class,()->trade.redeem(otherOperator,store,code,5000L));
        jdbc.update("UPDATE store SET status=0 WHERE id=?",store);
        assertThrows(CoolException.class,()->trade.redeem(operator,store,code,5000L));
    }
    @Test void authenticatedAppRoutesReturnPngAndRejectMerchantAndGuestAccess() throws Exception {
        var u=users.getById(customer);
        var auth=(cn.hutool.core.lang.Dict)login.password(u.getPhone(),"CustomerTest123!");
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();
        String body="{\"couponTemplateId\":"+templateId+",\"quantity\":1,\"requestId\":\""+UUID.randomUUID()+"\"}";
        var response=mvc.perform(post("/app/order/create").header("Authorization",auth.getStr("token")).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andReturn();
        Long order=JSONUtil.parseObj(response.getResponse().getContentAsString()).getJSONObject("data").getLong("id");
        Long coupon=((Number)((List<?>)trade.simulatePay(customer,order,"SUCCESS").get("couponIds")).get(0)).longValue();
        mvc.perform(get("/app/coupon/wallet/qrcode").param("id",coupon.toString()).header("Authorization",auth.getStr("token")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/app/order/create").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/merchant/redemption/confirm").header("Authorization",auth.getStr("token")).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void merchantTokenCanPreviewAndConfirmButCannotReadCustomerWallet() throws Exception {
        Long coupon=pay(order(1)); String code=(String)trade.walletInfo(customer,coupon).get("code");
        var input=new com.cool.modules.base.dto.sys.BaseSysLoginDto();
        input.setUsername(username); input.setPassword("MerchantTest123!");
        String captcha=UUID.randomUUID().toString();cache.set("verify:img:"+captcha,"1234",60);
        input.setCaptchaId(captcha); input.setVerifyCode("1234");
        String token=((cn.hutool.core.lang.Dict)accounts.login(input)).getStr("token");
        try {
            MockMvc mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();
            String body=JSONUtil.toJsonStr(Map.of("storeId",store,"code",code,"consumptionAmount",5000));
            String inspectBody=JSONUtil.toJsonStr(Map.of("storeId",store,"code",code));
            mvc.perform(post("/merchant/redemption/inspect").header("Authorization",token).contentType("application/json").content(inspectBody))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.data.type").value("CASH"))
                    .andExpect(jsonPath("$.data.requiresConsumptionAmount").value(true));
            mvc.perform(post("/merchant/redemption/preview").header("Authorization",token).contentType("application/json").content(inspectBody))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(1000)));
            mvc.perform(post("/merchant/redemption/inspect").contentType("application/json").content(inspectBody))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/merchant/redemption/preview").header("Authorization",token).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.confirmed").value(false));
            String prepared=mvc.perform(post("/merchant/consumption/prepare").header("Authorization",token).contentType("application/json")
                    .content(JSONUtil.toJsonStr(Map.of("storeId",store,"code",code,"consumptionAmount",5000,"requestId",UUID.randomUUID().toString()))))
                    .andExpect(jsonPath("$.code").value(1000)).andReturn().getResponse().getContentAsString();
            Long billId=JSONUtil.parseObj(prepared).getJSONObject("data").getLong("id");
            String confirmation=JSONUtil.toJsonStr(Map.of("orderId",billId,"version",1,"receivedAmount",3000));
            mvc.perform(post("/merchant/redemption/confirm").header("Authorization",token).contentType("application/json").content(confirmation))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.status").value("COMPLETED"));
            mvc.perform(post("/merchant/redemption/confirm").header("Authorization",token).contentType("application/json").content(confirmation))
                    .andExpect(jsonPath("$.code").value(1000));
            mvc.perform(get("/admin/coupon/consumptionOrder/info").header("Authorization",token).param("id",billId.toString()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/app/coupon/wallet/info").param("id",coupon.toString()).header("Authorization",token))
                    .andExpect(status().isForbidden());
            assertEquals(1L,trade.redemptions(operator,1,20,store).getPagination().getTotal());
        } finally {accounts.logout(token);}
    }

    @Test void fixedValidityIsKeptInIssuedCoupon() {
        publications.unpublish(templateId,1);var fixed=(CouponTemplateEntity)templates.info(templateId);
        fixed.setValidityType("FIXED");fixed.setValidDays(null);
        Date from=new Date(System.currentTimeMillis()/1000*1000-3600_000L);
        Date to=new Date(from.getTime()+7*24*3600_000L);fixed.setValidFrom(from);fixed.setValidTo(to);
        templates.update(fixed);publish(templateId);
        Long coupon=pay(order(1));var detail=trade.walletInfo(customer,coupon);
        assertEquals(CouponTradeRules.time(from),detail.get("validFrom"));
        assertEquals(CouponTradeRules.time(to),detail.get("validTo"));assertEquals("UNUSED",detail.get("state"));
    }

    @Test void packageCouponIssuesContentSnapshotAndRedeemsWithoutInventedMoneyDiscount() {
        publications.unpublish(templateId,1);var bundle=(CouponTemplateEntity)templates.info(templateId);
        bundle.setType("PACKAGE");bundle.setDeductAmount(null);
        var content=new com.cool.modules.coupon.controller.admin.params.CouponContentParam();
        content.setName("双人套餐");content.setType("GOODS");content.setQuantity(2);bundle.setContentItems(List.of(content));
        templates.update(bundle);publish(templateId);
        Long coupon=pay(order(1));String code=(String)trade.walletInfo(customer,coupon).get("code");
        var receipt=trade.redeem(operator,store,code,null);assertEquals(0L,receipt.get("discountAmount"));
        var items=JSONUtil.parseArray(JSONUtil.toJsonStr(receipt.get("contents")));
        assertEquals("双人套餐",items.getJSONObject(0).getStr("name"));
        assertEquals(2,items.getJSONObject(0).getInt("quantity"));assertEquals("USED",trade.walletInfo(customer,coupon).get("state"));
    }

    @Test void singleStoreStaffCannotCrossStoreOrReadOthersOrUseWorkspace() throws Exception {

        Long managerId=accounts.provision("manager_"+UUID.randomUUID().toString().substring(0,8),"试验店长","Manager123!",merchant);
        jdbc.update("UPDATE base_sys_user SET merchant_role='MANAGER' WHERE id=?",managerId);
        jdbc.update("UPDATE base_sys_user SET merchant_role='STAFF' WHERE id=?",operator);
        jdbc.update("INSERT INTO base_sys_user_store(merchant_id,user_id,store_id) VALUES(?,?,?),(?,?,?)",merchant,operator,store,merchant,managerId,store);
        session.clearCache();
        var credentials = new com.cool.modules.base.dto.sys.BaseSysLoginDto();
        credentials.setUsername(username); credentials.setPassword("MerchantTest123!");
        credentials.setCaptchaId("staff_fixture_"+UUID.randomUUID()); credentials.setVerifyCode("1234");
        cache.set("verify:img:"+credentials.getCaptchaId(),"1234",30);
        String staffToken=JSONUtil.parseObj(accounts.login(credentials)).getStr("token");
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security).build();
        mvc.perform(get("/merchant/staff/me").header("Authorization",staffToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.storeId").value(store));
        mvc.perform(post("/merchant/workspace/store/save").header("Authorization",staffToken)
            .contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        var order=trade.create(customer,templateId,1,"staff_pilot_123");
        var paid=trade.simulatePay(customer,((Number)order.get("id")).longValue(),"SUCCESS");
        Long couponId=((Number)((List<?>)paid.get("couponIds")).get(0)).longValue();
        String code=(String)trade.walletInfo(customer,couponId).get("code");
        mvc.perform(post("/merchant/redemption/inspect").header("Authorization",staffToken)
                .contentType("application/json").content(JSONUtil.toJsonStr(Map.of("code",code))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.data.storeId").value(store));
        assertThrows(CoolException.class,()->trade.preview(operator,otherStore,code,3000L));
        assertThrows(CoolException.class,()->trade.preview(operator,store,"bad",3000L));
        trade.redeem(operator,null,code,3000L);
        assertThrows(CoolException.class,()->trade.redeem(operator,null,code,3000L));
        assertEquals(1,trade.redemptions(operator,1,20,null,"self").getList().size());
        assertThrows(CoolException.class,()->trade.redemptions(operator,1,20,null,"store"));
        assertEquals(0,trade.redemptions(managerId,1,20,null,"self").getList().size());
        assertEquals(1,trade.redemptions(managerId,1,20,null,"store").getList().size());
        jdbc.update("DELETE FROM base_sys_user_store WHERE user_id=?",operator);
        session.clearCache();
        assertThrows(CoolException.class,()->trade.redemptions(operator,1,20,null,"self"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FULL_REDUCTION","DISCOUNT","CASH","PACKAGE","EXPERIENCE","EXCHANGE"})
    void inspectIdentifiesSixTypesWithoutAmountOrChangingCoupon(String type) {
        publications.unpublish(templateId,1);
        var definition=(CouponTemplateEntity)templates.info(templateId);
        definition.setType(type);
        definition.setDescription("识别返回发券时的使用规则");
        if ("FULL_REDUCTION".equals(type)) {
            definition.setMinSpend(new BigDecimal("120")); definition.setDiscountAmount(new BigDecimal("20"));
        }
        if ("DISCOUNT".equals(type)) {
            definition.setDiscountRate(new BigDecimal("8.8")); definition.setMaxDiscount(new BigDecimal("30"));
        }
        boolean requiresAmount=Set.of("FULL_REDUCTION","DISCOUNT","CASH").contains(type);
        if (!requiresAmount) {
            var item=new com.cool.modules.coupon.controller.admin.params.CouponContentParam();
            item.setName("对应商品或服务"); item.setType("GOODS"); item.setQuantity(2);
            definition.setContentItems(List.of(item));
        }
        templates.update(definition); publish(templateId);
        Long coupon=pay(order(1)); String code=(String)trade.walletInfo(customer,coupon).get("code");
        var inspection=trade.inspect(operator,store,code);
        assertEquals(type,inspection.get("type"));
        assertNotNull(inspection.get("typeText"));
        assertEquals(requiresAmount,inspection.get("requiresConsumptionAmount"));
        assertEquals(false,inspection.get("confirmed"));
        assertFalse(inspection.containsKey("discountAmount"),"识别不能冒充金额试算");
        assertFalse(inspection.containsKey("consumptionAmount"));
        var rule=JSONUtil.parseObj(inspection.get("rule"));
        assertEquals(990L,rule.getLong("salePrice"));
        assertEquals("识别返回发券时的使用规则",rule.getStr("description"));
        if ("FULL_REDUCTION".equals(type)) {
            assertEquals(12000L,rule.getLong("minSpend")); assertEquals(2000L,rule.getLong("discountAmount"));
        }
        if ("DISCOUNT".equals(type)) {
            assertEquals(new BigDecimal("8.8"),rule.getBigDecimal("discountRate").stripTrailingZeros());
            assertEquals(3000L,rule.getLong("maxDiscount"));
        }
        if (!requiresAmount) assertEquals("对应商品或服务",JSONUtil.parseArray(JSONUtil.toJsonStr(inspection.get("contents"))).getJSONObject(0).getStr("name"));
        assertEquals("UNUSED",trade.walletInfo(customer,coupon).get("state"));
        assertNull(trade.walletInfo(customer,coupon).get("usedAt"));
        assertEquals(0L,trade.redemptions(operator,1,20,store).getPagination().getTotal());
        if (requiresAmount) {
            assertThrows(CoolException.class,()->trade.preview(operator,store,code,null));
            assertThrows(CoolException.class,()->trade.redeem(operator,store,code,null));
            assertNotNull(trade.preview(operator,store,code,12000L).get("discountAmount"));
        } else assertEquals(0L,trade.preview(operator,store,code,null).get("discountAmount"));
    }

    @Test void inspectUsesIssuedSnapshotAndStillRejectsInvalidStoreAndCouponStates() {
        Long coupon=pay(order(1)); String code=(String)trade.walletInfo(customer,coupon).get("code");
        publications.unpublish(templateId,1);
        var changed=(CouponTemplateEntity)templates.info(templateId);
        changed.setName("模板的新名称"); changed.setDeductAmount(new BigDecimal("99")); templates.update(changed);
        var inspection=trade.inspect(operator,store,code);
        assertEquals("自动购券测试代金券_回滚",inspection.get("couponName"));
        assertEquals(2000L,JSONUtil.parseObj(inspection.get("rule")).getLong("deductAmount"));
        assertThrows(CoolException.class,()->trade.inspect(operator,store,"bad"));
        assertThrows(CoolException.class,()->trade.inspect(operator,store,"f".repeat(64)));
        assertThrows(CoolException.class,()->trade.inspect(operator,otherStore,code));
        Long outsider=accounts.provision("inspect_"+UUID.randomUUID().toString().substring(0,8),"其他商家","MerchantTest123!",merchant("其他商家_回滚"));
        assertThrows(CoolException.class,()->trade.inspect(outsider,store,code));
        jdbc.update("UPDATE store SET status=0 WHERE id=?",store); session.clearCache();
        assertThrows(CoolException.class,()->trade.inspect(operator,store,code));
        jdbc.update("UPDATE store SET status=1 WHERE id=?",store);
        jdbc.update("UPDATE customer_coupon SET valid_to=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",coupon); session.clearCache();
        assertThrows(CoolException.class,()->trade.inspect(operator,store,code));
        jdbc.update("UPDATE customer_coupon SET valid_from=DATE_ADD(NOW(),INTERVAL 1 DAY),valid_to=DATE_ADD(NOW(),INTERVAL 2 DAY) WHERE id=?",coupon); session.clearCache();
        assertThrows(CoolException.class,()->trade.inspect(operator,store,code));
        jdbc.update("UPDATE customer_coupon SET status=2 WHERE id=?",coupon); session.clearCache();
        assertThrows(CoolException.class,()->trade.inspect(operator,store,code));
        jdbc.update("UPDATE customer_coupon SET status=0,valid_from=DATE_SUB(NOW(),INTERVAL 1 DAY),valid_to=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",coupon); session.clearCache();
        trade.redeem(operator,store,code,12000L);
        assertThrows(CoolException.class,()->trade.inspect(operator,store,code));
    }
    ConsumptionOrderService.PrepareInput billInput(String code, Long amount, String key) {
        return new ConsumptionOrderService.PrepareInput(store,code,amount,"测试消费账单",key);
    }
    String issuedCode() { return (String)trade.walletInfo(customer,pay(order(1))).get("code"); }
    @Test void monetaryBillKeepsCouponUnusedUntilOfflineReceiptAndLinksBothDirections() {
        String code=issuedCode(), key=UUID.randomUUID().toString();
        var draft=bills.prepare(operator,billInput(code,5000L,key));
        assertEquals("PENDING",draft.status()); assertEquals(3000L,draft.payableAmount());
        assertEquals(draft.id(),bills.prepare(operator,billInput(code,5000L,key)).id());
        assertEquals("UNUSED",trade.walletInfo(customer,draft.customerCouponId()).get("state"));
        assertThrows(CoolException.class,()->bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(draft.id(),1,2999L)));
        assertEquals("UNUSED",trade.walletInfo(customer,draft.customerCouponId()).get("state"));
        var done=bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(draft.id(),1,3000L));
        assertEquals("COMPLETED",done.status()); assertEquals("OFFLINE",done.paymentMode());
        assertEquals(done.id(),jdbc.queryForObject("SELECT consumption_order_id FROM coupon_redemption WHERE id=?",Long.class,done.redemptionId()));
        assertEquals(done.redemptionId(),bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(draft.id(),1,3000L)).redemptionId());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemption WHERE consumption_order_id=?",Integer.class,done.id()));
        var receipt=trade.redemptions(operator,1,20,store).getList().get(0);
        assertEquals(done.id(),((Number)receipt.get("consumptionOrderId")).longValue());assertEquals(done.orderNo(),receipt.get("orderNo"));
        assertFalse(JSONUtil.toJsonStr(done).contains(code),"订单查询不得泄露可核销原始券码");
        assertThrows(CoolException.class,()->bills.cancel(operator,done.id()));
    }
    @Test void cancellingBillReleasesCouponAndNextBillHasNewNumber() {
        String code=issuedCode();var a=bills.prepare(operator,billInput(code,5000L,UUID.randomUUID().toString()));
        assertThrows(CoolException.class,()->bills.prepare(operator,billInput(code,5000L,UUID.randomUUID().toString())));
        assertEquals("CANCELLED",bills.cancel(operator,a.id()).status());
        assertEquals("CANCELLED",bills.cancel(operator,a.id()).status());
        assertEquals("UNUSED",trade.walletInfo(customer,a.customerCouponId()).get("state"));
        var b=bills.prepare(operator,billInput(code,5000L,UUID.randomUUID().toString()));
        assertNotEquals(a.orderNo(),b.orderNo());
        assertThrows(CoolException.class,()->bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(a.id(),1,3000L)));
    }
    @Test void changingBillKeepsNumberAndRejectsStaleReceiptConfirmation() {
        String code=issuedCode();var a=bills.prepare(operator,billInput(code,5000L,UUID.randomUUID().toString()));
        var b=bills.revise(operator,new ConsumptionOrderService.ReviseInput(a.id(),1,6000L,"修改金额"));
        assertEquals(a.orderNo(),b.orderNo());assertEquals(4000L,b.payableAmount());assertEquals(2,b.version());
        assertThrows(CoolException.class,()->bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(a.id(),1,3000L)));
        var done=bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(b.id(),b.version(),4000L));
        assertEquals(6000L,done.consumptionAmount());
    }
    @Test void insufficientThresholdRejectsBillWithoutConsumingCoupon() {
        Long coupon=pay(order(1));String code=(String)trade.walletInfo(customer,coupon).get("code");
        jdbc.update("UPDATE customer_coupon SET rule_snapshot=JSON_SET(rule_snapshot,'$.minSpend',120) WHERE id=?",coupon);session.clearCache();
        assertThrows(CoolException.class,()->bills.prepare(operator,billInput(code,10000L,UUID.randomUUID().toString())));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consumption_order WHERE operator_id=?",Integer.class,operator));
        assertEquals("UNUSED",trade.walletInfo(customer,coupon).get("state"));
    }
    @Test void zeroDifferenceCompletesWithoutClaimingOfflinePayment() {
        var a=bills.prepare(operator,billInput(issuedCode(),1000L,UUID.randomUUID().toString()));
        assertEquals(0L,a.payableAmount());
        var done=bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(a.id(),a.version(),0L));
        assertEquals("NONE",done.paymentMode());assertEquals("COMPLETED",done.status());
    }
    @Test void expiredCouponAtReceiptRollsBackCompletionAndLeavesDraftCancellable() {
        var a=bills.prepare(operator,billInput(issuedCode(),5000L,UUID.randomUUID().toString()));
        jdbc.update("UPDATE customer_coupon SET valid_to=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",a.customerCouponId());session.clearCache();
        assertThrows(CoolException.class,()->bills.confirm(operator,new ConsumptionOrderService.ConfirmInput(a.id(),1,3000L)));
        assertEquals("PENDING",bills.info(operator,a.id()).status());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemption WHERE consumption_order_id=?",Integer.class,a.id()));
        assertEquals("CANCELLED",bills.cancel(operator,a.id()).status());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PACKAGE","EXPERIENCE","EXCHANGE"})
    void contentVoucherAtomicallyCreatesCompletedBillWithSnapshotAndRetriesSafely(String type) {
        publications.unpublish(templateId,1);var c=(CouponTemplateEntity)templates.info(templateId);c.setType(type);
        var item=new com.cool.modules.coupon.controller.admin.params.CouponContentParam();item.setName("两人套餐或体验内容");item.setType("GOODS");item.setQuantity(2);
        c.setContentItems(List.of(item));templates.update(c);publish(templateId);
        String code=issuedCode(),key=UUID.randomUUID().toString();
        var done=bills.redeemContent(operator,billInput(code,null,key));
        assertEquals("COMPLETED",done.status());assertEquals(type,done.couponType());assertNull(done.consumptionAmount());
        assertEquals(0L,done.payableAmount());assertEquals("NONE",done.paymentMode());
        assertTrue(done.couponSnapshot().toString().contains("两人套餐或体验内容"));
        assertEquals(done.id(),bills.redeemContent(operator,billInput(code,null,key)).id());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consumption_order WHERE operator_id=?",Integer.class,operator));
        assertEquals(done.id(),jdbc.queryForObject("SELECT consumption_order_id FROM coupon_redemption WHERE id=?",Long.class,done.redemptionId()));
    }
    @Test void requestKeyCannotBeReusedForDifferentAmountAndMoneyVoucherCannotSkipReceipt() {
        String code=issuedCode(),key=UUID.randomUUID().toString();bills.prepare(operator,billInput(code,5000L,key));
        assertThrows(CoolException.class,()->bills.prepare(operator,billInput(code,6000L,key)));
        assertThrows(CoolException.class,()->bills.redeemContent(operator,billInput(code,null,UUID.randomUUID().toString())));
    }
    @Test void billIsolationAndStoreRolesApplyToDetailListCancelAndReceipt() {
        var a=bills.prepare(operator,billInput(issuedCode(),5000L,UUID.randomUUID().toString()));
        Long colleague=accounts.provision("bill_"+UUID.randomUUID().toString().substring(0,8),"账单测试员工","MerchantTest123!",merchant);
        jdbc.update("UPDATE base_sys_user SET merchant_role='STAFF' WHERE id=?",colleague);
        jdbc.update("INSERT INTO base_sys_user_store(user_id,merchant_id,store_id) VALUES(?,?,?)",colleague,merchant,store);session.clearCache();
        assertEquals(0L,bills.page(colleague,new ConsumptionOrderService.PageInput(1,20,null,null,null,null,null)).getPagination().getTotal());
        assertThrows(CoolException.class,()->bills.info(colleague,a.id()));
        assertThrows(CoolException.class,()->bills.cancel(colleague,a.id()));
        assertThrows(CoolException.class,()->bills.confirm(colleague,new ConsumptionOrderService.ConfirmInput(a.id(),1,3000L)));
        jdbc.update("UPDATE base_sys_user SET merchant_role='MANAGER' WHERE id=?",colleague);session.clearCache();
        assertEquals(a.id(),bills.info(colleague,a.id()).id());
        assertEquals(1L,bills.page(colleague,new ConsumptionOrderService.PageInput(1,20,null,store,null,a.orderNo(),null)).getPagination().getTotal());
        jdbc.update("UPDATE base_sys_user_store SET store_id=? WHERE user_id=?",otherStore,colleague);session.clearCache();
        assertThrows(CoolException.class,()->bills.info(colleague,a.id()));
        assertEquals(0L,bills.page(colleague,new ConsumptionOrderService.PageInput(1,20,null,store,null,null,null)).getPagination().getTotal());
    }
    @Test void newBillHttpRoutesAndLegacyConfirmCannotBypassOrder() throws Exception {
        String code=issuedCode();var mvc=MockMvcBuilders.webAppContextSetup(web).build();
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(billInput(code,5000L,UUID.randomUUID().toString()));
        String body=mvc.perform(post("/merchant/consumption/prepare").requestAttr("merchantUserId",operator)
            .contentType("application/json").content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.data.payableAmount").value(3000)).andReturn().getResponse().getContentAsString();
        Long id=JSONUtil.parseObj(body).getJSONObject("data").getLong("id");
        mvc.perform(post("/merchant/redemption/confirm").requestAttr("merchantUserId",operator).contentType("application/json")
            .content(JSONUtil.toJsonStr(Map.of("code",code,"storeId",store,"consumptionAmount",5000))))
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(1000)));
        mvc.perform(post("/merchant/redemption/confirm").requestAttr("merchantUserId",operator).contentType("application/json")
            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new ConsumptionOrderService.ConfirmInput(id,1,3000L))))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get("/merchant/consumption/info").param("id",id.toString()).requestAttr("merchantUserId",operator))
            .andExpect(jsonPath("$.data.redemptionId").isNumber()).andExpect(jsonPath("$.data.couponCode").doesNotExist());
    }

    @Test void platformConsumptionQueriesRequireExplicitReadPermissions() throws Exception {
        var draft=bills.prepare(operator,billInput(issuedCode(),5000L,UUID.randomUUID().toString()));
        var mvc=MockMvcBuilders.webAppContextSetup(web).build();
        var principal=new com.cool.core.security.jwt.JwtUser(1L,"bill_viewer","",List.of(),true);
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        mvc.perform(get("/admin/coupon/consumptionOrder/info").param("id",draft.id().toString()))
            .andExpect(jsonPath("$.code").value(403));
        principal.setPerms(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("coupon:consumptionOrder:info"),
            new org.springframework.security.core.authority.SimpleGrantedAuthority("coupon:consumptionOrder:page")));
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        mvc.perform(get("/admin/coupon/consumptionOrder/info").param("id",draft.id().toString()))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.orderNo").value(draft.orderNo()));
        mvc.perform(post("/admin/coupon/consumptionOrder/page").contentType("application/json")
            .content(JSONUtil.toJsonStr(Map.of("merchantId",merchant,"customerCouponId",draft.customerCouponId()))))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.pagination.total").value(1));
    }

}
