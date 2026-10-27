package com.cool.modules.approval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.hutool.json.JSONUtil;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.modules.approval.entity.*;
import com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.*;
import com.cool.modules.approval.mapper.*;
import com.cool.modules.approval.service.*;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.util.UserSmsUtil;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 事务回滚验证游客申请审批发码手机号隔离及一次性商家开通
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:08:04
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={
        "sms-auth.access-key-id=guest-test-unused","sms-auth.access-key-secret=guest-test-unused",
        "spring.quartz.auto-startup=false","logging.level.com.cool=ERROR",
        "logging.level.com.cool.core.config.cache.CaffeineConfig=OFF"})
@ActiveProfiles("local")
@Transactional
class GuestMerchantOnboardingIntegrationTest {
    @Autowired GuestMerchantOnboardingService onboarding;
    @Autowired ApprovalCenterService review;
    @Autowired MerchantApplicationMapper applications;
    @Autowired MerchantActivationMapper credentials;
    @Autowired MerchantCategoryMapper categories;
    @Autowired BaseSysUserMapper accounts;
    @Autowired UserInfoMapper ordinaryUsers;
    @Autowired MerchantInfoMapper merchants;
    @Autowired MerchantAccountService merchantLogin;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired ApprovalMapper auditRows;
    @Autowired CoolCache cache;
    @Autowired WebApplicationContext web;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @MockBean UserSmsUtil sms;
    String phone,token,key;Long categoryId;MockMvc mvc;
    final List<String> grants=new ArrayList<>();
    @BeforeEach void setup() {
        phone="197"+String.format("%08d",new Random().nextInt(100_000_000));
        token=grant(phone);key=UUID.randomUUID().toString();
        var category=new MerchantCategoryEntity();category.setName("游客入驻测试分类");category.setSort(0);
        category.setShowOnFront(1);category.setStatus(1);categories.insertSelective(category);categoryId=category.getId();
        mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();
    }
    @AfterEach void cleanup() {grants.forEach(t->cache.del("onboarding:"+cn.hutool.crypto.SecureUtil.sha256(t)));org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    String grant(String number) {String t=onboarding.verify(number,"123456");grants.add(t);return t;}
    Submit input(String no,String requestId) {return new Submit(token,requestId,no,"游客测试商家",categoryId,"测试联系人","测试说明");}
    MerchantApplicationEntity application(String no) {return applications.selectOneByQuery(QueryWrapper.create().eq(MerchantApplicationEntity::getApplicationNo,no).eq(MerchantApplicationEntity::getCurrentFlag,1));}
    MerchantActivationEntity credential(String no) {return credentials.selectOneByQuery(QueryWrapper.create().eq(MerchantActivationEntity::getApplicationNo,no));}
    String approved() {String no=onboarding.submit(input(null,key),false).getApplicationNo();var a=application(no);review.review("MERCHANT",a.getId(),a.getSubmissionNo(),true,"资料完整");return no;}
    Activate activate(String code,String username) {return new Activate(token,code,username,"MerchantTest123!");}
    String username() {return "guesttest_"+UUID.randomUUID().toString().replace("-","").substring(0,15);}

    @Test void publicGuestSubmitReviewAndActivationDoNotCreateOrdinaryUser() throws Exception {
        long before=ordinaryUsers.selectCountByQuery(QueryWrapper.create());
        var response=mvc.perform(post("/app/merchant/onboarding/submit").contentType("application/json").content(json.writeValueAsString(input(null,key))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andReturn();
        String no=JSONUtil.parseObj(response.getResponse().getContentAsString()).getJSONObject("data").getStr("applicationNo");
        var a=application(no);review.review("MERCHANT",a.getId(),1,true,"同意");
        assertNull(application(no).getMerchantId());assertEquals("PENDING_ACTIVATION",review.info("MERCHANT",a.getId()).getItem().getActivationState());
        var info=onboarding.info(token,no);assertEquals("APPROVED",info.getState());assertTrue(info.getActivationCode().matches("MK-[A-F0-9]{32}"));
        assertFalse(info.getSnapshot().containsKey("licenseUrl"),"当前申请无需营业执照资料");
        assertFalse(info.getSnapshot().containsKey("address"),"申请商家不收集门店地址");
        assertFalse(credential(no).getCodeCipher().contains(info.getActivationCode()));
        String username=username();
        mvc.perform(post("/merchant/auth/activate").contentType("application/json").content(json.writeValueAsString(activate(info.getActivationCode(),username))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.state").value("ACTIVATED"));
        assertEquals(before,ordinaryUsers.selectCountByQuery(QueryWrapper.create()));
        var account=accounts.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,username));
        assertEquals("OWNER",account.getMerchantRole());assertEquals(application(no).getMerchantId(),account.getMerchantId());
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("MerchantTest123!",account.getPassword()));
        assertNull(credential(no).getCodeCipher());assertNull(onboarding.info(token,no).getActivationCode());
    }
    @Test void legacyAddressIsIgnoredOnSubmitAndResubmit() throws Exception {
        var payload=json.valueToTree(input(null,key));
        ((com.fasterxml.jackson.databind.node.ObjectNode)payload).put("address","旧页面仍传的地址");
        var response=mvc.perform(post("/app/merchant/onboarding/submit").contentType("application/json").content(json.writeValueAsString(payload)))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.snapshot.address").doesNotExist()).andReturn();
        String no=JSONUtil.parseObj(response.getResponse().getContentAsString()).getJSONObject("data").getStr("applicationNo");
        var first=application(no);
        var legacy=JSONUtil.parseObj(first.getSnapshot());legacy.set("address","历史申请地址");
        first.setSnapshot(legacy.toString());applications.update(first);
        // 改版后仍可用同一requestId找回历史申请，不因废弃地址字段而失去幂等性。
        assertEquals(no,onboarding.submit(input(null,key),false).getApplicationNo());
        review.review("MERCHANT",first.getId(),1,false,"修改资料");
        mvc.perform(post("/app/merchant/onboarding/resubmit").contentType("application/json")
            .content(json.writeValueAsString(input(no,UUID.randomUUID().toString()))))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.submissionNo").value(2))
            .andExpect(jsonPath("$.data.snapshot.address").doesNotExist());
        assertEquals("历史申请地址",JSONUtil.parseObj(applications.selectOneById(first.getId()).getSnapshot()).getStr("address"),"历史审核资料应原样保留");
    }
    @Test void activatingHistoricalAddressDoesNotCopyItOrCreateStore() {
        String no=approved();var a=application(no);
        var legacy=JSONUtil.parseObj(a.getSnapshot());legacy.set("address","历史地址不是新门店地址");
        a.setSnapshot(legacy.toString());applications.update(a);
        var result=onboarding.activate(activate(onboarding.info(token,no).getActivationCode(),username()));
        assertNull(merchants.selectOneById(result.getMerchantId()).getAddress());
        assertEquals(0,web.getBean(MerchantStoreMapper.class).selectCountByQuery(QueryWrapper.create()
            .eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getMerchantId,result.getMerchantId())));
        assertEquals("历史地址不是新门店地址",onboarding.info(token,no).getSnapshot().get("address"));
    }
    @Test void sameRequestIsIdempotentAndCannotChangePayload() {
        String no=onboarding.submit(input(null,key),false).getApplicationNo();assertEquals(no,onboarding.submit(input(null,key),false).getApplicationNo());
        Submit changed=new Submit(token,key,null,"不同商家",categoryId,"联系人",null);
        assertThrows(CoolException.class,()->onboarding.submit(changed,false));
        assertEquals(1,applications.selectCountByQuery(QueryWrapper.create().eq(MerchantApplicationEntity::getApplicationNo,no)));
    }
    @Test void anotherVerifiedPhoneCannotReadRenewOrRedeemSomeoneElsesCode() {
        String no=approved(),other=grant("196"+phone.substring(3));String code=onboarding.info(token,no).getActivationCode();
        assertTrue(onboarding.list(other).isEmpty());assertThrows(CoolException.class,()->onboarding.info(other,no));
        assertThrows(CoolException.class,()->onboarding.renew(other,no));
        assertThrows(CoolException.class,()->onboarding.activate(new Activate(other,code,username(),"MerchantTest123!")));
    }
    @Test void rejectionCreatesNewHistoricalRoundAndStillRequiresApproval() {
        String no=onboarding.submit(input(null,key),false).getApplicationNo();var first=application(no);review.review("MERCHANT",first.getId(),1,false,"补充证照");
        assertEquals("补充证照",onboarding.info(token,no).getReason());onboarding.submit(input(no,UUID.randomUUID().toString()),true);
        assertEquals(2,application(no).getSubmissionNo());assertEquals(0,applications.selectOneById(first.getId()).getCurrentFlag());
        assertEquals("PENDING",onboarding.info(token,no).getState());assertNull(onboarding.info(token,no).getActivationCode());
        assertThrows(CoolException.class,()->review.review("MERCHANT",first.getId(),1,true,null));
    }
    @Test void renewalInvalidatesOldCodeAndExpiredCodeCannotActivate() {
        String no=approved(),old=onboarding.info(token,no).getActivationCode();
        var c=credential(no);c.setExpiresAt(new Date(System.currentTimeMillis()-60_000));credentials.update(c);
        assertEquals("CODE_EXPIRED",onboarding.info(token,no).getState());assertThrows(CoolException.class,()->onboarding.activate(activate(old,username())));
        String fresh=onboarding.renew(token,no).getActivationCode();assertNotEquals(old,fresh);
        assertThrows(CoolException.class,()->onboarding.activate(activate(old,username())));
        assertEquals("ACTIVATED",onboarding.activate(activate(fresh,username())).getState());
    }
    @Test void duplicateActivationAndDuplicateReviewNeverCreateAnotherMerchant() {
        String no=approved(),code=onboarding.info(token,no).getActivationCode();String username=username();
        var first=onboarding.activate(activate(code,username));assertNotNull(first.getMerchantId());
        assertThrows(CoolException.class,()->onboarding.activate(activate(code,username)));
        var a=application(no);assertThrows(CoolException.class,()->review.review("MERCHANT",a.getId(),1,true,null));
        assertEquals(1,accounts.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getMerchantId,first.getMerchantId())));
    }
    @Test void ordinaryLoginIsNotRequiredButPhoneVerificationIsRequired() throws Exception {
        var verified=mvc.perform(post("/app/merchant/onboarding/verify").contentType("application/json").content("{\"phone\":\""+phone+"\",\"smsCode\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andReturn();
        grants.add(JSONUtil.parseObj(verified.getResponse().getContentAsString()).getJSONObject("data").getStr("onboardingToken"));
        String expired="a".repeat(64);assertThrows(CoolException.class,()->onboarding.list(expired));
        var grant=cache.get("onboarding:"+cn.hutool.crypto.SecureUtil.sha256(token),cn.hutool.core.lang.Dict.class);
        grant.set("expiresAt",0L);cache.set("onboarding:"+cn.hutool.crypto.SecureUtil.sha256(token),grant,1800);
        assertThrows(CoolException.class,()->onboarding.list(token));
        mvc.perform(post("/merchant/workspace/store/page").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void invalidSmsDoesNotIssueAnOnboardingGrant() {
        doThrow(new CoolException("验证码错误")).when(sms).checkVerifyCode(phone,"000000",UserSmsUtil.SendSceneEnum.ALL);
        assertThrows(CoolException.class,()->onboarding.verify(phone,"000000"));
    }
    @Test void accountCollisionDoesNotConsumeCodeOrCreateMerchant() {
        String no=approved(),code=onboarding.info(token,no).getActivationCode();
        var user=new BaseSysUserEntity();String username=username();user.setUsername(username);user.setName("已存在账号");user.setNickName("已存在账号");user.setPassword("test-unused");user.setPasswordV(1);user.setStatus(1);accounts.insertSelective(user);
        long before=merchants.selectCountByQuery(QueryWrapper.create());
        assertThrows(CoolException.class,()->onboarding.activate(activate(code,username)));
        assertEquals(before,merchants.selectCountByQuery(QueryWrapper.create()));assertNull(credential(no).getActivatedAt());
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void independentTransactionsCanActivateTheSameApplicationOnlyOnce() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            String no=tx.execute(status->approved());
            String code=onboarding.info(token,no).getActivationCode(),username=username();
            var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Object> action=()->{ready.countDown();start.await();try{return onboarding.activate(activate(code,username));}catch(CoolException expected){return expected;}};
            var first=pool.submit(action);var second=pool.submit(action);
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            var results=List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(com.cool.modules.approval.entity.resp.MerchantOnboardingResp.class::isInstance).count());
            assertEquals(1,results.stream().filter(CoolException.class::isInstance).count());
            assertEquals(1,accounts.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,username)));
        } finally {
            pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS));
            tx.execute(status->{
                var rows=credentials.selectListByQuery(QueryWrapper.create().eq(MerchantActivationEntity::getPhone,phone).eq(MerchantActivationEntity::getRequestId,key));
                for(var row:rows) {
                    var rounds=applications.selectListByQuery(QueryWrapper.create().eq(MerchantApplicationEntity::getApplicationNo,row.getApplicationNo()));
                    for(var round:rounds) {
                        auditRows.deleteByQuery(QueryWrapper.create().eq(AuditRecordEntity::getBusinessType,"MERCHANT_APPLICATION").eq(AuditRecordEntity::getBusinessId,round.getId()));
                        if(round.getMerchantId()!=null) {
                            accounts.deleteByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getMerchantId,round.getMerchantId()));
                            merchants.deleteById(round.getMerchantId());
                        }
                        applications.deleteById(round.getId());
                    }
                    credentials.deleteById(row.getId());
                }
                categories.deleteById(categoryId);return null;
            });
        }
    }
    @Test void retiredWebRegistrationAlwaysReturnsMigrationNoticeAndNeverWritesApplication() throws Exception {
        long before=applications.selectCountByQuery(QueryWrapper.create());
        mvc.perform(post("/merchant/auth/register").contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(410))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("小程序")));
        mvc.perform(post("/merchant/auth/register"))
            .andExpect(jsonPath("$.code").value(410));
        assertEquals(before,applications.selectCountByQuery(QueryWrapper.create()));
    }
    @Test void separateMobileVisitsWithSamePhoneSeeSameApplicationWithoutSharingLoginToken() throws Exception {
        String no=onboarding.submit(input(null,key),false).getApplicationNo();
        String secondTicket=grant(phone);
        assertNotEquals(token,secondTicket);
        mvc.perform(post("/app/merchant/onboarding/list").header("Authorization","expired-token-from-another-app")
            .contentType("application/json").content(json.writeValueAsString(new Session(secondTicket))))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data[0].applicationNo").value(no))
            .andExpect(jsonPath("$.data[0].activationCode").isEmpty());
        var a=application(no);review.review("MERCHANT",a.getId(),1,true,"同意");
        mvc.perform(post("/app/merchant/onboarding/info").contentType("application/json")
            .content(json.writeValueAsString(new Detail(secondTicket,no))))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.state").value("APPROVED"))
            .andExpect(jsonPath("$.data.activationCode").isString());
        assertEquals(no,onboarding.submit(new Submit(secondTicket,key,null,"游客测试商家",categoryId,"测试联系人","测试说明"),false).getApplicationNo());
    }
    @Test void onceActivatedOwnerLogsInWithPasswordWithoutAnyFurtherActivationCode() throws Exception {
        String no=approved(),code=onboarding.info(token,no).getActivationCode(),name=username();
        var activated=onboarding.activate(activate(code,name));
        var storeMapper=web.getBean(com.cool.modules.merchant.mapper.MerchantStoreMapper.class);
        assertEquals(0,storeMapper.selectCountByQuery(QueryWrapper.create()
            .eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getMerchantId,activated.getMerchantId())));
        String captchaId=UUID.randomUUID().toString();cache.set("verify:img:"+captchaId,"1234",60);
        var login=new com.cool.modules.base.dto.sys.BaseSysLoginDto();login.setUsername(name);login.setPassword("MerchantTest123!");
        login.setCaptchaId(captchaId);login.setVerifyCode("1234");
        var loggedIn=(cn.hutool.core.lang.Dict)merchantLogin.login(login);
        try {
            var owner=merchantLogin.validate(loggedIn.getStr("token"),false);
            assertEquals("OWNER",owner.getMerchantRole());assertEquals(activated.getMerchantId(),owner.getMerchantId());
            assertNull(onboarding.info(token,no).getActivationCode());
            assertThrows(CoolException.class,()->onboarding.renew(token,no));
            assertThrows(CoolException.class,()->onboarding.activate(activate(code,name)));
        } finally {merchantLogin.logout(loggedIn.getStr("token"));cache.del("verify:img:"+captchaId);}
    }
    @Test void mobileApplicationCannotUseLegacyPasswordResubmissionRoute() {
        String no=onboarding.submit(input(null,key),false).getApplicationNo();var a=application(no);
        review.review("MERCHANT",a.getId(),1,false,"修改资料");
        // 即使测试主动设置了已知密码，带手机号凭证的新申请也不能走旧身份流程。
        a=application(no);a.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("LegacyTest123!"));applications.update(a);
        var p=new com.cool.modules.approval.entity.req.MerchantRegistrationReq();p.setUsername(a.getUsername());p.setPassword("LegacyTest123!");
        p.setMerchantName(a.getMerchantName());p.setCategoryId(categoryId);p.setContactName("测试联系人");p.setContactPhone(phone);p.setAddress("测试地址");
        p.setCaptchaId("test-unused");p.setVerifyCode("1234");
        var error=assertThrows(CoolException.class,()->web.getBean(MerchantRegistrationService.class).submit(p,true));
        assertTrue(error.getMessage().contains("小程序"));assertEquals(1,application(no).getSubmissionNo());
    }

}
