package com.cool.modules.coupon;

import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.CouponTemplateService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以事务回滚方式验证优惠券模板与门店关联的数据库行为
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:21:22
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=coupon-test-unused", "sms-auth.access-key-secret=coupon-test-unused"
})
@ActiveProfiles("local")
@Transactional
class CouponTemplatePersistenceTest {
    @Autowired private CouponTemplateService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private org.springframework.web.context.WebApplicationContext webContext;
    @Autowired private com.cool.modules.merchant.service.MerchantStoreService storeService;
    @Autowired private com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper relations;
    private Long merchantId;
    private Long storeId;

    @BeforeEach void setup() {
        // 测试辅助SQL复用框架Mapper的连接，避免Flex数据源包装产生第二个事务连接。
        jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(sqlSession.getConnection(), true));
        merchantId = jdbc.queryForObject("SELECT merchant_id FROM store WHERE status=1 ORDER BY id LIMIT 1", Long.class);
        storeId = jdbc.queryForObject("SELECT id FROM store WHERE status=1 AND merchant_id=? ORDER BY id LIMIT 1", Long.class, merchantId);
    }
    private CouponTemplateEntity coupon() {
        var e = new CouponTemplateEntity();
        e.setMerchantId(merchantId); e.setName("自动测试券_事务回滚");
        e.setType("CASH"); e.setDeductAmount(new BigDecimal("20"));
        e.setValidityType("AFTER_ISSUE"); e.setValidDays(30);
        e.setScopeType(1); e.setStoreIds(List.of(storeId)); e.setStatus(0);
        return e;
    }
    @Test void persistsStoreRelationsAndReturnsReadableNames() {
        var e = coupon(); Long id = service.add(e);
        var info = (CouponTemplateEntity) service.info(id);
        assertNotNull(info.getMerchantName());
        assertEquals(List.of(storeId), info.getStoreIds());
        assertEquals(1, info.getStoreNames().size());
        assertEquals(0L, info.getIssuedCount());
    }
    @Test void switchingTypeClearsOldMoneyAndIncrementsVersion() {
        Long id = service.add(coupon());
        var e = (CouponTemplateEntity) service.info(id);
        e.setType("DISCOUNT"); e.setDiscountRate(new BigDecimal("8.8"));
        e.setScopeType(0);
        assertTrue(service.update(e));
        var saved = (CouponTemplateEntity) service.info(id);
        assertNull(saved.getDeductAmount());
        assertEquals(2, saved.getRuleVersion());
        assertEquals(0, saved.getStoreIds().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_store WHERE coupon_template_id=?", Integer.class, id));
    }
    @Test void rejectsCrossMerchantStoreBeforeInsertingTemplate() {
        var missingMerchant = coupon(); missingMerchant.setMerchantId(-1L);
        assertThrows(CoolException.class, () -> service.add(missingMerchant));
        Long other = jdbc.queryForObject("SELECT id FROM store WHERE merchant_id<>? LIMIT 1", Long.class, merchantId);
        var invalid = coupon(); invalid.setStoreIds(List.of(other));
        assertThrows(CoolException.class, () -> service.add(invalid));
    }
    @Test void missingProductContentsBlocksEnablingExperienceCoupon() {
        var e = coupon(); e.setType("EXPERIENCE"); e.setStatus(1);
        assertThrows(CoolException.class, () -> service.add(e));
    }
    @Test void rejectsChangingMerchantAndProtectsServerCounters() {
        Long id = service.add(coupon());
        var e = (CouponTemplateEntity) service.info(id);
        e.setIssuedCount(999L); e.setRuleVersion(999);
        service.update(e);
        assertEquals(0L, service.getById(id).getIssuedCount());
        assertEquals(1, service.getById(id).getRuleVersion());
        e.setMerchantId(-1L);
        assertThrows(CoolException.class, () -> service.update(e));
    }
    @Test void deletesDraftAndItsStoreRelations() {
        Long id = service.add(coupon());
        assertTrue(service.delete(id));
        assertNull(service.getById(id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_store WHERE coupon_template_id=?", Integer.class, id));
    }
    @Test void publishedReferencesBlockDeletion() {
        Long id = service.add(coupon());
        jdbc.update("INSERT INTO coupon_publication(merchant_id,coupon_template_id,channel,audit_status,publish_status) VALUES(?,?,'PLATFORM',0,0)", merchantId, id);
        assertThrows(CoolException.class, () -> service.delete(id));
        assertNotNull(service.getById(id));
    }
    @Test void enabledCouponCannotBeDeleted() {
        var e = coupon(); e.setStatus(1);
        Long id = service.add(e);
        assertThrows(CoolException.class, () -> service.delete(id));
    }
    @Test void pendingReviewBlocksRuleChanges() {
        Long id = service.add(coupon());
        jdbc.update("INSERT INTO coupon_publication(merchant_id,coupon_template_id,channel,audit_status,publish_status) VALUES(?,?,'PLATFORM',1,0)", merchantId, id);
        var e = (CouponTemplateEntity) service.info(id); e.setName("修改审核中的券");
        assertThrows(CoolException.class, () -> service.update(e));
    }
    @Test void storeWithCouponAssociationCannotBeDeleted() {
        service.add(coupon());
        assertThrows(CoolException.class, () -> storeService.delete(storeId));
    }
    @Test void pageEndpointFiltersDraftsAndExposesReadableNames() throws Exception {
        Long id = service.add(coupon());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new com.cool.core.request.RequestParamsFilter()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/coupon/template/page")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"page\":1,\"size\":20,\"keyWord\":\"自动测试券_事务回滚\",\"status\":0,\"type\":\"CASH\",\"merchantId\":" + merchantId + "}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(1000))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.pagination.total").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.list[0].id").value(id.intValue()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.list[0].merchantName").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.list[0].storeNames[0]").isNotEmpty());
    }
    @Test void batchDeleteChecksEveryTemplateBeforeDeletingAnything() {
        Long deletable = service.add(coupon());
        var enabled = coupon(); enabled.setStatus(1); Long protectedId = service.add(enabled);
        assertThrows(CoolException.class, () -> service.delete(deletable, protectedId));
        assertNotNull(service.getById(deletable));
    }

    @Test void countsAllFourReferenceSourcesAndPreservesRelationTypeRules() {
        Long id = service.add(coupon());
        assertEquals(0, relations.references(id));
        jdbc.update("INSERT INTO coupon_publication(merchant_id,coupon_template_id,channel,audit_status,publish_status) VALUES(?,?,'PLATFORM',0,0)", merchantId, id);
        jdbc.update("INSERT INTO coupon_product(merchant_id,coupon_template_id,product_id,relation_type,quantity) VALUES(?,?,700001,2,2),(?,?,700002,1,1)", merchantId, id, merchantId, id);
        jdbc.update("INSERT INTO coupon_issue_batch(merchant_id,coupon_template_id,source_type) VALUES(?,?,'TEST')", merchantId, id);
        jdbc.update("INSERT INTO customer_coupon(merchant_id,customer_id,coupon_template_id,source_type,channel,code,valid_from,valid_to,rule_snapshot,issued_at) VALUES(?,1,?,'TEST','PLATFORM',?,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),'{}',NOW())", merchantId, id, java.util.UUID.randomUUID().toString());
        relations.lockTemplate(id);
        assertEquals(5, relations.references(id));
        assertEquals(1, relations.contents(id));
        assertEquals(1, relations.validContents(id, merchantId));
        assertEquals(0, relations.validContents(id, -1L));
        assertThrows(CoolException.class, () -> service.delete(id));
    }

    @Test void relationWritesKeepTimestampAndEmptyLookupsDoNotReturnOtherRecords() {
        Long id = service.add(coupon());
        assertNotNull(jdbc.queryForObject("SELECT create_time FROM coupon_store WHERE coupon_template_id=? AND store_id=?", java.sql.Timestamp.class, id, storeId));
        assertEquals(List.of(storeId), relations.storeIds(id));
        assertTrue(relations.merchantNames(List.of()).isEmpty());
        assertTrue(relations.storeNames(List.of()).isEmpty());
        assertTrue(relations.storeIds(null).isEmpty());
        assertTrue(relations.storeNames(java.util.Arrays.asList((Long) null)).isEmpty());
        assertTrue(relations.merchantNames(java.util.Arrays.asList((Long) null)).isEmpty());
        assertEquals(0, relations.deleteStores(null));
        assertEquals(List.of(storeId), relations.storeIds(id));
        assertEquals(0, relations.activePublications(null));
        assertEquals(0, relations.references(null));
        assertEquals(0, relations.contents(null));
        assertEquals(0, relations.validContents(id, null));
        assertEquals(1, relations.deleteStores(id));
        assertTrue(relations.storeIds(id).isEmpty());
        assertEquals(1, relations.insertStore(merchantId, id, storeId));
        assertEquals(List.of(storeId), relations.storeIds(id));
    }
}

