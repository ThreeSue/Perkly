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
            mvc.perform(post("/merchant/redemption/preview").header("Authorization",token).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.confirmed").value(false));
            mvc.perform(post("/merchant/redemption/confirm").header("Authorization",token).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.confirmed").value(true));
            mvc.perform(post("/merchant/redemption/confirm").header("Authorization",token).contentType("application/json").content(body))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(1000)));
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
}
