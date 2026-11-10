package com.cool.modules.workspace;

import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.base.entity.sys.BaseSysUserRoleEntity;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.entity.CouponPublicationEntity;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.workspace.entity.WorkspaceGrantEntity;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.transaction.TestTransaction;
import java.nio.file.*;
import java.util.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供真实浏览器联调的隔离测试账号和HTTP服务并在结束时清理夹具
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 19:55:00
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"sms-auth.access-key-id=features-test-unused", "sms-auth.access-key-secret=features-test-unused", "server.address=127.0.0.1"})
@EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_BROWSER_TEST", matches = "true")
class CouponAssistantBrowserFixtureTest extends FeatureModulesIntegrationTest {
    @LocalServerPort int port;
    @Autowired javax.sql.DataSource source;
    @Autowired com.cool.modules.base.service.sys.BaseSysPermsService perms;
    @Autowired com.cool.modules.approval.mapper.ApprovalMapper audits;
    @Autowired com.cool.modules.workspace.mapper.WorkspaceCouponStoreMapper couponStores;

    @Test void serveIsolatedBrowserFlow() throws Exception {
        try (var connection = source.getConnection()) {
            if (!connection.getMetaData().getURL().contains("perkly_ai_regression_20261106"))
                throw new IllegalStateException("Browser fixture requires the dedicated regression database");
        }
        Long category = merchants.selectOneById(merchant).getCategoryId();
        Long storeType = stores.selectOneById(store).getCategoryId();
        var operatorRole = new BaseSysUserRoleEntity(); operatorRole.setUserId(admin); operatorRole.setRoleId(1L); roleLinks.insert(operatorRole);
        var coupon = coupon("页面联调代金券");
        if ("true".equals(System.getenv("PERKLY_ASSISTANT_RECOVERY_BROWSER_TEST"))) {
            coupon.setStatus(1); coupons.update(coupon);
            var publication = new CouponPublicationEntity();
            publication.setMerchantId(merchant); publication.setCouponTemplateId(coupon.getId()); publication.setChannel("PLATFORM");
            publication.setAuditStatus(2); publication.setPublishStatus(1); publication.setSubmittedVersion(1); publication.setPublishedVersion(1);
            publications.insertSelective(publication);
        }
        var removable = coupon("页面联调删除券");
        var adminToken = token(admin, UserTypeEnum.ADMIN, perms.getPerms(admin));
        var ownerToken = token(owner, UserTypeEnum.MERCHANT);
        keys.forEach(key -> cache.set(key, cache.get(key), 1800));
        TestTransaction.flagForCommit(); TestTransaction.end();
        Path config = Path.of("logs/test/assistant-browser-session.json");
        Path stop = Path.of("logs/test/assistant-browser-stop");
        Files.deleteIfExists(stop);
        try {
            var data = Map.of("port", port, "merchantId", merchant, "couponId", coupon.getId(), "deleteId", removable.getId(),
                "adminId", admin, "ownerId", owner, "adminToken", adminToken, "ownerToken", ownerToken, "prefix", prefix);
            Files.writeString(config, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data));
            System.out.println("BROWSER_FIXTURE_READY port=" + port);
            long until = System.currentTimeMillis() + 20 * 60_000;
            while (!Files.exists(stop) && System.currentTimeMillis() < until) Thread.sleep(500);
        } finally {
            Files.deleteIfExists(config); Files.deleteIfExists(stop);
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                var ids = List.of(owner, staff, otherStaff, admin);
                audits.deleteByQuery(QueryWrapper.create().eq(com.cool.modules.approval.entity.AuditRecordEntity::getMerchantId, merchant));
                publications.deleteByQuery(QueryWrapper.create().eq(CouponPublicationEntity::getMerchantId, merchant));
                couponStores.deleteByQuery(QueryWrapper.create().eq(com.cool.modules.workspace.entity.WorkspaceCouponStoreEntity::getMerchantId, merchant));
                coupons.deleteByQuery(QueryWrapper.create().eq(CouponTemplateEntity::getMerchantId, merchant));
                grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType, "USER").in(WorkspaceGrantEntity::getSubjectId, ids));
                grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType, "MERCHANT").eq(WorkspaceGrantEntity::getSubjectId, merchant));
                roleLinks.deleteByQuery(QueryWrapper.create().in(BaseSysUserRoleEntity::getUserId, ids));
                bindings.deleteByQuery(QueryWrapper.create().in(MerchantUserStoreEntity::getUserId, ids));
                users.deleteByQuery(QueryWrapper.create().in(BaseSysUserEntity::getId, ids));
                stores.deleteByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId, merchant));
                merchants.deleteById(merchant); categories.deleteById(category); storeTypes.deleteById(storeType);
                customers.deleteById(customer); customers.deleteById(otherCustomer);
            });
            org.junit.jupiter.api.Assertions.assertNull(merchants.selectOneById(merchant));
            System.out.println("BROWSER_FIXTURE_CLEANED");
        }
    }

    private CouponTemplateEntity coupon(String name) {
        var row = new CouponTemplateEntity(); row.setMerchantId(merchant); row.setName(name); row.setType("CASH");
        row.setSalePrice(java.math.BigDecimal.ZERO); row.setDeductAmount(java.math.BigDecimal.TEN); row.setScopeType(0);
        row.setValidityType("AFTER_ISSUE"); row.setValidDays(30); row.setIssuedCount(0L); row.setRuleVersion(1); row.setStatus(0);
        coupons.insertSelective(row); return row;
    }
}
