package com.cool.modules.workspace;

import com.cool.modules.profile.*;
import com.cool.modules.profile.mapper.*;
import com.cool.modules.ai.mapper.AiSettingsMapper;
import com.cool.core.enums.UserTypeEnum;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 隔离库验证画像来源接口授权快照与重复任务合并
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:55:00
 */
@TestPropertySource(properties={"perkly.profile.worker-enabled=false"})
class ProfileIntegrationTest extends FeatureModulesIntegrationTest {
    static { if(!Objects.toString(System.getenv("SPRING_DATASOURCE_URL"),"").contains("/perkly_ai_regression_20261106?"))throw new IllegalStateException("必须显式指定隔离回归库"); }
    @Autowired DataSource dataSource;
    @Autowired ProfileSourceReader reader;
    @Autowired ProfileService profiles;
    @Autowired ProfileJobMapper profileJobs;
    @Autowired AiSettingsMapper aiSettings;
    @Autowired com.cool.modules.wallet.mapper.WalletTransactionMapper walletTransactions;
    @Autowired com.cool.modules.coupon.mapper.PurchaseOrderMapper purchaseRows;
    @Autowired ProfileSnapshotMapper snapshotRows;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper profileJson;
    @BeforeEach void profileGuard() throws Exception {
        try(var c=dataSource.getConnection()){assertEquals("perkly_ai_regression_20261106",c.getCatalog(),"只允许隔离测试库");}
    }
    @Test void profileColdStartQueueAndSnapshot() {
        assertEquals("NOT_BUILT",profiles.view(customer).state());
        profiles.request(customer,true);profiles.request(customer,true);
        var j=profileJobs.selectOneById(customer);assertEquals(1L,j.getRequestedVersion());
        var settings=aiSettings.selectOneById(1L);settings.setEnabled(true);aiSettings.update(settings);
        assertTrue(profiles.runOne());
        var view=profiles.view(customer);assertEquals("COMPLETED",view.state());assertEquals(0,view.evidence().statistics().windows().get(2).visits());
        profiles.request(customer,false);assertEquals(1L,profileJobs.selectOneById(customer).getRequestedVersion());
    }
    @Test void profileOperatorPermissionAndMerchantIsolation() throws Exception {
        String platformToken=token(admin,UserTypeEnum.ADMIN,"profile:read");
        mvc.perform(get("/admin/profile/info").header("Authorization",platformToken).param("customerId",customer.toString())).andExpect(status().isOk()).andExpect(jsonPath("data.customerId").value(customer));
        String forbidden=token(admin,UserTypeEnum.ADMIN);
        mvc.perform(get("/admin/profile/info").header("Authorization",forbidden).param("customerId",customer.toString())).andExpect(jsonPath("code").value(403));
        String merchantToken=token(owner,UserTypeEnum.MERCHANT,"profile:read");
        mvc.perform(get("/admin/profile/info").header("Authorization",merchantToken).param("customerId",customer.toString())).andExpect(status().isForbidden());
    }
    @Test void profileCompletedWalletPaymentHasExactTimeAndMoney() {
        wallet.recharge(customer,new com.cool.modules.wallet.service.PlatformWalletService.Recharge(prefix+"topup",5000L,null));
        var bill=wallet.createBill(staff,new com.cool.modules.wallet.service.PlatformWalletService.BillInput(prefix+"bill",store,1800L,null));
        wallet.consumptionPay(customer,settlementPay(prefix+"pay",bill.getBillNo(),"WALLET",1800));
        var evidence=reader.read(customer,Instant.now().plusSeconds(1));
        var window=evidence.statistics().windows().get(0);
        assertEquals(1,window.visits());assertEquals(1800,window.consumptionCents());assertEquals(0,evidence.statistics().approximateTimeVisits());
        assertEquals(0,reader.read(otherCustomer,Instant.now().plusSeconds(1)).statistics().windows().get(0).visits());
    }
    @Test void profileRecommendationApiFiltersCurrentCustomerEligibility() throws Exception {
        var coupon=new com.cool.modules.coupon.entity.CouponTemplateEntity();coupon.setMerchantId(merchant);coupon.setName(prefix+"公开推荐券");coupon.setType("CASH");coupon.setDeductAmount(java.math.BigDecimal.TEN);coupon.setSalePrice(java.math.BigDecimal.ZERO);coupon.setScopeType(0);coupon.setValidityType("AFTER_ISSUE");coupon.setValidDays(30);coupon.setPerCustomerLimit(1);coupon.setStatus(1);coupon.setRuleVersion(1);coupon.setIssuedCount(0L);coupons.insertSelective(coupon);
        var publication=new com.cool.modules.coupon.entity.CouponPublicationEntity();publication.setMerchantId(merchant);publication.setCouponTemplateId(coupon.getId());publication.setChannel("PLATFORM");publication.setAuditStatus(2);publication.setPublishStatus(1);publication.setPublishedVersion(1);publication.setPublishedAt(new Date());publications.insertSelective(publication);
        String customerToken=token(customer,UserTypeEnum.APP);
        String input="{\"kind\":\"COUPON\",\"merchantId\":"+merchant+",\"customerId\":"+otherCustomer+",\"sort\":\"PRICE_ASC\"}";
        mvc.perform(post("/app/recommendation/page").header("Authorization",customerToken).contentType("application/json").content(input)).andExpect(status().isOk()).andExpect(jsonPath("data.total").value(1)).andExpect(jsonPath("data.list[0].id").value(coupon.getId())).andExpect(jsonPath("data.list[0].salePrice").value(0)).andExpect(jsonPath("data.locationMode").value("NO_LOCATION"));
        trade.claim(customer,coupon.getId(),1,UUID.randomUUID().toString());
        mvc.perform(post("/app/recommendation/page").header("Authorization",customerToken).contentType("application/json").content(input)).andExpect(jsonPath("data.total").value(0));
    }
    @Test void profileCorrectionPreservesOriginalEvidence() throws Exception {
        var row=new ProfileSnapshotEntity();row.setCustomerId(customer);row.setRevision(1L);row.setCutoff(new Date());row.setUpdatedAt(new Date());row.setModelVersion("synthetic-fixture");row.setPromptVersion(ProfileModel.VERSION);
        row.setEvidenceJson(profileJson.writeValueAsString(reader.read(customer,Instant.now())));
        row.setInterpretationJson(profileJson.writeValueAsString(new ProfileModel.Interpretation(List.of(new ProfileModel.Tag("咖啡","SYNTHETIC","虚构咖啡标签",List.of("TEST"))),"NO_CONSUMPTION")));snapshotRows.insert(row);
        profiles.correct(customer,List.of("咖啡"));assertTrue(profiles.view(customer).interpretation().tags().isEmpty());assertEquals(List.of("咖啡"),profiles.view(customer).excludedLabels());
        assertEquals(row.getInterpretationJson(),snapshotRows.selectOneById(customer).getInterpretationJson());
        assertThrows(com.cool.core.exception.CoolException.class,()->profiles.correct(customer,List.of("杜撰收入")));
        profiles.correct(customer,List.of());assertEquals(1,profiles.view(customer).interpretation().tags().size());
    }
    @Autowired com.cool.modules.ai.config.AiProperties modelConfig;
    @Test void profileRealModelUsesGroundedBusinessContents() {
        Assumptions.assumeTrue(modelConfig.configured(),"本机未配置真实模型");
        var merchantRow=merchants.selectOneById(merchant);merchantRow.setName("画像测试咖啡店");merchantRow.setIntroduction("现磨咖啡、拿铁、美式咖啡和烘焙面包");merchants.update(merchantRow);
        wallet.recharge(customer,new com.cool.modules.wallet.service.PlatformWalletService.Recharge(prefix+"model",5000L,null));
        var bill=wallet.createBill(staff,new com.cool.modules.wallet.service.PlatformWalletService.BillInput(prefix+"modelbill",store,2500L,null));
        wallet.consumptionPay(customer,settlementPay(prefix+"modelpay",bill.getBillNo(),"WALLET",2500));
        var settings=aiSettings.selectOneById(1L);settings.setEnabled(true);aiSettings.update(settings);
        profiles.request(customer,true);assertTrue(profiles.runOne());
        var view=profiles.view(customer);assertEquals("COMPLETED",view.state());assertEquals(2500,view.evidence().statistics().windows().get(0).consumptionCents());
        assertNotNull(ProfileModel.validate(view.interpretation(),view.evidence()));
        assertEquals(ProfileModel.VERSION,view.promptVersion());
    }
    @Test void profileSixScenariosAreComputedFromSourceRows() throws Exception {
        StringBuilder report=new StringBuilder("# 画像六场景隔离验证\n\n全部为本次事务中新建的虚拟顾客和虚构消费，测试结束回滚。统计由 ProfileSourceReader 计算，不写死画像输出。\n\n|场景|完成消费|免费领取|已知消费分|数据充分度|\n|---|---:|---:|---:|---|\n");
        String[] labels={"咖啡规律消费","聚餐消费","多领少用","刚完成海鲜消费","偏好改变","新用户"};
        int[][] offsets={{28,21,14,7},{45,30,15},{},{0},{120,100,80,12,6,3},{}};
        for(int scenario=0;scenario<labels.length;scenario++) {
            Long person=customer();
            syntheticBusiness(labels[scenario]);
            for(int offset:offsets[scenario]) {
                if(scenario==4&&offset==12)syntheticBusiness("近期转向轻食");
                syntheticVisit(person,offset,scenario==1?24000:2500);
            }
            if(scenario==2)for(int i=0;i<12;i++) {
                var p=new com.cool.modules.coupon.entity.PurchaseOrderEntity();p.setOrderNo(prefix+scenario+"claim"+i);p.setCustomerId(person);p.setMerchantId(merchant);p.setChannel("APP");p.setOrderStatus("PAID");p.setPaymentStatus("SUCCESS");p.setTotalAmount(java.math.BigDecimal.ZERO);p.setPaidAt(new Date());p.setCreateTime(new Date());purchaseRows.insert(p);
            }
            var e=reader.read(person,Instant.now().plusSeconds(1));var w=e.statistics().windows().get(2);
            assertEquals(offsets[scenario].length,w.visits());assertEquals(scenario==2?12:0,w.freeClaims());
            assertEquals((long)offsets[scenario].length*(scenario==1?24000:2500),w.consumptionCents());
            if(scenario==0)assertEquals(7.0,w.categories().get(0).meanGapDays());
            if(scenario==4)assertEquals(2,w.categories().size());
            if(scenario==2||scenario==5)assertEquals("NO_CONSUMPTION",e.statistics().sufficiency());
            report.append("|").append(labels[scenario]).append("|").append(w.visits()).append("|").append(w.freeClaims()).append("|").append(w.consumptionCents()).append("|").append(e.statistics().sufficiency()).append("|\n");
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("logs/test/profile-six-scenarios.md"),report);
    }
    void syntheticBusiness(String label) {
        var category=new com.cool.modules.merchant.entity.MerchantCategoryEntity();category.setName(prefix+label);category.setSort(0);category.setStatus(1);categories.insertSelective(category);
        var type=stores.selectOneById(store).getCategoryId();
        var m=new com.cool.modules.merchant.entity.MerchantInfoEntity();m.setName(prefix+label);m.setIntroduction(label);m.setCategoryId(category.getId());m.setContactName("隔离模拟");m.setContactPhone("13800000000");m.setStatus(1);merchants.insertSelective(m);merchant=m.getId();
        store=store(type);staff=account(UUID.randomUUID().toString(),"STAFF",store);
    }
    void syntheticVisit(Long person,int days,long amount) {
        var b=new com.cool.modules.wallet.entity.WalletBillEntity();b.setBillNo(UUID.randomUUID().toString());b.setOperatorId(staff);b.setRequestId(UUID.randomUUID().toString());b.setMerchantId(merchant);b.setStoreId(store);b.setCustomerId(person);b.setAmount(amount);b.setPaidAmount(amount);b.setStatus("PAID");billRows.insert(b);
        var tx=new com.cool.modules.wallet.entity.WalletTransactionEntity();tx.setCustomerId(person);tx.setRequestId(UUID.randomUUID().toString());tx.setType("DIRECT");tx.setStatus("SUCCESS");tx.setBillId(b.getId());tx.setMerchantId(merchant);tx.setStoreId(store);tx.setOriginalAmount(amount);tx.setAmount(amount);tx.setDiscountAmount(0L);tx.setBalanceAfter(0L);tx.setCreateTime(Date.from(Instant.now().minus(Duration.ofDays(days))));walletTransactions.insert(tx);
        tx.setCreateTime(Date.from(Instant.now().minus(Duration.ofDays(days))));walletTransactions.update(tx);
        b.setTransactionId(tx.getId());billRows.update(b);
    }
}
