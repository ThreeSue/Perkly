package com.cool.modules.promotion.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.promotion.entity.*;
import com.cool.modules.promotion.mapper.*;
import com.cool.modules.coupon.service.CouponTradeService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.support.service.UserFeedbackService.PageResult;
import com.cool.modules.wallet.entity.WalletActivityEntity;
import com.cool.modules.wallet.mapper.WalletActivityMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 异步推进唯一商家集章并以独立事务发放可重试奖励
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:50:25
 */
@Service @RequiredArgsConstructor @Slf4j
public class StampProgressService {
    private final StampVisitMatchMapper matches;
    private final CompletedVisitMapper visits;
    private final CustomerStampMapper stamps;
    private final StampProgressMapper progresses;
    private final StampRewardAttemptMapper attempts;
    private final StampCampaignMapper configs;
    private final WalletActivityMapper activities;
    private final UserInfoMapper customers;
    private final StampCampaignService campaigns;
    private final CouponTradeService trade;
    private final SupportAccess access;
    private final PlatformTransactionManager transactions;
    private void transaction(Runnable work) {
        var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.executeWithoutResult(s->work.run());
    }
    private void lockCustomer(Long id) {
        var user=customers.selectOneByQuery(QueryWrapper.create().eq(UserInfoEntity::getId,id).forUpdate());
        if(user==null || !Integer.valueOf(1).equals(user.getStatus()))throw new CoolException("顾客账号已失效");
    }
    private void advance(Long matchId) {
        var match=matches.selectOneByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getId,matchId).forUpdate());
        if(match==null || !"PENDING".equals(match.getStatus()))return;
        var visit=visits.selectOneById(match.getVisitId());lockCustomer(visit.getCustomerId());
        var c=campaigns.config(match.getActivityId());
        if(campaigns.goals(c).stream().noneMatch(g->g.merchantId().equals(visit.getMerchantId()) && g.storeIds().contains(visit.getStoreId())))
            throw new CoolException("消费命中记录与冻结目标不符");
        var progress=progresses.selectOneByQuery(QueryWrapper.create().eq(StampProgressEntity::getActivityId,match.getActivityId())
            .eq(StampProgressEntity::getCustomerId,visit.getCustomerId()).forUpdate());
        if(progress==null) {
            progress=new StampProgressEntity();progress.setActivityId(match.getActivityId());progress.setCustomerId(visit.getCustomerId());
            progress.setStampCount(0);progress.setRewardStatus("COLLECTING");progress.setAttempts(0);progresses.insert(progress);
        }
        if(stamps.selectCountByQuery(QueryWrapper.create().eq(CustomerStampEntity::getActivityId,match.getActivityId())
            .eq(CustomerStampEntity::getCustomerId,visit.getCustomerId()).eq(CustomerStampEntity::getMerchantId,visit.getMerchantId()))==0) {
            var stamp=new CustomerStampEntity();stamp.setActivityId(match.getActivityId());stamp.setCustomerId(visit.getCustomerId());
            stamp.setMerchantId(visit.getMerchantId());stamp.setVisitId(visit.getId());stamps.insert(stamp);
            progress.setStampCount(progress.getStampCount()+1);
            if(progress.getStampCount()==campaigns.goals(c).size()) {
                progress.setCompletedAt(visit.getCompletedAt());progress.setRewardStatus("PENDING");progress.setNextAttemptAt(new Date(0));
            }
            progresses.update(progress);
        }
        match.setStatus("DONE");match.setLastError("");matches.update(match);
    }
    private void grant(Long id) {
        var found=progresses.selectOneById(id);if(found==null)return;
        lockCustomer(found.getCustomerId());
        var p=progresses.selectOneByQuery(QueryWrapper.create().eq(StampProgressEntity::getId,id).forUpdate());
        if(!"PENDING".equals(p.getRewardStatus()))return;
        var c=configs.selectOneByQuery(QueryWrapper.create().eq(StampCampaignEntity::getActivityId,p.getActivityId()).forUpdate());
        if(c.getGrantedCount()>=c.getRewardStock())throw new CoolException("活动奖励库存不足，请补充活动库存");
        Long coupon=trade.issueTaskReward(p.getCustomerId(),c.getRewardTemplateId(),c.getRewardRuleVersion(),c.getRewardSnapshot());
        c.setGrantedCount(c.getGrantedCount()+1);configs.update(c);
        p.setRewardCouponId(coupon);p.setRewardStatus("ISSUED");p.setAttempts(p.getAttempts()+1);p.setLastError("");
        progresses.update(p);attempt(id,"SUCCESS","奖励已发放，持券编号 "+coupon);
    }
    private void attempt(Long id,String status,String message) {
        var row=new StampRewardAttemptEntity();row.setProgressId(id);row.setStatus(status);row.setMessage(message);attempts.insert(row);
    }
    private String message(Exception e) {
        var text=Objects.toString(e.getMessage(),"处理失败");return text.substring(0,Math.min(500,text.length()));
    }
    public void retryGrant(Long id,boolean operator) {
        if(operator)campaigns.permission();
        try {transaction(()->grant(id));}
        catch(Exception e) {
            transaction(()->{
                var p=progresses.selectOneByQuery(QueryWrapper.create().eq(StampProgressEntity::getId,id).forUpdate());
                if(p==null || !"PENDING".equals(p.getRewardStatus()))return;
                p.setAttempts(p.getAttempts()+1);p.setLastError(message(e));
                p.setNextAttemptAt(new Date(System.currentTimeMillis()+Math.min(3600000L,p.getAttempts()*60000L)));
                progresses.update(p);attempt(id,"FAILED",message(e));
            });
        }
    }
    public void tick() {
        for(var m:matches.selectListByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getStatus,"PENDING")
            .orderBy(StampVisitMatchEntity::getId,true).limit(100))) {
            try{transaction(()->advance(m.getId()));}
            catch(Exception e) {
                log.warn("集章消费记录 {} 待重试",m.getId(),e);
                transaction(()->{
                    var row=matches.selectOneByQuery(QueryWrapper.create().eq(StampVisitMatchEntity::getId,m.getId()).forUpdate());
                    if(row!=null && "PENDING".equals(row.getStatus())){row.setLastError(message(e));matches.update(row);}
                });
            }
        }
        for(var p:progresses.selectListByQuery(QueryWrapper.create().eq(StampProgressEntity::getRewardStatus,"PENDING")
            .le(StampProgressEntity::getNextAttemptAt,new Date()).orderBy(StampProgressEntity::getId,true).limit(100)))retryGrant(p.getId(),false);
    }
    public record ProgressView(Long id,Long activityId,String title,Long customerId,String customerName,Integer stampCount,Integer targetCount,
        String rewardStatus,Long rewardCouponId,Integer attempts,String lastError,List<Long> stampedMerchantIds,Date completedAt) {}
    private ProgressView view(StampProgressEntity p) {
        var a=activities.selectOneById(p.getActivityId());var user=customers.selectOneById(p.getCustomerId());
        return new ProgressView(p.getId(),p.getActivityId(),a.getTitle(),p.getCustomerId(),user==null?"历史顾客":Objects.toString(user.getNickName(),"顾客"),
            p.getStampCount(),campaigns.goals(campaigns.config(p.getActivityId())).size(),p.getRewardStatus(),p.getRewardCouponId(),p.getAttempts(),p.getLastError(),
            stamps.selectListByQuery(QueryWrapper.create().eq(CustomerStampEntity::getActivityId,p.getActivityId())
                .eq(CustomerStampEntity::getCustomerId,p.getCustomerId())).stream().map(CustomerStampEntity::getMerchantId).toList(),p.getCompletedAt());
    }
    public PageResult<ProgressView> records(Long activity,int page,int size) {
        campaigns.permission();campaigns.header(activity,false);SupportAccess.page(page,size);
        var data=progresses.paginate(page,size,QueryWrapper.create().eq(StampProgressEntity::getActivityId,activity).orderBy(StampProgressEntity::getId,false));
        return new PageResult<>(data.getRecords().stream().map(this::view).toList(),data.getTotalRow(),page,size);
    }
    public List<StampRewardAttemptEntity> attempts(Long id) {
        campaigns.permission();return attempts.selectListByQuery(QueryWrapper.create().eq(StampRewardAttemptEntity::getProgressId,id)
            .orderBy(StampRewardAttemptEntity::getId,false).limit(100));
    }
    public record CustomerActivity(StampCampaignService.View activity,ProgressView progress) {}
    public PageResult<CustomerActivity> customerList(Long customer,int page,int size) {
        access.customer(customer);SupportAccess.page(page,size);
        var joined=progresses.selectListByQuery(QueryWrapper.create().eq(StampProgressEntity::getCustomerId,customer));
        var ids=joined.stream().map(StampProgressEntity::getActivityId).toList();
        var rows=activities.selectListByQuery(QueryWrapper.create().eq(WalletActivityEntity::getKind,"STAMP").orderBy(WalletActivityEntity::getId,false))
            .stream().filter(a->!ids.isEmpty() && ids.contains(a.getId()) || "PUBLISHED".equals(a.getStatus())
                && !Boolean.FALSE.equals(a.getEnabled()) && a.getEndAt().after(new Date())).toList();
        int start=(int)Math.min((long)(page-1)*size,rows.size());
        var data=rows.subList(start,Math.min(start+size,rows.size())).stream().map(a->new CustomerActivity(campaigns.customerView(a.getId()),
            joined.stream().filter(p->p.getActivityId().equals(a.getId())).findFirst().map(this::view).orElse(null))).toList();
        return new PageResult<>(data,rows.size(),page,size);
    }
}
