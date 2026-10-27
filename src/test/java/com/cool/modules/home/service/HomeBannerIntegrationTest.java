package com.cool.modules.home.service;

import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.home.entity.HomeBannerEntity;
import com.cool.modules.home.mapper.HomeBannerMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static com.cool.modules.home.model.HomeBannerModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在事务回滚的数据库中验证广告时间边界目标过滤空值清除和权限
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:21:59
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=banner-test-unused", "sms-auth.access-key-secret=banner-test-unused"
})
@ActiveProfiles("local")
@Transactional
class HomeBannerIntegrationTest {
    @Autowired HomeBannerService service;
    @Autowired HomeBannerMapper banners;
    @Autowired MerchantInfoMapper merchants;
    @Autowired MerchantStoreMapper stores;
    @Autowired MerchantCategoryMapper categories;
    @Autowired MerchantStoreCategoryMapper storeTypes;
    @Autowired CouponTemplateMapper coupons;
    @Autowired CouponPublicationMapper publications;
    @Autowired BaseSysUserMapper users;
    @Autowired CoolCache cache;
    @Autowired JwtTokenUtil tokens;
    @Autowired WebApplicationContext web;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.modules.user.util.UserSmsUtil sms;
    String prefix;
    Long merchantId, storeId;
    MockMvc mvc;
    List<String> cacheKeys = new ArrayList<>();

    @BeforeEach void setup() {
        prefix = "广告测试" + UUID.randomUUID().toString().substring(0,8);
        var category = new MerchantCategoryEntity(); category.setName(prefix); category.setStatus(1); category.setSort(0); category.setShowOnFront(1); categories.insertSelective(category);
        var type = new MerchantStoreCategoryEntity(); type.setName(prefix); type.setStatus(1); type.setSort(0); storeTypes.insertSelective(type);
        var merchant = new MerchantInfoEntity(); merchant.setName(prefix); merchant.setCategoryId(category.getId()); merchant.setContactName("测试联系人"); merchant.setContactPhone("13800000001"); merchant.setStatus(1); merchants.insertSelective(merchant); merchantId = merchant.getId();
        var store = new MerchantStoreEntity(); store.setName(prefix); store.setMerchantId(merchantId); store.setCategoryId(type.getId()); store.setAddress("测试路1号"); store.setPhone("13800000001"); store.setStatus(1); stores.insertSelective(store); storeId = store.getId();
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); cacheKeys.forEach(key -> cache.del(key)); }
    HomeBannerEntity banner(int sort) {
        var value = new HomeBannerEntity(); value.setTitle(prefix + sort); value.setImageUrl("/uploads/banner-test.png"); value.setJumpType("NONE"); value.setSort(sort); value.setStatus(1); return value;
    }
    List<PublicBanner> mine(Date at) { return service.visibleAt(at).stream().filter(b -> b.title().startsWith(prefix)).toList(); }
    String adminToken(String... permissions) {
        var user = new BaseSysUserEntity(); user.setUsername("btest_"+UUID.randomUUID().toString().replace("-", "").substring(0,15)); user.setName(prefix); user.setNickName(prefix); user.setPassword("unused"); user.setPasswordV(1); user.setStatus(1); users.insertSelective(user);
        var auth = new ArrayList<org.springframework.security.core.GrantedAuthority>(); for (String permission : permissions) auth.add(new SimpleGrantedAuthority(permission));
        var principal = new JwtUser(user.getId(),user.getUsername(),"",auth,true); principal.setUserTypeEnum(UserTypeEnum.ADMIN);
        String details = "admin:userDetails:" + user.getUsername(), version = "admin:passwordVersion:" + user.getId();
        cache.set(details,principal,60); cache.set(version,1,60); cacheKeys.add(details); cacheKeys.add(version);
        return tokens.generateToken(Dict.create().set("userType","ADMIN").set("userId",user.getId()).set("username",user.getUsername()).set("passwordVersion",1));
    }
    Long coupon() {
        var value = new CouponTemplateEntity(); value.setName(prefix); value.setMerchantId(merchantId); value.setType("CASH"); value.setDeductAmount(BigDecimal.TEN); value.setSalePrice(BigDecimal.ONE); value.setScopeType(0); value.setValidityType("AFTER_ISSUE"); value.setValidDays(30); value.setStatus(1); value.setRuleVersion(1); coupons.insertSelective(value);
        var pub = new CouponPublicationEntity(); pub.setMerchantId(merchantId); pub.setCouponTemplateId(value.getId()); pub.setChannel("PLATFORM"); pub.setAuditStatus(2); pub.setPublishStatus(1); pub.setPublishedVersion(1); pub.setPublishedAt(new Date()); publications.insertSelective(pub);
        return value.getId();
    }

    @Test void createsDraftByDefaultAndReturnsReadablePage() {
        var value = banner(4); value.setStatus(null); value.setSort(null); value.setTitle("  " + prefix + "  ");
        Long id = service.add(value); var stored = service.info(id);
        assertEquals(0,stored.getStatus()); assertEquals(0,stored.getSort()); assertEquals(prefix,stored.getTitle()); assertEquals("已停用",stored.getDisplayStateText());
        var input = new PageInput(); input.setKeyword(prefix); input.setStatus(0);
        assertEquals(1,service.page(input).getPagination().getTotal());
        assertTrue(mine(new Date()).isEmpty());
    }

    @Test void startIsInclusiveAndEndIsExclusiveAndDisabledIsHidden() {
        Date at = Date.from(Instant.parse("2031-01-01T00:00:00Z"));
        var current = banner(0); current.setStartTime(at); current.setEndTime(new Date(at.getTime()+1000)); Long currentId = service.add(current);
        var ended = banner(1); ended.setEndTime(at); service.add(ended);
        var future = banner(2); future.setStartTime(new Date(at.getTime()+1000)); service.add(future);
        var disabled = banner(3); disabled.setStatus(0); service.add(disabled);
        assertEquals(List.of(currentId),mine(at).stream().map(PublicBanner::id).toList());
    }

    @Test void sortsAndLimitsOnlyAfterInvalidTargetsHaveBeenRemoved() {
        var storeBanner = banner(0); storeBanner.setJumpType("STORE"); storeBanner.setJumpTarget(storeId.toString()); service.add(storeBanner);
        var change = stores.selectOneById(storeId); change.setStatus(0); stores.update(change);
        List<Long> valid = new ArrayList<>(); for (int i=1;i<=12;i++) valid.add(service.add(banner(i)));
        assertEquals(valid.subList(0,10),mine(new Date()).stream().map(PublicBanner::id).toList());
    }

    @Test void updateClearsDatesAndPreviousJumpTargetInDatabase() {
        var value = banner(0); value.setJumpType("WEB"); value.setJumpTarget("https://example.com/activity"); value.setStartTime(new Date(System.currentTimeMillis()+60000)); value.setEndTime(new Date(System.currentTimeMillis()+120000)); Long id=service.add(value);
        var updated = banner(0); updated.setId(id); updated.setJumpType("NONE"); updated.setJumpTarget("stale-target");
        assertTrue(service.update(updated)); var row=banners.selectOneById(id);
        assertNull(row.getStartTime()); assertNull(row.getEndTime()); assertNull(row.getJumpTarget()); assertNotNull(row.getCreateTime());
        assertEquals(1,mine(new Date()).size());
    }

    @Test void validatesUnsafeUrlsMissingFieldsAndTimeOrder() {
        var value=banner(0); value.setJumpType("WEB"); value.setJumpTarget("javascript:alert(1)"); assertThrows(CoolException.class,()->service.add(value));
        value.setJumpTarget("//example.com"); assertThrows(CoolException.class,()->service.add(value));
        value.setJumpTarget("https://user:password@example.com"); assertThrows(CoolException.class,()->service.add(value));
        value.setJumpType("NONE"); value.setImageUrl("data:image/png;base64,x"); assertThrows(CoolException.class,()->service.add(value));
        value.setImageUrl("/uploads/a.png"); value.setEndTime(new Date(1)); value.setStartTime(new Date(2)); assertThrows(CoolException.class,()->service.add(value));
        value.setStartTime(null); value.setEndTime(null); value.setTitle(" "); assertThrows(CoolException.class,()->service.add(value));
        var page=new PageInput(); page.setSize(101); assertThrows(CoolException.class,()->service.page(page));
    }

    @Test void cannotEnableMissingOrDisabledTargetButCanPauseIt() {
        var value=banner(0); value.setJumpType("STORE"); value.setJumpTarget(storeId.toString()); Long id=service.add(value);
        var store=stores.selectOneById(storeId); store.setStatus(0); stores.update(store);
        assertTrue(service.status(new StatusInput(id,0))); assertThrows(CoolException.class,()->service.status(new StatusInput(id,1)));
        assertEquals(0,banners.selectOneById(id).getStatus());
        value.setId(null); value.setJumpTarget("999999999999999999999999"); assertThrows(CoolException.class,()->service.add(value));
    }

    @Test void couponUnpublishOrRuleChangeRemovesAdvertisement() {
        Long couponId=coupon(); var value=banner(0); value.setJumpType("COUPON"); value.setJumpTarget(couponId.toString()); service.add(value);
        assertEquals(1,mine(new Date()).size());
        var coupon=coupons.selectOneById(couponId); coupon.setRuleVersion(2); coupons.update(coupon);
        assertTrue(mine(new Date()).isEmpty()); assertEquals("目标不可用",service.info(value.getId()).getDisplayStateText());
    }

    @Test void targetOptionsArePubliclyVisibleAndHaveReadableNames() {
        Long couponId=coupon(); var result=service.targetOptions("STORE",prefix,1);
        assertEquals(storeId,result.getList().get(0).getId()); assertEquals(prefix,result.getList().get(0).getMerchantName());
        assertEquals(couponId,service.targetOptions("COUPON",prefix,1).getList().get(0).getId());
    }

    @Test void batchDeleteIsAtomicIfOneIdDoesNotExist() {
        Long id=service.add(banner(0)); assertThrows(CoolException.class,()->service.delete(List.of(id,Long.MAX_VALUE)));
        assertNotNull(banners.selectOneById(id)); assertTrue(service.delete(List.of(id))); assertNull(banners.selectOneById(id));
    }

    @Test void publicReadWorksWithoutLoginAndAdminWriteRequiresPermissions() throws Exception {
        Long id=service.add(banner(0));
        mvc.perform(get("/app/home/banner/list")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(post("/admin/home/banner/page").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        String token=adminToken("home:banner:page","home:banner:info");
        mvc.perform(post("/admin/home/banner/page").header("Authorization",token).contentType("application/json").content("{}")).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(post("/admin/home/banner/status").header("Authorization",token).contentType("application/json").content("{\"id\":"+id+",\"status\":0}")).andExpect(jsonPath("$.code").value(403));
        assertEquals(1,banners.selectOneById(id).getStatus());
    }

    @Test void adminApiCanCreateEditClearAndDeleteAdvertisement() throws Exception {
        String token=adminToken("home:banner:add","home:banner:update","home:banner:delete","home:banner:info");
        var value=banner(0); value.setStartTime(new Date(System.currentTimeMillis()+60000));
        String response=mvc.perform(post("/admin/home/banner/add").header("Authorization",token).contentType("application/json").content(cn.hutool.json.JSONUtil.toJsonStr(value)))
                .andExpect(jsonPath("$.code").value(1000)).andReturn().getResponse().getContentAsString();
        Long id=cn.hutool.json.JSONUtil.parseObj(response).getLong("data");
        String body="{\"id\":"+id+",\"title\":\""+prefix+"修改\",\"imageUrl\":\"/uploads/new.png\",\"jumpType\":\"NONE\",\"jumpTarget\":null,\"sort\":1,\"status\":0,\"startTime\":null,\"endTime\":null}";
        mvc.perform(post("/admin/home/banner/update").header("Authorization",token).contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(1000));
        assertNull(banners.selectOneById(id).getStartTime());
        mvc.perform(post("/admin/home/banner/delete").header("Authorization",token).contentType("application/json").content("{\"ids\":["+id+"]}")).andExpect(jsonPath("$.code").value(1000));
        assertNull(banners.selectOneById(id));
    }
}
