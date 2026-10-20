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
    @Autowired private com.cool.modules.coupon.service.CouponPublicationService publications;
    @Autowired private com.cool.modules.merchant.service.AppDiscoveryService discovery;
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

    private Long enabledCoupon() {
        var e = coupon(); e.setStatus(1);
        return service.add(e);
    }

    @Test void reviewedPublishedCouponAppearsInAppAndDisappearsAfterUnpublish() {
        Long id = enabledCoupon();
        assertThrows(CoolException.class, () -> discovery.coupon(id));
        publications.submitAudit(id, 1);
        assertEquals(1, ((CouponTemplateEntity)service.info(id)).getPublication().get("auditStatus"));
        publications.audit(id, 1, true);
        assertThrows(CoolException.class, () -> discovery.coupon(id));
        publications.publish(id, 1);
        assertEquals(id, ((Number)discovery.coupon(id).get("id")).longValue());
        var query = new com.cool.modules.merchant.controller.app.req.DiscoveryPageReq();
        query.setKeyword("自动测试券_事务回滚"); query.setMerchantId(merchantId);
        assertTrue(discovery.coupons(query).getList().stream().anyMatch(row -> id.equals(((Number)row.get("id")).longValue())));
        assertNotNull(jdbc.queryForObject("SELECT published_snapshot FROM coupon_publication WHERE coupon_template_id=?", String.class, id));
        assertEquals(jdbc.queryForObject("SELECT submitted_snapshot FROM coupon_publication WHERE coupon_template_id=?", String.class, id),
                jdbc.queryForObject("SELECT published_snapshot FROM coupon_publication WHERE coupon_template_id=?", String.class, id));
        publications.unpublish(id, 1);
        assertThrows(CoolException.class, () -> discovery.coupon(id));
        publications.publish(id, 1);
        assertNotNull(discovery.coupon(id));
    }

    @Test void draftCannotSubmitAndUnreviewedCouponCannotPublish() {
        Long draft = service.add(coupon());
        assertThrows(CoolException.class, () -> publications.submitAudit(draft, 1));
        Long enabled = enabledCoupon();
        assertThrows(CoolException.class, () -> publications.publish(enabled, 1));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_publication WHERE coupon_template_id IN (?,?)", Integer.class, draft, enabled));
    }

    @Test void duplicateAndOutOfOrderActionsAreRejected() {
        Long id = enabledCoupon();
        assertThrows(CoolException.class, () -> publications.audit(id, 1, true));
        assertThrows(CoolException.class, () -> publications.unpublish(id, 1));
        assertThrows(CoolException.class, () -> publications.withdrawAudit(id, 1));
        publications.submitAudit(id, 1);
        assertThrows(CoolException.class, () -> publications.submitAudit(id, 1));
        assertThrows(CoolException.class, () -> publications.audit(id, 1, null));
        publications.audit(id, 1, true);
        assertThrows(CoolException.class, () -> publications.audit(id, 1, true));
        publications.publish(id, 1);
        assertThrows(CoolException.class, () -> publications.publish(id, 1));
        assertThrows(CoolException.class, () -> publications.submitAudit(id, 1));
    }

    @Test void rejectionAndWithdrawalAllowCorrectionAndResubmission() {
        Long id = enabledCoupon();
        publications.submitAudit(id, 1);
        publications.audit(id, 1, false);
        assertThrows(CoolException.class, () -> publications.publish(id, 1));
        publications.submitAudit(id, 1);
        publications.withdrawAudit(id, 1);
        var e = (CouponTemplateEntity)service.info(id); e.setName("修改后重新审核券");
        service.update(e);
        assertEquals(2, service.getById(id).getRuleVersion());
        publications.submitAudit(id, 2);
        publications.audit(id, 2, true);
        publications.publish(id, 2);
        assertEquals("修改后重新审核券", discovery.coupon(id).get("name"));
    }

    @Test void modifiedRulesInvalidateEarlierApprovalAndStaleClientVersion() {
        Long id = enabledCoupon();
        publications.submitAudit(id, 1); publications.audit(id, 1, true);
        var e = (CouponTemplateEntity)service.info(id); e.setSalePrice(new BigDecimal("9.90")); service.update(e);
        assertThrows(CoolException.class, () -> publications.publish(id, 1));
        assertThrows(CoolException.class, () -> publications.publish(id, 2));
        publications.submitAudit(id, 2);
        assertThrows(CoolException.class, () -> publications.audit(id, 1, true));
        publications.audit(id, 2, true); publications.publish(id, 2);
        assertEquals(990L, ((Number)discovery.coupon(id).get("salePrice")).longValue());
    }

    @Test void pendingAndPublishedCouponsMustBeWithdrawnOrUnpublishedBeforeDisabling() {
        Long id = enabledCoupon(); publications.submitAudit(id, 1);
        var pending = (CouponTemplateEntity)service.info(id); pending.setStatus(2);
        assertThrows(CoolException.class, () -> service.update(pending));
        publications.audit(id, 1, true); publications.publish(id, 1);
        var published = (CouponTemplateEntity)service.info(id); published.setStatus(2);
        assertThrows(CoolException.class, () -> service.update(published));
        publications.unpublish(id, 1);
        var disabled = (CouponTemplateEntity)service.info(id); disabled.setStatus(2);
        assertTrue(service.update(disabled));
        assertThrows(CoolException.class, () -> publications.publish(id, 1));
    }

    @Test void inactiveStoresOrMerchantsBlockPublication() {
        Long id = enabledCoupon(); publications.submitAudit(id, 1); publications.audit(id, 1, true);
        jdbc.update("UPDATE store SET status=0 WHERE id=?", storeId);
        assertThrows(CoolException.class, () -> publications.publish(id, 1));
        jdbc.update("UPDATE store SET status=1 WHERE id=?", storeId);
        jdbc.update("UPDATE merchant_info SET status=0 WHERE id=?", merchantId);
        assertThrows(CoolException.class, () -> publications.publish(id, 1));
    }

    @Test void allStoreCouponRequiresAtLeastOneOpenStore() {
        var e = coupon(); e.setStatus(1); e.setScopeType(0);
        Long id = service.add(e);
        jdbc.update("UPDATE store SET status=0 WHERE merchant_id=?", merchantId);
        assertThrows(CoolException.class, () -> publications.submitAudit(id, 1));
    }

    @Test void publicationControllerRequiresAdminWithTemplateUpdatePermission() {
        Long id = enabledCoupon();
        var controller = new com.cool.modules.coupon.controller.admin.AdminCouponTemplateController(publications);
        var req = new com.cool.modules.coupon.controller.admin.params.CouponPublicationParam();
        req.setId(id); req.setVersion(1);
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            assertThrows(CoolException.class, () -> controller.submitAudit(req));
            var appUser = new com.cool.core.security.jwt.JwtUser(123L, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("coupon:template:update")), true);
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(appUser, null, appUser.getAuthorities()));
            assertThrows(CoolException.class, () -> controller.submitAudit(req));
            var admin = new com.cool.core.security.jwt.JwtUser(123L, "operator-test", "unused", List.of(), true);
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
            assertThrows(CoolException.class, () -> controller.submitAudit(req));
            admin.setPerms(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("coupon:template:update")));
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
            assertNotNull(controller.submitAudit(req));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void httpPublicationActionsExposeReviewedCouponWithoutChangingAppContract() throws Exception {
        Long id = enabledCoupon();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new com.cool.core.request.RequestParamsFilter()).build();
        var admin = new com.cool.core.security.jwt.JwtUser(123L, "operator-test", "unused",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("coupon:template:update")), true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        try {
            for (String action : List.of("submitAudit", "audit", "publish")) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/coupon/template/" + action)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"version\":1,\"approved\":true}"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(1000));
            }
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/app/coupon/info").param("id", String.valueOf(id)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(1000))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.id").value(id.intValue()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.stores[0].name").isNotEmpty());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/coupon/template/unpublish")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"id\":" + id + ",\"version\":1}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.publication.publishStatus").value(2));
            publications.submitAudit(id, 1);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/coupon/template/withdrawAudit")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"id\":" + id + ",\"version\":1}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.publication.auditStatus").value(0));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
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
        assertEquals(0, relations.validContents(id, merchantId)); // 不存在的商品不能作为有效内容。
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

    private com.cool.modules.coupon.controller.admin.params.CouponContentParam item(String name) {
        var item = new com.cool.modules.coupon.controller.admin.params.CouponContentParam();
        item.setName(name); item.setType("GOODS"); item.setQuantity(2); item.setItemDescription("150克／份");
        return item;
    }

    @Test void createsProductAndContentTogetherAndEnablesPackage() {
        var e = coupon(); e.setType("PACKAGE"); e.setStatus(1);
        e.setContentItems(List.of(item("自动测试肥牛")));
        Long id = service.add(e);
        var saved = (CouponTemplateEntity) service.info(id);
        assertEquals("自动测试肥牛", saved.getContentItems().get(0).getName());
        assertNotNull(saved.getContentItems().get(0).getProductId());
        assertEquals(2, saved.getContentItems().get(0).getQuantity());
        assertEquals(1, relations.validContents(id, merchantId));
        assertEquals(1, service.productOptions(merchantId).stream().filter(p -> p.get("name").equals("自动测试肥牛")).count());
        service.update(saved);
        assertEquals(1, service.getById(id).getRuleVersion());
    }

    @Test void contentChangeIncrementsVersionAndPublishedCouponRejectsChanges() {
        var e = coupon(); e.setType("EXPERIENCE"); e.setContentItems(List.of(item("自动测试体验")));
        Long id = service.add(e);
        var saved = (CouponTemplateEntity) service.info(id);
        saved.getContentItems().get(0).setQuantity(3); service.update(saved);
        assertEquals(2, service.getById(id).getRuleVersion());
        jdbc.update("INSERT INTO coupon_publication(merchant_id,coupon_template_id,channel,audit_status,publish_status) VALUES(?,?,'PLATFORM',1,0)", merchantId, id);
        saved = (CouponTemplateEntity) service.info(id);
        saved.setContentItems(List.of(item("不应创建的新项目")));
        var rejected = saved;
        assertThrows(CoolException.class, () -> service.update(rejected));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE name='不应创建的新项目'", Integer.class));
        assertEquals(3, ((CouponTemplateEntity)service.info(id)).getContentItems().get(0).getQuantity());
    }

    @Test void rejectsForeignDisabledDuplicateAndInvalidContent() {
        Long otherMerchant = jdbc.queryForObject("SELECT id FROM merchant_info WHERE id<>? LIMIT 1", Long.class, merchantId);
        Long foreign = relations.insertProduct(otherMerchant, "其他商家项目", "SERVICE");
        var e = coupon(); e.setType("EXCHANGE");
        var selected = item("客户端伪造名称"); selected.setProductId(foreign); e.setContentItems(List.of(selected));
        assertThrows(CoolException.class, () -> service.add(e));
        Long own = relations.insertProduct(merchantId, "本商家项目", "SERVICE");
        selected.setProductId(own); e.setContentItems(List.of(selected, selected));
        assertThrows(CoolException.class, () -> service.add(e));
        e.setContentItems(List.of(selected)); selected.setQuantity(0);
        assertThrows(CoolException.class, () -> service.add(e));
        selected.setQuantity(1); jdbc.update("UPDATE product SET status=0 WHERE id=?", own);
        assertThrows(CoolException.class, () -> service.add(e));
    }

    @Test void draftCanClearContentsAndSwitchToCashAtomically() {
        var e = coupon(); e.setType("PACKAGE"); e.setContentItems(List.of(item("保留商品项目")));
        Long id = service.add(e);
        var saved = (CouponTemplateEntity)service.info(id);
        saved.setType("CASH"); saved.setDeductAmount(new BigDecimal("20")); saved.setContentItems(List.of());
        service.update(saved);
        assertTrue(((CouponTemplateEntity)service.info(id)).getContentItems().isEmpty());
        assertEquals(0, relations.contents(id));
        assertEquals(1, service.productOptions(merchantId).stream().filter(p -> p.get("name").equals("保留商品项目")).count());
    }

    @Test void adminAddAcceptsNestedContentsAndReturnsNames() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new com.cool.core.request.RequestParamsFilter()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/coupon/template/add")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"merchantId\":" + merchantId + ",\"name\":\"接口套餐测试\",\"type\":\"PACKAGE\",\"validityType\":\"AFTER_ISSUE\",\"validDays\":30,\"status\":1,\"contentItems\":[{\"name\":\"接口肥牛\",\"type\":\"GOODS\",\"quantity\":2}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(1000));
        Long id = jdbc.queryForObject("SELECT id FROM coupon_template WHERE name='接口套餐测试'", Long.class);
        assertEquals("接口肥牛", ((CouponTemplateEntity)service.info(id)).getContentItems().get(0).getName());
    }
}

