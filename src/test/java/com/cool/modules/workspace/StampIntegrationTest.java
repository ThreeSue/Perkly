package com.cool.modules.workspace;
import com.cool.modules.promotion.entity.*;
import com.cool.modules.promotion.mapper.*;
import com.cool.modules.promotion.service.*;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.service.*;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.service.WalletActivityService;
import com.cool.modules.merchant.entity.*;
import com.cool.core.exception.CoolException;
import com.cool.core.enums.UserTypeEnum;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证集章配置渠道限制完成事件进度奖励重试并回归原五模块
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:59:38
 */
class StampIntegrationTest extends FeatureModulesIntegrationTest {
    @Autowired StampCampaignService stampCampaign;
    @Autowired StampVisitService stampVisits;
    @Autowired StampProgressService stampProgress;
    @Autowired StampProgressMapper stampProgressRows;
    @Autowired CustomerStampMapper stampRows;
    @Autowired CompletedVisitMapper visitRows;
    @Autowired StampVisitMatchMapper matchRows;
    @Autowired StampRewardAttemptMapper attemptRows;
    @Autowired StampCampaignMapper stampConfigs;
    @Autowired CouponTemplateService templateService;
    @Autowired CouponPublicationService publicationService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    void ops() {
        var u=principal(admin,UserTypeEnum.ADMIN,"stamp:manage","activity:manage","coupon:template:add","coupon:template:update");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
    }
    CouponTemplateEntity reward(boolean reviewed) {
        ops();var e=new CouponTemplateEntity();e.setMerchantId(merchant);e.setName(prefix+"集章奖励");
        e.setType("CASH");e.setDeductAmount(new BigDecimal("10"));e.setScopeType(0);
        e.setValidityType("AFTER_ISSUE");e.setValidDays(30);e.setIssueLimit(2L);e.setPerCustomerLimit(1);
        e=(CouponTemplateEntity)stampCampaign.makeReward(e);
        if(reviewed)e=(CouponTemplateEntity)stampCampaign.reviewReward(new StampCampaignService.RewardReview(e.getId(),1,true,null));
        return e;
    }
    List<StampCampaignService.Goal> targets(int count,List<Long> extraMerchants) {
        var goals=new ArrayList<StampCampaignService.Goal>();
        goals.add(new StampCampaignService.Goal(merchant,List.of(store,otherStore)));
        for(int i=1;i<count;i++) {
            var m=merchants.selectOneById(merchant);m.setId(null);m.setName(prefix+"商家"+i);merchants.insertSelective(m);extraMerchants.add(m.getId());
            var s=stores.selectOneById(store);s.setId(null);s.setMerchantId(m.getId());s.setName(prefix+"分店"+i);stores.insertSelective(s);
            goals.add(new StampCampaignService.Goal(m.getId(),List.of(s.getId())));
        }
        return goals;
    }
    Long draft(CouponTemplateEntity reward,List<StampCampaignService.Goal> goals,long stock) {
        return stampCampaign.save(admin,new StampCampaignService.Input(null,null,prefix+"五商家集章","集齐发券",
            new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()+3600000),goals,reward.getId(),stock));
    }
    Long invitedOwner(Long business) {
        if(business.equals(merchant)){activityPermission(owner,"activity:participate");return owner;}
        Long original=merchant;merchant=business;
        try {
            Long actor=account("invite"+business,"OWNER",null);
            for(var menu:auth.catalog()) {var g=new com.cool.modules.workspace.entity.WorkspaceGrantEntity();g.setSubjectType("MERCHANT");g.setSubjectId(business);g.setMenuId(menu.getId());g.setAllowed(true);grants.insert(g);}
            activityPermission(actor,"activity:participate");return actor;
        } finally {merchant=original;}
    }
    void publish(Long id){
        stampCampaign.publish(admin,new StampCampaignService.Command(id,stampCampaign.view(id).version()));
        for(var goal:stampCampaign.goals(stampCampaign.config(id))) {
            Long actor=invitedOwner(goal.merchantId());
            stampCampaign.answer(actor,new StampCampaignService.Answer(id,stampCampaign.view(id).version(),true,goal.storeIds(),null));
        }
    }
    @Test void stampInvitationsGateConsumptionAndRejectStaleResponses() throws Exception {
        var r=reward(true);var goals=targets(2,new ArrayList<>());var id=draft(r,goals,10);
        stampCampaign.publish(admin,new StampCampaignService.Command(id,stampCampaign.view(id).version()));
        assertEquals("CONFIRMING",stampCampaign.view(id).status());
        assertTrue(stampProgress.customerList(customer,1,100).list().stream().noneMatch(a->a.activity().id().equals(id)));
        completed(customer,goals.get(0),store,"PAID");
        assertEquals(0,matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,id)));
        Long actor=invitedOwner(merchant);int version=stampCampaign.view(id).version();
        var merchantToken=token(actor,UserTypeEnum.MERCHANT);
        mvc.perform(get("/merchant/governance/wallet/activities/stamp-invitation").header("Authorization",merchantToken).param("id",id.toString()))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.status").value("CONFIRMING"));
        mvc.perform(post("/merchant/governance/wallet/activities/stamp-confirm").header("Authorization",merchantToken).contentType("application/json")
            .content("{\"id\":"+id+",\"version\":"+version+",\"accepted\":false,\"reason\":\"时间待调整\"}"))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.answer.status").value("REJECTED"));
        ops();version=stampCampaign.view(id).version();final int answerVersion=version;
        assertThrows(CoolException.class,()->stampCampaign.answer(actor,new StampCampaignService.Answer(id,answerVersion,true,goals.get(1).storeIds(),null)));
        stampCampaign.answer(actor,new StampCampaignService.Answer(id,version,false,List.of(),"时间不合适"));
        assertEquals("REJECTED",stampCampaign.view(id).confirmations().get(0).status());
        assertThrows(CoolException.class,()->stampCampaign.answer(actor,new StampCampaignService.Answer(id,answerVersion,true,List.of(store),null)));
        stampCampaign.withdraw(new StampCampaignService.Command(id,stampCampaign.view(id).version()));
        assertEquals("DRAFT",stampCampaign.view(id).status());
        assertThrows(CoolException.class,()->stampCampaign.answer(actor,new StampCampaignService.Answer(id,stampCampaign.view(id).version(),true,List.of(store),null)));
        stampCampaign.publish(admin,new StampCampaignService.Command(id,stampCampaign.view(id).version()));
        assertTrue(stampCampaign.view(id).confirmations().stream().allMatch(c->"PENDING".equals(c.status())));
        stampCampaign.answer(actor,new StampCampaignService.Answer(id,stampCampaign.view(id).version(),true,List.of(store),null));
        assertEquals("CONFIRMING",stampCampaign.view(id).status());
        Long second=invitedOwner(goals.get(1).merchantId());
        stampCampaign.answer(second,new StampCampaignService.Answer(id,stampCampaign.view(id).version(),true,goals.get(1).storeIds(),null));
        assertEquals("PUBLISHED",stampCampaign.view(id).status());
        assertEquals(List.of(store),stampCampaign.goals(stampCampaign.config(id)).get(0).storeIds());
        assertThrows(CoolException.class,()->activity.participate(actor,new com.cool.modules.wallet.service.ActivityAudienceService.ParticipationInput(id,stampCampaign.view(id).version(),true,List.of())));
    }
    WalletBillEntity completed(Long customer,StampCampaignService.Goal goal,Long storeId,String status) {
        var b=new WalletBillEntity();b.setBillNo("W"+UUID.randomUUID().toString().replace("-",""));b.setOperatorId(admin);
        b.setRequestId(UUID.randomUUID().toString());b.setMerchantId(goal.merchantId());b.setStoreId(storeId);
        b.setAmount(100L);b.setCustomerId(customer);b.setStatus(status);billRows.insertSelective(b);stampVisits.direct(b.getId());return b;
    }
    @Test void stampConfigurationRejectsInvalidGoalsForeignStoresAndStaleVersion() {
        var r=reward(true);var extras=new ArrayList<Long>();var goals=targets(2,extras);
        assertThrows(CoolException.class,()->draft(r,List.of(goals.get(0),goals.get(0)),10));
        assertThrows(CoolException.class,()->draft(r,List.of(goals.get(0),new StampCampaignService.Goal(extras.get(0),List.of(store))),10));
        var id=draft(r,goals,10);assertEquals(2,stampCampaign.view(id).goals().size());
        assertThrows(CoolException.class,()->stampCampaign.publish(admin,new StampCampaignService.Command(id,0)));
        publish(id);
        var changed=coupons.selectOneById(r.getId());changed.setDeductAmount(new BigDecimal("20"));coupons.update(changed);
        assertEquals(0,new BigDecimal("10").compareTo(stampCampaign.view(id).reward().getDeductAmount()),"已发布奖励展示保留规则快照");
        assertThrows(CoolException.class,()->stampCampaign.save(admin,new StampCampaignService.Input(id,2,"不能改",null,
            new Date(),new Date(System.currentTimeMillis()+60000),goals,r.getId(),5L)));
        assertEquals(1000L,activity.quote(store,1000).amount(),"集章不是储值折扣");
        assertTrue(activity.publicList(store).isEmpty());
        assertTrue(activity.page(admin,true,1,100).list().isEmpty());
        assertThrows(CoolException.class,()->activity.schedule(new WalletActivityService.Schedule(id,false,new Date(),new Date(System.currentTimeMillis()+60000))));
    }
    @Test void stampRewardRequiresAuditAndCannotBecomePublicOrBeClaimed() {
        var r=reward(false);var id=draft(r,targets(2,new ArrayList<>()),10);
        assertThrows(CoolException.class,()->publish(id));
        stampCampaign.reviewReward(new StampCampaignService.RewardReview(r.getId(),1,true,null));publish(id);
        assertThrows(CoolException.class,()->trade.create(customer,r.getId(),1,"task_claim_forbidden"));
        assertThrows(CoolException.class,()->trade.claim(customer,r.getId(),1,"task_claim_forbidden"));
        var copy=(CouponTemplateEntity)templateService.info(r.getId());copy.setIssueChannel("PUBLIC");
        assertThrows(CoolException.class,()->templateService.update(copy));
        var publicCopy=(CouponTemplateEntity)templateService.info(r.getId());publicCopy.setId(null);publicCopy.setIssueChannel("PUBLIC");publicCopy.setName(prefix+"公开券");
        Long publicId=templateService.add(publicCopy);
        var publicReward=(CouponTemplateEntity)templateService.info(publicId);
        assertThrows(CoolException.class,()->draft(publicReward,targets(2,new ArrayList<>()),10));
    }
    @Test void stampCaptureHonorsTimePauseOwnershipCanonicalSourceAndCompletedOnly() {
        var r=reward(true);var goals=targets(2,new ArrayList<>());var id=draft(r,goals,10);publish(id);
        completed(customer,goals.get(0),store,"PENDING");completed(customer,goals.get(0),store,"CANCELLED");
        assertEquals(0,visitRows.selectCountByQuery(QueryWrapper.create().eq(CompletedVisitEntity::getCustomerId,customer)));
        var paid=completed(customer,goals.get(0),store,"PAID");stampVisits.direct(paid.getId());
        assertEquals(1,matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,id)));
        var v=stampCampaign.view(id);stampCampaign.schedule(new StampCampaignService.Schedule(id,v.version(),false,v.startAt(),v.endAt()));
        completed(customer,goals.get(0),otherStore,"PAID");
        assertEquals(1,matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,id)));
        v=stampCampaign.view(id);final var currentVersion=v.version();
        assertThrows(CoolException.class,()->stampCampaign.schedule(new StampCampaignService.Schedule(id,currentVersion,true,new Date(System.currentTimeMillis()+60000),new Date(System.currentTimeMillis()+120000))));
        completed(customer,goals.get(0),store,"PAID");
        assertEquals(1,matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,id)));
        assertTrue(stampProgress.customerList(otherCustomer,1,100).list().stream().noneMatch(a->a.activity().id().equals(id)));
    }
    @Test void stampHttpUsesActualCustomerAndRequiresOperatorPermission() throws Exception {
        var r=reward(true);Long id=draft(r,targets(2,new ArrayList<>()),10);publish(id);
        var adminToken=token(admin,UserTypeEnum.ADMIN,"stamp:manage");
        mvc.perform(get("/admin/promotions/stamps/info").header("Authorization",adminToken).param("id",id.toString()))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.title").value(prefix+"五商家集章"));
        var appToken=token(customer,UserTypeEnum.APP);
        mvc.perform(get("/app/promotions/stamps/page").header("Authorization",appToken).param("customerId",otherCustomer.toString()))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.list[0].progress").isEmpty());
        mvc.perform(post("/app/promotions/stamps/claim").header("Authorization",appToken).contentType("application/json").content("{\"customerId\":"+otherCustomer+"}"))
            .andExpect(result->assertNotEquals(1000,cn.hutool.json.JSONUtil.parseObj(result.getResponse().getContentAsString()).getInt("code")));
        platform();assertThrows(CoolException.class,()->stampCampaign.options());
    }
    @Test void stampRealPaymentsAndZeroExtraContentRedemptionsCaptureTrustedOwner() {
        var reward=reward(true);var goals=targets(2,new ArrayList<>());Long activityId=draft(reward,goals,10);publish(activityId);
        for(String type:List.of("PACKAGE","EXPERIENCE","EXCHANGE")) {
            var e=new CouponTemplateEntity();e.setMerchantId(merchant);e.setName(prefix+type);e.setType(type);
            e.setSalePrice(BigDecimal.ZERO);e.setValidityType("AFTER_ISSUE");e.setValidDays(30);e.setStatus(1);e.setScopeType(0);
            var item=new com.cool.modules.coupon.controller.admin.params.CouponContentParam();
            item.setName(type+"项目");item.setType("EXPERIENCE");item.setQuantity(1);e.setContentItems(List.of(item));
            Long template=templateService.add(e);publicationService.submitAudit(template,1);publicationService.audit(template,1,true);publicationService.publish(template,1);
            var claimed=trade.claim(customer,template,1,UUID.randomUUID().toString());
            Long couponId=((Number)((List<?>)claimed.get("couponIds")).get(0)).longValue();
            String code=(String)trade.walletInfo(customer,couponId).get("code");
            long before=matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,activityId));
            var input=new ConsumptionOrderService.PrepareInput(store,code,null,null,UUID.randomUUID().toString());
            var completed=consumption.redeemContent(staff,input);consumption.redeemContent(staff,input);
            assertEquals("COMPLETED",completed.status());assertEquals(0L,completed.receivedAmount());
            assertEquals(before+1,matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,activityId)));
        }
        String code=settlementCode();
        long before=matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,activityId));
        var bill=wallet.createBill(staff,new com.cool.modules.wallet.service.PlatformWalletService.BillInput("stamp_linked_open",store,10000L,null));
        bill=wallet.applyCoupon(staff,new com.cool.modules.wallet.service.PlatformWalletService.ApplyCoupon(bill.getId(),1,code));
        wallet.consumptionPay(customer,settlementPay("stamp_linked_pay",bill.getBillNo(),"SIMULATED",8000L));
        assertEquals(before+1,matchRows.selectCountByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getActivityId,activityId)),"关联消费不能在两套账单重复计章");
        var direct=wallet.createBill(staff,new com.cool.modules.wallet.service.PlatformWalletService.BillInput("stamp_direct_open",store,1000L,null));
        wallet.consumptionPay(otherCustomer,settlementPay("stamp_direct_pay",direct.getBillNo(),"SIMULATED",1000L));
        var directVisit=visitRows.selectOneByQuery(QueryWrapper.create().eq(CompletedVisitEntity::getSourceType,"DIRECT").eq(CompletedVisitEntity::getSourceId,direct.getId()));
        assertEquals(otherCustomer,directVisit.getCustomerId());
    }
    @Test void stampFiveMerchantsStockFailureRetryAndConcurrentIssuanceAreDurable() throws Exception {
        var extra=new ArrayList<Long>();var r=reward(true);var goals=targets(5,extra);var id=draft(r,goals,1);publish(id);
        // 同一商家两门店、多次消费不重复集章；两位顾客均达标。
        for(var goal:goals){completed(customer,goal,goal.storeIds().get(0),"PAID");completed(otherCustomer,goal,goal.storeIds().get(0),"PAID");}
        completed(customer,goals.get(0),otherStore,"PAID");
        var duplicate=completed(customer,goals.get(0),store,"PAID");stampVisits.direct(duplicate.getId());
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();org.springframework.test.context.transaction.TestTransaction.end();
        try {
            stampProgress.tick();stampProgress.tick();
            var states=stampProgressRows.selectListByQuery(QueryWrapper.create().eq(StampProgressEntity::getActivityId,id));
            assertEquals(2,states.size());assertTrue(states.stream().allMatch(p->p.getStampCount()==5));
            assertEquals(10,stampRows.selectCountByQuery(QueryWrapper.create().eq(CustomerStampEntity::getActivityId,id)));
            assertEquals(1,states.stream().filter(p->"ISSUED".equals(p.getRewardStatus())).count());
            var pending=states.stream().filter(p->"PENDING".equals(p.getRewardStatus())).findFirst().orElseThrow();
            assertTrue(pending.getLastError().contains("库存"));
            assertTrue(attemptRows.selectCountByQuery(QueryWrapper.create().eq(StampRewardAttemptEntity::getProgressId,pending.getId()))>0);
            ops();stampCampaign.stock(admin,new StampCampaignService.Stock(id,"stamps_stock_retry",1L));
            stampCampaign.stock(admin,new StampCampaignService.Stock(id,"stamps_stock_retry",1L));
            assertEquals(2,stampCampaign.view(id).rewardStock());
            var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var a=pool.submit(()->stampProgress.retryGrant(pending.getId(),false));
                var b=pool.submit(()->stampProgress.retryGrant(pending.getId(),false));a.get();b.get();
            } finally{pool.shutdownNow();}
            stampProgress.tick();
            var granted=stampProgressRows.selectOneById(pending.getId());
            assertEquals("ISSUED",granted.getRewardStatus());assertNotNull(granted.getRewardCouponId());
            assertEquals(2,stampCampaign.view(id).grantedCount());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM customer_coupon WHERE coupon_template_id=?",Long.class,r.getId()));
            assertEquals(1,stampProgress.customerList(customer,1,20).list().size());
            assertEquals(customer,stampProgress.customerList(customer,1,20).list().get(0).progress().customerId());
            assertEquals(otherCustomer,stampProgress.customerList(otherCustomer,1,20).list().get(0).progress().customerId());
            var held=trade.walletInfo(pending.getCustomerId(),granted.getRewardCouponId());
            assertEquals("TASK_REWARD",held.get("sourceType"));assertNotNull(held.get("qrCodeImage"));
        } finally {
            // 清理仅本例提交的专用夹具，保留数据库中其他业务和测试数据。
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
                jdbc.update("DELETE a FROM stamp_reward_attempt a JOIN stamp_progress p ON a.progress_id=p.id WHERE p.activity_id=?",id);
                jdbc.update("DELETE FROM stamp_progress WHERE activity_id=?",id);
                jdbc.update("DELETE FROM customer_stamp WHERE activity_id=?",id);
                jdbc.update("DELETE FROM stamp_visit_match WHERE activity_id=?",id);
                jdbc.update("DELETE FROM completed_visit WHERE customer_id IN (?,?)",customer,otherCustomer);
                jdbc.update("DELETE FROM stamp_stock_entry WHERE activity_id=?",id);jdbc.update("DELETE FROM stamp_campaign WHERE activity_id=?",id);
                jdbc.update("DELETE FROM customer_coupon WHERE customer_id IN (?,?)",customer,otherCustomer);
                jdbc.update("DELETE FROM wallet_bill WHERE operator_id=?",admin);
                jdbc.update("DELETE FROM coupon_publication WHERE coupon_template_id=?",r.getId());
                jdbc.update("DELETE FROM coupon_template WHERE id=?",r.getId());
                jdbc.update("DELETE FROM audit_record WHERE merchant_id=?",merchant);
                for(Long m:extra){stores.deleteByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,m));merchants.deleteById(m);}
            });
            cleanupActivityNoticeFixtures(admin);
        }
    }
}
