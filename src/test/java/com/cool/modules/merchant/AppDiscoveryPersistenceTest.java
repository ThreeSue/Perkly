package com.cool.modules.merchant;

import com.cool.core.annotation.TokenIgnore;
import com.cool.core.exception.CoolException;
import com.cool.core.request.prefix.AutoPrefixUrlMapping;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.service.AppDiscoveryService;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用事务回滚验证用户公开查询的可见性、分页和门店券关系
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:06:05
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=discovery-test-unused", "sms-auth.access-key-secret=discovery-test-unused"
})
@ActiveProfiles("local")
@Transactional
@AutoConfigureMockMvc
class AppDiscoveryPersistenceTest {
    @Autowired private SqlSessionTemplate session;
    @Autowired private AutoPrefixUrlMapping mappings;
    @Autowired private MockMvc mvc;
    private JdbcTemplate jdbc;
    @Autowired private AppDiscoveryService discovery;
    private long merchant;
    private long root;
    private long store;
    private long secondStore;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(session.getConnection(), true)) {
            @Override public int update(String sql, Object... args) {
                int changed = super.update(sql, args);
                // 测试辅助SQL绕过Mapper写数据后清理一级缓存，模拟真实请求间的数据变化。
                session.clearCache();
                return changed;
            }
        };
        root = insert("INSERT INTO merchant_category(name,sort,status,show_on_front) VALUES('公开查询回滚分类',0,1,1)");
        long category = insert("INSERT INTO merchant_category(name,parent_id,sort,status,show_on_front) VALUES('公开查询回滚子分类',?,0,1,1)", root);
        merchant = insert("INSERT INTO merchant_info(name,category_id,contact_name,contact_phone,status,remark) VALUES('公开查询回滚商家',?,'内部联系人','13800000000',1,'不可泄露的内部备注')", category);
        long storeCategory = insert("INSERT INTO store_category(merchant_id,name,sort,status,show_on_front) VALUES(?,'回滚门店分类',0,1,1)", merchant);
        store = insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,'公开查询门店','测试地址','13800000000',1)", merchant, storeCategory);
        secondStore = insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,'第二门店','测试地址','13800000000',1)", merchant, storeCategory);
    }

    @Test void storePagingIncludesSubcategoriesAndExcludesInternalFields() {
        DiscoveryPageReq req = request(); req.setCategoryId(root); req.setSize(1);
        var first = discovery.stores(req);
        assertEquals(2L, first.getPagination().getTotal());
        assertEquals(1, first.getList().size());
        assertEquals(secondStore, number(first.getList().get(0).get("id")));
        assertEquals("公开查询回滚商家", first.getList().get(0).get("merchantName"));
        req.setPage(2);
        assertEquals(store, number(discovery.stores(req).getList().get(0).get("id")));
        Map<String, Object> detail = discovery.store(store);
        assertFalse(detail.containsKey("remark"));
        assertFalse(detail.containsKey("contactName"));
        assertFalse(detail.containsKey("contactPhone"));
        req.setPage(99);
        assertTrue(discovery.stores(req).getList().isEmpty());
    }

    @Test void disabledStoresAndMerchantsCannotBeOpenedByGuessingIds() {
        jdbc.update("UPDATE store SET status=0 WHERE id=?", store);
        assertThrows(CoolException.class, () -> discovery.store(store));
        jdbc.update("UPDATE merchant_info SET status=0 WHERE id=?", merchant);
        assertTrue(discovery.stores(request()).getList().isEmpty());
        assertThrows(CoolException.class, () -> discovery.store(secondStore));
    }

    @Test void onlyApprovedCurrentPlatformPublicationsArePublic() {
        long id = coupon(0);
        assertTrue(discovery.coupons(request()).getList().isEmpty());
        publish(id, "MERCHANT", 2, 1, 1);
        assertThrows(CoolException.class, () -> discovery.coupon(id));
        jdbc.update("DELETE FROM coupon_publication WHERE coupon_template_id=?", id);
        publish(id, "PLATFORM", 1, 1, 1);
        assertTrue(discovery.coupons(request()).getList().isEmpty());
        jdbc.update("UPDATE coupon_publication SET audit_status=2,published_version=2 WHERE coupon_template_id=?", id);
        assertThrows(CoolException.class, () -> discovery.coupon(id));
        jdbc.update("UPDATE coupon_publication SET published_version=1 WHERE coupon_template_id=?", id);
        assertEquals(1L, discovery.coupons(request()).getPagination().getTotal());
        jdbc.update("UPDATE coupon_publication SET publish_status=2 WHERE coupon_template_id=?", id);
        assertThrows(CoolException.class, () -> discovery.coupon(id));
    }

    @Test void storeFilteringAndDetailsRespectAllStoresAndSpecificStores() {
        long all = coupon(0); publish(all, "PLATFORM", 2, 1, 1);
        long specific = coupon(1); publish(specific, "PLATFORM", 2, 1, 1);
        jdbc.update("INSERT INTO coupon_store(merchant_id,coupon_template_id,store_id) VALUES(?,?,?)", merchant, specific, store);
        DiscoveryPageReq req = request(); req.setStoreId(secondStore);
        assertEquals(1L, discovery.coupons(req).getPagination().getTotal());
        req.setStoreId(store);
        assertEquals(2L, discovery.coupons(req).getPagination().getTotal());
        List<?> stores = (List<?>) discovery.coupon(specific).get("stores");
        assertEquals(1, stores.size());
        assertEquals(store, number(((Map<?, ?>) stores.get(0)).get("id")));
        assertEquals(1990L, discovery.coupon(specific).get("salePrice"));
        assertEquals(2000L, discovery.coupon(specific).get("deductAmount"));
        jdbc.update("UPDATE store SET status=0 WHERE id=?", store);
        assertThrows(CoolException.class, () -> discovery.coupon(specific));
        assertEquals(1, ((List<?>) discovery.coupon(all).get("stores")).size());
        req.setStoreId(store);
        assertTrue(discovery.coupons(req).getList().isEmpty());
    }

    @Test void couponAvailabilityAndFiltersDoNotInventStockOrPurchaseState() {
        long id = coupon(0); publish(id, "PLATFORM", 2, 1, 1);
        assertNull(discovery.coupon(id).get("remainingStock"));
        assertEquals("AVAILABLE", discovery.coupon(id).get("saleState"));
        jdbc.update("UPDATE coupon_template SET issue_limit=1,issued_count=1 WHERE id=?", id);
        assertEquals("SOLD_OUT", discovery.coupon(id).get("saleState"));
        jdbc.update("UPDATE coupon_template SET sale_end_time=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?", id);
        assertEquals("ENDED", discovery.coupon(id).get("saleState"));
        DiscoveryPageReq req = request(); req.setKeyword("%_' OR 1=1 --");
        assertTrue(discovery.coupons(req).getList().isEmpty());
        req.setKeyword(null); req.setType("DISCOUNT");
        assertTrue(discovery.coupons(req).getList().isEmpty());
        jdbc.update("UPDATE merchant_info SET status=0 WHERE id=?", merchant);
        assertThrows(CoolException.class, () -> discovery.coupon(id));
    }

    @Test void rejectsUnboundedPagingAndInvalidIds() {
        DiscoveryPageReq req = request(); req.setSize(101);
        assertThrows(CoolException.class, () -> discovery.stores(req));
        req.setSize(20); req.setPage(0);
        assertThrows(CoolException.class, () -> discovery.coupons(req));
        assertThrows(CoolException.class, () -> discovery.store(-1L));
    }

    @Test void storeCoverTakesPriorityAndMissingCoverFallsBackToMerchantLogo() {
        jdbc.update("UPDATE merchant_info SET logo='/upload/merchant-logo.png' WHERE id=?", merchant);
        assertEquals("/upload/merchant-logo.png", discovery.store(store).get("cover"));
        jdbc.update("UPDATE store SET cover='/upload/store-cover.jpg' WHERE id=?", store);
        Map<String, Object> detail = discovery.store(store);
        assertEquals("/upload/store-cover.jpg", detail.get("cover"));
        assertEquals("/upload/merchant-logo.png", detail.get("merchantLogo"));
        assertTrue(discovery.stores(request()).getList().stream()
                .anyMatch(row -> number(row.get("id")) == store && "/upload/store-cover.jpg".equals(row.get("cover"))));
        long coupon = coupon(1); publish(coupon, "PLATFORM", 2, 1, 1);
        jdbc.update("INSERT INTO coupon_store(merchant_id,coupon_template_id,store_id) VALUES(?,?,?)", merchant, coupon, store);
        Map<?, ?> applicable = (Map<?, ?>) ((List<?>) discovery.coupon(coupon).get("stores")).get(0);
        assertEquals("/upload/store-cover.jpg", applicable.get("cover"));
        jdbc.update("UPDATE store SET cover='  ' WHERE id=?", store);
        assertEquals("/upload/merchant-logo.png", discovery.store(store).get("cover"));
    }

    @Test void preservesEveryPublicFieldNullDateMoneyAndNestedContentContract() throws Exception {
        Map<String, Object> storeDetail = discovery.store(store);
        assertEquals(java.util.Set.of("id", "name", "merchantId", "merchantName", "merchantLogo", "cover",
                "categoryId", "categoryName", "address", "phone", "businessHours", "longitude", "latitude",
                "status", "statusText", "merchantIntroduction"), storeDetail.keySet());
        assertTrue(storeDetail.containsKey("merchantLogo"));
        assertNull(storeDetail.get("merchantLogo"));
        long id = coupon(0); publish(id, "PLATFORM", 2, 1, 1);
        jdbc.update("UPDATE coupon_template SET issue_limit=10,issued_count=3,validity_type='FIXED',valid_days=NULL,valid_from='2098-01-02 03:04:05',valid_to='2100-01-01 00:00:00',sale_start_time='2099-01-01 00:00:00' WHERE id=?", id);
        jdbc.update("INSERT INTO coupon_product(merchant_id,coupon_template_id,product_id,relation_type,quantity) VALUES(?,?,987654,1,2)", merchant, id);
        Map<String, Object> detail = discovery.coupon(id);
        assertEquals(java.util.Set.of("id", "publicationId", "merchantId", "merchantName", "merchantLogo",
                "categoryId", "categoryName", "name", "type", "cover", "description", "salePrice", "scopeType",
                "minSpend", "discountAmount", "deductAmount", "maxDiscount", "discountRate", "issueLimit",
                "issuedCount", "remainingStock", "perCustomerLimit", "allowCombine", "validityType", "validDays",
                "saleStartTime", "saleEndTime", "validFrom", "validTo", "saleState", "typeText", "saleStateText",
                "stores", "contents"), detail.keySet());
        assertNull(detail.get("discountRate"));
        assertEquals(7L, number(detail.get("remainingStock")));
        assertEquals("2098-01-02 03:04:05", detail.get("validFrom"));
        assertEquals("NOT_STARTED", detail.get("saleState"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) detail.get("contents")).get(0);
        assertEquals(java.util.Set.of("productId", "relationType", "quantity", "description"), item.keySet());
        assertNull(item.get("description"));
        assertEquals(2, number(item.get("quantity")));
        mvc.perform(get("/app/coupon/info").param("id", Long.toString(id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.salePrice").value(1990))
                .andExpect(jsonPath("$.data.validFrom").value("2098-01-02 03:04:05"))
                .andExpect(jsonPath("$.data.saleState").value("NOT_STARTED"));
        mvc.perform(get("/app/merchant/store/info").param("id", Long.toString(store)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.merchantName").value("公开查询回滚商家"));
    }

    @Test void realFrameworkRegistersOnlyGuestReadRoutes() {
        Map<String, String> expected = Map.of(
                "/app/merchant/store/page", "POST", "/app/merchant/store/info", "GET",
                "/app/coupon/page", "POST", "/app/coupon/info", "GET");
        expected.forEach((path, method) -> {
            var entry = mappings.getHandlerMethods().entrySet().stream()
                    .filter(e -> e.getKey().getPatternValues().contains(path)).findFirst().orElseThrow();
            assertTrue(entry.getKey().getMethodsCondition().toString().contains(method));
            assertNotNull(entry.getValue().getMethodAnnotation(TokenIgnore.class));
        });
        assertTrue(mappings.getHandlerMethods().keySet().stream()
                .noneMatch(key -> key.getPatternValues().contains("/app/coupon/add")));
    }

    @Test void guestsCanReadListsThroughRealSecurityAndResponseSerialization() throws Exception {
        mvc.perform(get("/app/merchant/category/list"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data").isArray());
        for (String url : List.of("/app/merchant/store/page", "/app/coupon/page")) {
            mvc.perform(post(url).contentType("application/json").content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                    .andExpect(jsonPath("$.data.list").isArray())
                    .andExpect(jsonPath("$.data.pagination.page").value(1))
                    .andExpect(jsonPath("$.data.pagination.size").value(20));
        }
    }

    private DiscoveryPageReq request() {
        DiscoveryPageReq req = new DiscoveryPageReq(); req.setMerchantId(merchant); return req;
    }

    private long coupon(int scope) {
        return insert("INSERT INTO coupon_template(merchant_id,name,type,sale_price,deduct_amount,scope_type,validity_type,valid_days,status) VALUES(?,'公开查询测试券','CASH',19.90,20.00,?,'AFTER_ISSUE',30,1)", merchant, scope);
    }

    private void publish(long id, String channel, int audit, int status, int version) {
        jdbc.update("INSERT INTO coupon_publication(merchant_id,coupon_template_id,channel,audit_status,publish_status,published_version,published_at) VALUES(?,?,?,?,?,?,NOW())", merchant, id, channel, audit, status, version);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
}
