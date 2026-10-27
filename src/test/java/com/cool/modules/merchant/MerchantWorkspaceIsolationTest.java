package com.cool.modules.merchant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.base.security.JwtUserDetailsServiceImpl;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.merchant.service.MerchantWorkspaceService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在事务回滚的真实数据库中验证双商家隔离与不同端令牌访问边界
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:05:49
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=merchant-test-unused", "sms-auth.access-key-secret=merchant-test-unused"
})
@ActiveProfiles("local")
@Transactional
class MerchantWorkspaceIsolationTest {
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    JdbcTemplate jdbc;
    @Autowired MerchantAccountService accounts;
    @Autowired MerchantWorkspaceService workspace;
    @Autowired JwtTokenUtil tokens;
    @Autowired CoolCache cache;
    @Autowired WebApplicationContext web;
    @Autowired JwtUserDetailsServiceImpl platformUsers;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.modules.user.util.UserSmsUtil sms;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.core.file.strategy.LocalFileUploadStrategy imageStorage;
    Long merchantA, merchantB, userA, storeA, storeB, categoryA, categoryB;
    String username, access, refresh;
    MockMvc mvc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(sqlSession.getConnection(), true));
        Long category = jdbc.queryForObject("SELECT id FROM merchant_category LIMIT 1", Long.class);
        merchantA = insert("INSERT INTO merchant_info(name,category_id,contact_name,contact_phone,status) VALUES(?,?,?,?,1)", "隔离测试甲", category, "测试联系人", "13800000001");
        merchantB = insert("INSERT INTO merchant_info(name,category_id,contact_name,contact_phone,status) VALUES(?,?,?,?,1)", "隔离测试乙", category, "测试联系人", "13800000002");
        categoryA = jdbc.queryForObject("SELECT id FROM store_type WHERE name='直营店'", Long.class);
        categoryB = jdbc.queryForObject("SELECT id FROM store_type WHERE name='加盟店'", Long.class);
        storeA = insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,?,?,?,1)", merchantA,categoryA,"甲店","甲地址","13800000001");
        storeB = insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,?,?,?,1)", merchantB,categoryB,"乙店","乙地址","13800000002");
        username = "mtest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        userA = accounts.provision(username,"隔离测试管理员","MerchantTest123!",merchantA);
        Dict session = (Dict) accounts.login(loginInput());
        access = session.getStr("token"); refresh = session.getStr("refreshToken");
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(security, new com.cool.core.request.RequestParamsFilter()).build();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    BaseSysLoginDto loginInput() {
        var input = new BaseSysLoginDto(); input.setUsername(username); input.setPassword("MerchantTest123!");
        String captcha = UUID.randomUUID().toString(); cache.set("verify:img:" + captcha, "1234", 60);
        input.setCaptchaId(captcha); input.setVerifyCode("1234"); return input;
    }

    Long insert(String sql, Object... args) {
        var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i=0; i<args.length; i++) statement.setObject(i+1,args[i]); return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    CouponTemplateEntity coupon() {
        var c = new CouponTemplateEntity(); c.setName("甲商家测试券"); c.setType("CASH");
        c.setDeductAmount(BigDecimal.TEN); c.setValidityType("AFTER_ISSUE"); c.setValidDays(30);
        c.setStatus(0); c.setMerchantId(merchantB); return c;
    }

    org.springframework.mock.web.MockMultipartFile image(String name) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);
        return new org.springframework.mock.web.MockMultipartFile("file",name,"image/png",output.toByteArray());
    }
    @Test void ownerUploadsValidatedImageWithoutChangingBusinessCover() throws Exception {
        org.mockito.Mockito.when(imageStorage.upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn("http://localhost:8001/uploads/test-cover.png");
        mvc.perform(multipart("/merchant/workspace/image/upload").file(image("cover.png")).header("Authorization",access))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.data").value("http://localhost:8001/uploads/test-cover.png"));
        assertNull(web.getBean(com.cool.modules.merchant.mapper.MerchantStoreMapper.class).selectOneById(storeA).getCover());
        org.mockito.Mockito.verify(imageStorage).upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
    }
    @Test void rejectsFakeOversizedAndMismatchedImagesBeforeStorage() throws Exception {
        var invalid = List.of(
            new org.springframework.mock.web.MockMultipartFile("file","bad.png","image/png","not an image".getBytes()),
            new org.springframework.mock.web.MockMultipartFile("file","empty.png","image/png",new byte[0]),
            new org.springframework.mock.web.MockMultipartFile("file","big.jpg","image/jpeg",new byte[5*1024*1024+1]),
            image("wrong.jpg"), image("script.svg")
        );
        for (var file : invalid) {
            mvc.perform(multipart("/merchant/workspace/image/upload").file(file).header("Authorization",access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(1000)));
        }
        org.mockito.Mockito.verifyNoInteractions(imageStorage);
    }
    @Test void imageUploadRejectsAnonymousAndOtherClientIdentities() throws Exception {
        mvc.perform(multipart("/merchant/workspace/image/upload").file(image("cover.png"))).andExpect(status().isUnauthorized());
        for (String type : List.of("APP","ADMIN")) {
            String other=tokens.generateToken(Dict.create().set("userType",type).set("userId",userA).set("username",username));
            mvc.perform(multipart("/merchant/workspace/image/upload").file(image("cover.png")).header("Authorization",other))
                .andExpect(status().isUnauthorized());
        }
        org.mockito.Mockito.verifyNoInteractions(imageStorage);
    }
    @Test void storeStaffCannotUseOwnerImageUpload() throws Exception {
        var mapper=web.getBean(com.cool.modules.base.mapper.sys.BaseSysUserMapper.class);
        var user=mapper.selectOneById(userA); user.setMerchantRole("MANAGER"); mapper.update(user);
        Dict session=(Dict)accounts.login(loginInput());
        try {
            mvc.perform(multipart("/merchant/workspace/image/upload").file(image("cover.png")).header("Authorization",session.getStr("token")))
                .andExpect(status().isForbidden());
            org.mockito.Mockito.verifyNoInteractions(imageStorage);
        } finally { accounts.logout(session.getStr("token")); }
    }
    @Test void loginUsesBcryptAndOperatorLoginRejectsMerchantAccount() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT password FROM base_sys_user WHERE id=?",String.class,userA).startsWith("$2"));
        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class, () -> platformUsers.loadUserByUsername(username));
        var input = loginInput();
        mvc.perform(post("/merchant/auth/login").contentType("application/json").content(cn.hutool.json.JSONUtil.toJsonStr(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(get("/merchant/workspace/me").header("Authorization",access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.merchantName").value("隔离测试甲"));
    }

    @Test void rejectsWrongClientTokensAndRefreshTokensOnBusinessRoutes() throws Exception {
        mvc.perform(post("/admin/merchant/store/page").header("Authorization",access).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/merchant/workspace/me").header("Authorization",refresh)).andExpect(status().isUnauthorized());
        for (String type : List.of("APP","ADMIN")) {
            String other = tokens.generateToken(Dict.create().set("userType",type).set("userId",userA).set("username",username));
            mvc.perform(get("/merchant/workspace/me").header("Authorization",other)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/merchant/workspace/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/merchant/workspace/me").header("Authorization","invalid.jwt")).andExpect(status().isUnauthorized());
    }

    @Test void listsIgnoreForgedMerchantAndDetailsRejectForeignStore() throws Exception {
        mvc.perform(post("/merchant/workspace/store/page").header("Authorization",access).contentType("application/json")
                .content("{\"merchantId\":" + merchantB + ",\"page\":1,\"size\":100}"))
                .andExpect(jsonPath("$.data.records.length()").value(1)).andExpect(jsonPath("$.data.records[0].name").value("甲店"));
        mvc.perform(get("/merchant/workspace/store/info").param("id",storeB.toString()).header("Authorization",access))
                .andExpect(jsonPath("$.code").value(403));
        assertThrows(CoolException.class, () -> workspace.storeDelete(userA, List.of(storeA,storeB)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE id=?",Integer.class,storeA));
    }

    @Test void storeWritesForceOwnMerchantAcceptSharedTypesAndRejectForeignUpdate() {
        var input = new MerchantStoreEntity(); input.setMerchantId(merchantB); input.setCategoryId(categoryA);
        input.setName("新增甲店"); input.setAddress("测试地址"); input.setPhone("13800000001"); input.setStatus(1);
        Long id = (Long) workspace.storeSave(userA,input);
        assertEquals(merchantA,jdbc.queryForObject("SELECT merchant_id FROM store WHERE id=?",Long.class,id));
        input.setId(storeB); assertThrows(CoolException.class, () -> workspace.storeSave(userA,input));
        input.setId(null); input.setCategoryId(categoryB);
        assertNotNull(workspace.storeSave(userA,input));
        input.setId(null); input.setCategoryId(-1L);
        assertThrows(CoolException.class, () -> workspace.storeSave(userA,input));
    }

    @Autowired com.cool.modules.merchant.service.StoreTypeInitializer storeTypes;

    @Autowired com.cool.modules.merchant.service.MerchantStoreCategoryService publicTypes;

    @Test void publicTypeOptionsReflectEnabledPlatformTypes() throws Exception {
        var expected = publicTypes.list(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getStatus, 1)
                .orderBy(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getSort, true));
        String body = mvc.perform(get("/merchant/workspace/category/options").header("Authorization", access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var actual = cn.hutool.json.JSONUtil.parseObj(body).getJSONArray("data")
                .toList(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity.class);
        assertEquals(expected.stream().map(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getId).toList(),
                actual.stream().map(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getId).toList());
        assertEquals(expected.stream().map(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getName).toList(),
                actual.stream().map(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getName).toList());
    }

    @Test void randomMigrationIsBackedUpValidAndRunsOnlyOnce() {
        // 模拟首次迁移，所有修改均随本测试事务回滚。
        jdbc.update("DELETE FROM base_sys_conf WHERE c_key=?", storeTypes.MIGRATION_KEY);
        jdbc.update("DELETE FROM store_type");
        sqlSession.clearCache();
        var before = jdbc.queryForList("SELECT id,category_id FROM store ORDER BY id");
        storeTypes.run(null);
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM store_type", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM store s LEFT JOIN store_type t ON s.category_id=t.id WHERE t.id IS NULL", Integer.class));
        var backup = cn.hutool.json.JSONUtil.parseObj(jdbc.queryForObject(
                "SELECT c_value FROM base_sys_conf WHERE c_key=?", String.class, storeTypes.MIGRATION_KEY));
        assertEquals(before.size(), backup.getJSONArray("storeAssociations").size());
        var after = jdbc.queryForList("SELECT id,category_id FROM store ORDER BY id");
        storeTypes.run(null);
        assertEquals(after, jdbc.queryForList("SELECT id,category_id FROM store ORDER BY id"));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM store_type", Integer.class));
    }

    @Test void couponsForceOwnMerchantAndRejectForeignStoresAndPublication() {
        var c = coupon(); Long id = (Long) workspace.couponSave(userA,c);
        assertEquals(merchantA,jdbc.queryForObject("SELECT merchant_id FROM coupon_template WHERE id=?",Long.class,id));
        Long b = insert("INSERT INTO coupon_template(merchant_id,name,type,validity_type,valid_days,status) VALUES(?,?,'CASH','AFTER_ISSUE',30,0)",merchantB,"乙券");
        assertThrows(CoolException.class, () -> workspace.couponInfo(userA,b));
        assertThrows(CoolException.class, () -> workspace.publication(userA,b,1,"publish"));
        assertThrows(CoolException.class, () -> workspace.couponDelete(userA,List.of(id,b)));
        c = coupon(); c.setScopeType(1); c.setStoreIds(List.of(storeB));
        final var crossStore = c;
        assertThrows(CoolException.class, () -> workspace.couponSave(userA,crossStore));
        assertThrows(CoolException.class, () -> workspace.publication(userA,id,1,"audit"));
    }

    @Test void ordersAndItemsStayInsideMerchantAndHaveNoWriteEndpoint() throws Exception {
        Long a = insert("INSERT INTO purchase_order(order_no,merchant_id,customer_id,channel,total_amount) VALUES(?,?,1,'PLATFORM',10)",UUID.randomUUID().toString(),merchantA);
        Long b = insert("INSERT INTO purchase_order(order_no,merchant_id,customer_id,channel,total_amount) VALUES(?,?,1,'PLATFORM',20)",UUID.randomUUID().toString(),merchantB);
        mvc.perform(post("/merchant/workspace/order/page").header("Authorization",access).contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.data.records.length()").value(1)).andExpect(jsonPath("$.data.records[0].id").value(a.intValue()));
        assertThrows(CoolException.class, () -> workspace.orderInfo(userA,b));
        mvc.perform(post("/merchant/workspace/order/delete").header("Authorization",access).contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(1001));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE id=?", Integer.class, a));
    }

    @Test void statusAssociationRoleAndPasswordChangesInvalidateExistingSessions() {
        jdbc.update("UPDATE merchant_info SET status=0 WHERE id=?",merchantA);
        sqlSession.clearCache();
        assertThrows(CoolException.class, () -> accounts.validate(access,false));
        jdbc.update("UPDATE merchant_info SET status=1 WHERE id=?",merchantA);
        jdbc.update("UPDATE base_sys_user SET merchant_role='STAFF' WHERE id=?",userA);
        sqlSession.clearCache();
        assertThrows(CoolException.class, () -> accounts.validate(access,false));
        jdbc.update("UPDATE base_sys_user SET merchant_role='OWNER', merchant_id=? WHERE id=?",merchantB,userA);
        sqlSession.clearCache();
        assertThrows(CoolException.class, () -> accounts.validate(access,false));
        jdbc.update("UPDATE base_sys_user SET merchant_id=?,password_v=password_v+1 WHERE id=?",merchantA,userA);
        sqlSession.clearCache();
        assertThrows(CoolException.class, () -> accounts.validate(access,false));
        assertThrows(CoolException.class, () -> accounts.refresh(refresh));
    }

    @Test void refreshRotatesSessionAndLogoutRevokesBothTokens() {
        Dict next = (Dict)accounts.refresh(refresh);
        sqlSession.clearCache();
        assertThrows(CoolException.class, () -> accounts.validate(access,false));
        assertThrows(CoolException.class, () -> accounts.refresh(refresh));
        accounts.logout(next.getStr("token"));
        assertThrows(CoolException.class, () -> accounts.validate(next.getStr("token"),false));
        assertThrows(CoolException.class, () -> accounts.refresh(next.getStr("refreshToken")));
    }
    @Test void passwordChangeChecksOldPasswordAndRevokesBothTokens() throws Exception {
        assertThrows(CoolException.class, () -> accounts.changePassword(userA, "wrong", "NewPass123"));
        assertThrows(CoolException.class, () -> accounts.changePassword(userA, "MerchantTest123!", "123"));
        assertEquals(userA, accounts.validate(access, false).getId());
        mvc.perform(post("/merchant/auth/changePassword").header("Authorization", access)
                .contentType("application/json").content("{\"oldPassword\":\"MerchantTest123!\",\"newPassword\":\"Changed123\"}"))
                .andExpect(jsonPath("$.code").value(1000));
        assertThrows(CoolException.class, () -> accounts.validate(access, false));
        assertThrows(CoolException.class, () -> accounts.refresh(refresh));
        assertThrows(CoolException.class, () -> accounts.login(loginInput()));
        var input = loginInput(); input.setPassword("Changed123");
        assertNotNull(((Dict) accounts.login(input)).getStr("token"));
    }

    @Test void operatorResetUsesDefaultAndMerchantCannotReset() throws Exception {
        mvc.perform(post("/admin/merchant/account/resetPassword").header("Authorization", access)
                .contentType("application/json").content("{\"merchantId\":" + merchantA + "}"))
                .andExpect(status().isForbidden());
        assertEquals(userA, accounts.validate(access, false).getId());
        assertEquals(1, accounts.resetMerchantPassword(merchantA));
        assertThrows(CoolException.class, () -> accounts.validate(access, false));
        assertThrows(CoolException.class, () -> accounts.refresh(refresh));
        var input = loginInput(); input.setPassword("123456");
        assertEquals(merchantA, accounts.validate(((Dict) accounts.login(input)).getStr("token"), false).getMerchantId());
        assertThrows(CoolException.class, () -> accounts.resetMerchantPassword(-1L));
    }

    com.cool.modules.approval.entity.req.MerchantRegistrationReq registrationInput() {
        var input = new com.cool.modules.approval.entity.req.MerchantRegistrationReq();
        input.setUsername("reg_" + UUID.randomUUID().toString().replace("-", "").substring(0,15));
        input.setPassword("123456"); input.setMerchantName("注册测试" + input.getUsername());
        input.setCategoryId(jdbc.queryForObject("SELECT id FROM merchant_category WHERE status=1 LIMIT 1",Long.class));
        input.setContactName("入驻联系人");input.setContactPhone("13800138000");input.setAddress("测试地址");
        input.setCaptchaId(UUID.randomUUID().toString());input.setVerifyCode("1234");
        cache.set("verify:img:"+input.getCaptchaId(),"1234",60);return input;
    }

    @Test void closedRegistrationPreservesExistingHistoricalApplicationLoginAndReview() throws Exception {
        mvc.perform(get("/merchant/auth/registrationCategories")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        var input = registrationInput();
        var json = cn.hutool.json.JSONUtil.parseObj(cn.hutool.json.JSONUtil.toJsonStr(input));
        json.set("status",2);json.set("merchantId",merchantA);json.set("merchantRole","ADMIN");
        mvc.perform(post("/merchant/auth/register").contentType("application/json").content(json.toString()))
                .andExpect(jsonPath("$.code").value(410));
        // 旧申请由测试夹具模拟，公开入口不能再创建它。
        web.getBean(com.cool.modules.approval.service.MerchantRegistrationService.class).submit(input,false);
        var applicationMapper=web.getBean(com.cool.modules.approval.mapper.MerchantApplicationMapper.class);
        var application=applicationMapper.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getUsername,input.getUsername())
                .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getCurrentFlag,1));
        assertEquals(1,application.getStatus());assertNull(application.getMerchantId());
        var userMapper=web.getBean(com.cool.modules.base.mapper.sys.BaseSysUserMapper.class);
        assertEquals(0,userMapper.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create().eq(com.cool.modules.base.entity.sys.BaseSysUserEntity::getUsername,input.getUsername())));
        var login=loginInput();login.setUsername(input.getUsername());login.setPassword("123456");
        var limited=(Dict)accounts.login(login);
        String limitedToken=limited.getStr("token");
        assertThrows(CoolException.class,()->accounts.validate(limitedToken,false));
        mvc.perform(get("/merchant/workspace/me").header("Authorization",limitedToken))
                .andExpect(jsonPath("$.data.onboardingLocked").value(true))
                .andExpect(jsonPath("$.data.username").value(input.getUsername()));
        mvc.perform(get("/merchant/auth/onboarding").header("Authorization",limitedToken))
                .andExpect(jsonPath("$.data.status").value(1)).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        for(String path:List.of("/merchant/workspace/store/page","/merchant/workspace/coupon/page","/merchant/workspace/order/page","/merchant/workspace/store/save","/merchant/workspace/coupon/save","/merchant/redemption/confirm","/admin/merchant/info/page"))
            mvc.perform(post(path).header("Authorization",limitedToken).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        mvc.perform(get("/merchant/workspace/profile").header("Authorization",limitedToken)).andExpect(status().isForbidden());
        mvc.perform(get("/merchant/auth/onboarding").header("Authorization",limited.getStr("refreshToken"))).andExpect(status().isUnauthorized());
        var renewed=(Dict)accounts.refresh(limited.getStr("refreshToken"));
        assertThrows(CoolException.class,()->accounts.validateApplicant(limitedToken,false));
        assertThrows(CoolException.class,()->accounts.refresh(limited.getStr("refreshToken")));
        web.getBean(com.cool.modules.approval.service.ApprovalCenterService.class).review("MERCHANT",application.getId(),1,true,"");
        var next=loginInput();next.setUsername(input.getUsername());next.setPassword("123456");
        var stillLimited=(Dict)accounts.refresh(renewed.getStr("refreshToken"));
        assertThrows(CoolException.class,()->accounts.validate(stillLimited.getStr("token"),false));
        var onboarding=web.getBean(com.cool.modules.approval.service.GuestMerchantOnboardingService.class);
        String ticket=onboarding.verify(input.getContactPhone(),"123456");
        String code=onboarding.info(ticket,application.getApplicationNo()).getActivationCode();
        onboarding.activate(new com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.Activate(ticket,code,input.getUsername(),"MerchantTest123!"));
        cache.del("onboarding:"+cn.hutool.crypto.SecureUtil.sha256(ticket));
        next.setPassword("MerchantTest123!");
        var upgraded=(Dict)accounts.login(next);
        assertNotEquals(merchantA,accounts.validate(upgraded.getStr("token"),false).getMerchantId());
        assertThrows(CoolException.class,()->accounts.refresh(renewed.getStr("refreshToken")));
        cache.set("verify:img:"+next.getCaptchaId(),"1234",60);
        var session=(Dict)accounts.login(next);
        assertNotEquals(merchantA,accounts.validate(session.getStr("token"),false).getMerchantId());
    }

    @Test void rejectedRegistrationCanResubmitUsingOriginalPassword() throws Exception {
        var input=registrationInput();
        var registration=web.getBean(com.cool.modules.approval.service.MerchantRegistrationService.class);
        var applications=web.getBean(com.cool.modules.approval.mapper.MerchantApplicationMapper.class);
        var review=web.getBean(com.cool.modules.approval.service.ApprovalCenterService.class);
        registration.submit(input,false);
        var application=applications.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getUsername,input.getUsername())
                .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getCurrentFlag,1));
        assertThrows(CoolException.class,()->registration.submit(input,false));
        var pendingLogin=loginInput();pendingLogin.setUsername(input.getUsername());pendingLogin.setPassword("123456");
        var limited=(Dict)accounts.login(pendingLogin);
        review.review("MERCHANT",application.getId(),1,false,"请补充地址");
        mvc.perform(get("/merchant/auth/onboarding").header("Authorization",limited.getStr("token")))
                .andExpect(jsonPath("$.data.status").value(3)).andExpect(jsonPath("$.data.reason").value("请补充地址"));
        input.setPassword("wrong123"); assertThrows(CoolException.class,()->registration.submit(input,true));
        input.setPassword("123456");input.setAddress("补充后的地址");
        var next=registration.submit(input,true);assertEquals(2,next.getSubmissionNo());
        assertEquals(0,applications.selectOneById(application.getId()).getCurrentFlag());
        mvc.perform(get("/merchant/auth/onboarding").header("Authorization",limited.getStr("token")))
                .andExpect(jsonPath("$.data.status").value(1)).andExpect(jsonPath("$.data.reason").value(""));
        accounts.logout(limited.getStr("token"));
        assertThrows(CoolException.class,()->accounts.refresh(limited.getStr("refreshToken")));
    }

}
