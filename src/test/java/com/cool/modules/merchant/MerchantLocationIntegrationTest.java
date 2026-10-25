package com.cool.modules.merchant;

import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.merchant.location.MerchantLocationService;
import com.cool.modules.merchant.service.MerchantAccountService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static com.cool.modules.merchant.location.LocationModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用事务回滚的真实数据库验证地图归属位置补录与距离分页
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:14:18
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=location-test-unused", "sms-auth.access-key-secret=location-test-unused"
})
@ActiveProfiles("local")
@Transactional
class MerchantLocationIntegrationTest {
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired MerchantLocationService locations;
    @Autowired MerchantAccountService accounts;
    @Autowired CoolCache cache;
    @Autowired WebApplicationContext web;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.modules.user.util.UserSmsUtil sms;
    JdbcTemplate jdbc;
    MockMvc mvc;
    Long merchant, foreign, disabledMerchant, type, userId, atOrigin, farther, missing, disabled, foreignStore;
    String token, prefix;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(sqlSession.getConnection(), true));
        prefix = "位置测试" + UUID.randomUUID().toString().substring(0, 8);
        Long category = jdbc.queryForObject("SELECT id FROM merchant_category WHERE status=1 LIMIT 1", Long.class);
        type = jdbc.queryForObject("SELECT id FROM store_type WHERE status=1 LIMIT 1", Long.class);
        merchant = merchant(category, 1); foreign = merchant(category, 1); disabledMerchant = merchant(category, 0);
        atOrigin = store(merchant, "原点", "119", "26", 1);
        farther = store(merchant, "远点", "119.01", "26", 1);
        missing = store(merchant, "未定位", null, null, 1);
        disabled = store(merchant, "停用", "119", "26", 0);
        foreignStore = store(foreign, "其他商家", "119", "26", 1);
        store(disabledMerchant, "商家停用", "119", "26", 1);
        String username = "loc_" + UUID.randomUUID().toString().replace("-", "").substring(0,16);
        userId = accounts.provision(username, "地图测试", "LocationTest123!", merchant);
        var login = new BaseSysLoginDto(); login.setUsername(username); login.setPassword("LocationTest123!");
        String captcha = UUID.randomUUID().toString(); cache.set("verify:img:" + captcha, "1234", 60);
        login.setCaptchaId(captcha); login.setVerifyCode("1234");
        token = ((Dict) accounts.login(login)).getStr("token");
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(security, new com.cool.core.request.RequestParamsFilter()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    Long insert(String sql, Object... args) {
        var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var stmt = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]); return stmt;
        }, holder); return holder.getKey().longValue();
    }
    Long merchant(Long category, int status) {
        return insert("INSERT INTO merchant_info(name,category_id,contact_name,contact_phone,status) VALUES(?,?,?,?,?)", prefix, category, "位置测试", "13800000001", status);
    }
    Long store(Long owner, String suffix, String longitude, String latitude, int status) {
        return insert("INSERT INTO store(merchant_id,category_id,name,address,phone,longitude,latitude,status) VALUES(?,?,?,?,?,?,?,?)",
                owner, type, prefix+suffix, "测试路1号", "13800000001", longitude, latitude, status);
    }
    NearbyQuery nearby() {
        var input = new NearbyQuery(); input.setLongitude(new BigDecimal("119")); input.setLatitude(new BigDecimal("26"));
        input.setKeyword(prefix); return input;
    }
    LocationUpdate update(Long id) {
        var input = new LocationUpdate(); input.setId(id); input.setAddress("福州测试地址2号");
        input.setLongitude(new BigDecimal("119.1234567")); input.setLatitude(new BigDecimal("26.1234567")); return input;
    }

    @Test void mapIncludesAllStoresBeyondFirstPageAndCountsMissingCoordinates() {
        for (int i=0;i<22;i++) store(merchant, "补充"+i, null, null, 1);
        var query = new MapQuery(); query.setMerchantId(merchant);
        var result = locations.adminMap(query);
        assertEquals(26, result.total()); assertEquals(3, result.locatedCount()); assertEquals(23, result.unlocatedCount());
        assertTrue(result.list().stream().anyMatch(p -> p.getId().equals(disabled) && p.getStatusText().equals("已停用")));
        assertTrue(result.list().stream().allMatch(p -> prefix.equals(p.getMerchantName()) && p.getStoreTypeName() != null));
    }

    @Test void merchantScopeIgnoresForgedMerchantId() throws Exception {
        var query = new MapQuery(); query.setMerchantId(foreign);
        assertTrue(locations.merchantMap(userId,query).list().stream().allMatch(p -> merchant.equals(p.getMerchantId())));
        mvc.perform(post("/merchant/workspace/store/map").header("Authorization", token).contentType("application/json")
                .content("{\"merchantId\":"+foreign+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.unlocatedCount").value(1));
    }

    @Test void unlocatedFilterIncludesPartialHistoricalCoordinates() {
        store(merchant, "仅经度", "119", null, 1);
        var query = new MapQuery(); query.setMerchantId(merchant); query.setLocated(false);
        assertEquals(2, locations.adminMap(query).total());
        query.setLocated(true); assertEquals(3, locations.adminMap(query).total());
    }

    @Test void nearbyExcludesUnlocatedAndDisabledAndSortsBeforePagination() {
        var input = nearby(); input.setSize(1);
        var first = locations.nearby(input);
        assertEquals(3L, first.getPagination().getTotal());
        assertEquals(atOrigin, first.getList().get(0).getId());
        assertEquals(0d, first.getList().get(0).getDistanceMeters(), 0.001);
        input.setPage(2); assertEquals(foreignStore, locations.nearby(input).getList().get(0).getId());
        input.setPage(3); var last = locations.nearby(input).getList().get(0);
        assertEquals(farther, last.getId()); assertEquals(999.4, last.getDistanceMeters(), 3);
        input.setPage(4); assertTrue(locations.nearby(input).getList().isEmpty());
    }

    @Test void nearbyRadiusAndMerchantAndCategoryFiltersAreApplied() {
        var input = nearby(); input.setRadiusMeters(100); input.setMerchantId(merchant);
        assertEquals(List.of(atOrigin), locations.nearby(input).getList().stream().map(StorePoint::getId).toList());
        input.setCategoryId(jdbc.queryForObject("SELECT category_id FROM merchant_info WHERE id=?", Long.class, merchant));
        assertEquals(1, locations.nearby(input).getList().size());
        input.setCategoryId(Long.MAX_VALUE); assertEquals(0L, locations.nearby(input).getPagination().getTotal());
    }

    @Test void nearbyHandlesDateLineWithoutTreatingItAsAWorldWideDistance() {
        Long origin = store(merchant,"日期线东", "179.999", "0",1);
        Long across = store(merchant,"日期线西", "-179.999", "0",1);
        var input = nearby(); input.setLongitude(new BigDecimal("179.999")); input.setLatitude(BigDecimal.ZERO);
        input.setRadiusMeters(300); input.setMerchantId(merchant);
        var rows = locations.nearby(input).getList();
        assertEquals(List.of(origin,across),rows.stream().map(StorePoint::getId).toList());
        assertEquals(222.39, rows.get(1).getDistanceMeters(),1);
    }

    @Test void locationUpdatePreservesOtherFieldsAndRejectsForeignStore() throws Exception {
        assertTrue(locations.updateMerchant(userId, update(missing)));
        assertEquals(new BigDecimal("119.1234567"), jdbc.queryForObject("SELECT longitude FROM store WHERE id=?", BigDecimal.class,missing));
        assertEquals(prefix+"未定位", jdbc.queryForObject("SELECT name FROM store WHERE id=?",String.class,missing));
        assertThrows(CoolException.class, () -> locations.updateMerchant(userId,update(foreignStore)));
        mvc.perform(post("/merchant/workspace/store/location/update").header("Authorization", token).contentType("application/json")
                .content(cn.hutool.json.JSONUtil.toJsonStr(update(foreignStore)))).andExpect(jsonPath("$.code").value(403));
        assertEquals("测试路1号", jdbc.queryForObject("SELECT address FROM store WHERE id=?", String.class, foreignStore));
    }

    @Test void invalidInputsAreRejected() {
        var input = nearby(); input.setLongitude(new BigDecimal("181")); assertThrows(CoolException.class, () -> locations.nearby(input));
        input.setLongitude(null); assertThrows(CoolException.class, () -> locations.nearby(input));
        var radius = nearby(); radius.setRadiusMeters(0); assertThrows(CoolException.class, () -> locations.nearby(radius));
        var location = update(missing); location.setLatitude(null); assertThrows(CoolException.class, () -> locations.updateAdmin(location));
        var query = new MapQuery(); query.setStatus(2); assertThrows(CoolException.class, () -> locations.adminMap(query));
    }

    @Test void publicNearbyAndProtectedManagementRoutesHaveCorrectAuthentication() throws Exception {
        mvc.perform(post("/app/merchant/store/nearby").contentType("application/json").content(cn.hutool.json.JSONUtil.toJsonStr(nearby())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(post("/admin/merchant/store/map").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/merchant/store/map").header("Authorization", token).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/merchant/workspace/store/map").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/app/merchant/map/suggest").param("keyword", "测试")).andExpect(status().isUnauthorized());
    }
}
