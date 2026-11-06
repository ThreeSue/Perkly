package com.cool.modules.promotion.service;
import com.cool.modules.promotion.entity.*;
import com.cool.modules.promotion.mapper.*;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.wallet.service.ActivityAudienceService;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在可信到店消费完成事务中记录唯一消费及当时适用的集章活动
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:48:06
 */
@Service @RequiredArgsConstructor
public class StampVisitService {
    private final CompletedVisitMapper visits;
    private final StampVisitMatchMapper matches;
    private final WalletActivityMapper activities;
    private final ConsumptionOrderMapper orders;
    private final CustomerCouponMapper coupons;
    private final WalletBillMapper bills;
    private final ActivityAudienceService audience;

    @Transactional(rollbackFor=Exception.class)
    public void consumption(Long id) {
        var order=orders.selectOneById(id);
        if(order==null || !"COMPLETED".equals(order.getStatus()))return;
        var coupon=coupons.selectOneById(order.getCustomerCouponId());
        if(coupon==null)return;
        capture("CONSUMPTION",id,coupon.getCustomerId(),order.getMerchantId(),order.getStoreId(),order.getCompletedAt());
    }
    @Transactional(rollbackFor=Exception.class)
    public void direct(Long id) {
        var bill=bills.selectOneById(id);
        if(bill==null || !"PAID".equals(bill.getStatus()) || bill.getConsumptionOrderId()!=null)return;
        capture("DIRECT",id,bill.getCustomerId(),bill.getMerchantId(),bill.getStoreId(),new Date());
    }
    private void capture(String source,Long id,Long customer,Long merchant,Long store,Date completed) {
        if(customer==null || visits.selectCountByQuery(QueryWrapper.create()
            .eq(CompletedVisitEntity::getSourceType,source).eq(CompletedVisitEntity::getSourceId,id))>0)return;
        var visit=new CompletedVisitEntity();
        visit.setSourceType(source);visit.setSourceId(id);visit.setCustomerId(customer);
        visit.setMerchantId(merchant);visit.setStoreId(store);visit.setCompletedAt(completed);
        // 完成时冻结命中版本；后续停用不会撤销已经赚取的章。
        var eligible=activities.selectListByQuery(QueryWrapper.create()
            .eq(WalletActivityEntity::getKind,"STAMP").eq(WalletActivityEntity::getStatus,"PUBLISHED")
            .le(WalletActivityEntity::getStartAt,completed).gt(WalletActivityEntity::getEndAt,completed)
            .orderBy(WalletActivityEntity::getId,true).forUpdate()).stream()
            .filter(a->!Boolean.FALSE.equals(a.getEnabled()) && audience.participates(a,store)).toList();
        if(eligible.isEmpty())return;
        visits.insert(visit);
        for(var activity:eligible) {
            var match=new StampVisitMatchEntity();
            match.setVisitId(visit.getId());match.setActivityId(activity.getId());
            match.setActivityVersion(audience.version(activity));match.setStatus("PENDING");matches.insert(match);
        }
    }
}
