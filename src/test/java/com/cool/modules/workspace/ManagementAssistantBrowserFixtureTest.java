package com.cool.modules.workspace;

import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.ai.entity.ManagementAssistantTaskEntity;
import com.cool.modules.workspace.entity.WorkspaceGrantEntity;
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
 * @Description: 提供管理助手真实浏览器验收的隔离身份和测试数据并清理
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"sms-auth.access-key-id=features-test-unused","sms-auth.access-key-secret=features-test-unused","server.address=127.0.0.1"})
@EnabledIfEnvironmentVariable(named="PERKLY_MANAGEMENT_BROWSER_TEST",matches="true")
class ManagementAssistantBrowserFixtureTest extends FeatureModulesIntegrationTest {
    @LocalServerPort int port;
    @Autowired javax.sql.DataSource source;
    @Autowired com.cool.modules.base.service.sys.BaseSysPermsService perms;
    @Autowired com.cool.modules.ai.mapper.ManagementAssistantTaskMapper taskRows;
    @Autowired com.cool.modules.ai.config.AiProperties ai;
    @Test void serveManagement() throws Exception {
        try(var c=source.getConnection()){
            if(!c.getMetaData().getURL().contains("perkly_ai_regression_20261106"))throw new IllegalStateException("Requires dedicated AI regression DB");
        }
        var role=new BaseSysUserRoleEntity();role.setUserId(admin);role.setRoleId(1L);roleLinks.insert(role);
        var testCoupon=new com.cool.modules.coupon.entity.CouponTemplateEntity();testCoupon.setMerchantId(merchant);testCoupon.setName(prefix+"验收代金券");
        testCoupon.setType("CASH");testCoupon.setDeductAmount(java.math.BigDecimal.TEN);testCoupon.setMinSpend(new java.math.BigDecimal("50"));
        testCoupon.setSalePrice(java.math.BigDecimal.ZERO);testCoupon.setScopeType(0);testCoupon.setValidityType("AFTER_ISSUE");testCoupon.setValidDays(30);
        testCoupon.setPerCustomerLimit(1);testCoupon.setAllowCombine(0);testCoupon.setStatus(1);testCoupon.setRuleVersion(1);testCoupon.setIssuedCount(0L);coupons.insert(testCoupon);
        var adminPermissions=new ArrayList<String>(Arrays.asList(perms.getPerms(admin)));
        // Exercise the original approval controller's review-implies-read rule in this isolated fixture.
        adminPermissions.add("approval:center:review");
        String adminToken=token(admin,UserTypeEnum.ADMIN,adminPermissions.toArray(String[]::new));
        String ownerToken=token(owner,UserTypeEnum.MERCHANT);
        String staffToken=token(staff,UserTypeEnum.MERCHANT);
        keys.forEach(k->cache.set(k,cache.get(k),3600));
        TestTransaction.flagForCommit();TestTransaction.end();
        Path file=Path.of("logs/test/management-browser-session.json"),stop=Path.of("logs/test/management-browser-stop");
        Files.deleteIfExists(stop);
        try {
            var data=new LinkedHashMap<String,Object>();data.put("port",port);data.put("adminToken",adminToken);data.put("ownerToken",ownerToken);data.put("staffToken",staffToken);
            data.put("adminId",admin);data.put("ownerId",owner);data.put("staffId",staff);data.put("merchantId",merchant);data.put("storeId",store);data.put("prefix",prefix);
            data.put("categoryId",merchants.selectOneById(merchant).getCategoryId());data.put("storeTypeId",stores.selectOneById(store).getCategoryId());
            data.put("couponId",testCoupon.getId());data.put("couponName",testCoupon.getName());
            Files.writeString(file,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data));
            System.out.println("MANAGEMENT_BROWSER_READY modelConfigured="+ai.configured());
            long until=System.currentTimeMillis()+3600000;
            while(!Files.exists(stop) && System.currentTimeMillis()<until)Thread.sleep(500);
        } finally {
            Files.deleteIfExists(file);Files.deleteIfExists(stop);
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
                var ids=List.of(owner,staff,otherStaff,admin);
                publications.deleteByQuery(QueryWrapper.create().eq(com.cool.modules.coupon.entity.CouponPublicationEntity::getCouponTemplateId,testCoupon.getId()));
                coupons.deleteById(testCoupon.getId());
                taskRows.deleteByQuery(QueryWrapper.create().in(ManagementAssistantTaskEntity::getActorId,ids));
                grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").in(WorkspaceGrantEntity::getSubjectId,ids));
                grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"MERCHANT").eq(WorkspaceGrantEntity::getSubjectId,merchant));
                roleLinks.deleteByQuery(QueryWrapper.create().in(BaseSysUserRoleEntity::getUserId,ids));
                bindings.deleteByQuery(QueryWrapper.create().in(MerchantUserStoreEntity::getUserId,ids));
                users.deleteByQuery(QueryWrapper.create().in(BaseSysUserEntity::getId,ids));
                stores.deleteByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant));
                merchants.deleteById(merchant);
                categories.selectListByQuery(QueryWrapper.create().like(MerchantCategoryEntity::getName,prefix)).stream()
                    .filter(r->r.getName().startsWith(prefix)).forEach(r->categories.deleteById(r.getId()));
                storeTypes.selectListByQuery(QueryWrapper.create().like(MerchantStoreCategoryEntity::getName,prefix)).stream()
                    .filter(r->r.getName().startsWith(prefix)).forEach(r->storeTypes.deleteById(r.getId()));
                customers.deleteById(customer);customers.deleteById(otherCustomer);
            });
            System.out.println("MANAGEMENT_BROWSER_CLEANED");
        }
    }
}
