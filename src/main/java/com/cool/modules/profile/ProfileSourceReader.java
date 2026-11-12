package com.cool.modules.profile;

import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.core.exception.CoolException;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.cool.modules.profile.ProfileFacts.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 从真实交易来源读取指定顾客的去重画像事实而不访问聊天私信
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:05:36
 */
@Service @RequiredArgsConstructor
public class ProfileSourceReader {
    private final UserInfoMapper users;
    private final CustomerCouponMapper coupons;
    private final PurchaseOrderMapper purchases;
    private final ConsumptionOrderMapper orders;
    private final CouponRedemptionMapper redemptions;
    private final WalletBillMapper bills;
    private final WalletTransactionMapper transactions;
    private final MerchantInfoMapper merchants;
    private final com.cool.modules.merchant.mapper.MerchantCategoryMapper industryCategories;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public record Content(String key,String text,List<String> facts) {}
    public record Evidence(Batch batch,ProfileStatistics.Summary statistics,List<Content> contents,Map<Long,String> categoryNames) {}
    @Transactional(readOnly=true)
    public Evidence read(Long customer,Instant cutoff) {
        if(customer==null || customer<=0 || users.selectOneById(customer)==null)throw new CoolException("用户不存在",404);
        Map<Long,Long> categories=new HashMap<>();
        var owned=coupons.selectListByQuery(QueryWrapper.create().eq(CustomerCouponEntity::getCustomerId,customer));
        var couponIds=owned.stream().map(CustomerCouponEntity::getId).toList();
        var completed=couponIds.isEmpty()?List.<ConsumptionOrderEntity>of():orders.selectListByQuery(QueryWrapper.create()
            .in(ConsumptionOrderEntity::getCustomerCouponId,couponIds).eq(ConsumptionOrderEntity::getStatus,"COMPLETED"));
        var redeemed=couponIds.isEmpty()?List.<CouponRedemptionEntity>of():redemptions.selectListByQuery(QueryWrapper.create()
            .in(CouponRedemptionEntity::getCustomerCouponId,couponIds));
        var paid=bills.selectListByQuery(QueryWrapper.create().eq(WalletBillEntity::getCustomerId,customer).eq(WalletBillEntity::getStatus,"PAID"));
        List<Source> source=new ArrayList<>();List<Issue> issues=new ArrayList<>();
        for(var row:completed) {
            if(row.getSettlementBillId()!=null && paid.stream().noneMatch(b->b.getId().equals(row.getSettlementBillId()))) {
                issues.add(new Issue("LINKED_BILL_NOT_OWNED_OR_PAID",List.of("C:"+row.getId())));continue;
            }
            List<String> links=new ArrayList<>();if(row.getSettlementBillId()!=null)links.add("B:"+row.getSettlementBillId());
            if(row.getRedemptionId()!=null)links.add("R:"+row.getRedemptionId());
            source.add(new Source("C:"+row.getId(),customer,row.getMerchantId(),row.getStoreId(),category(categories,row.getMerchantId()),
                instant(row.getCompletedAt()),row.getConsumptionAmount(),row.getReceivedAmount(),row.getDiscountAmount(),2,links,false));
        }
        for(var row:redeemed) {
            if(row.getConsumptionOrderId()!=null && source.stream().noneMatch(o->o.key().equals("C:"+row.getConsumptionOrderId()))) {
                issues.add(new Issue("REDEMPTION_ORDER_NOT_OWNED_OR_COMPLETED",List.of("R:"+row.getId())));continue;
            }
            List<String> links=row.getConsumptionOrderId()==null?List.of():List.of("C:"+row.getConsumptionOrderId());
            source.add(new Source("R:"+row.getId(),customer,row.getMerchantId(),row.getStoreId(),category(categories,row.getMerchantId()),
                instant(row.getRedeemedAt()),cents(row.getConsumptionAmount()),null,cents(row.getDiscountAmount()),1,links,false));
        }
        for(var row:paid) {
            var tx=row.getTransactionId()==null?null:transactions.selectOneById(row.getTransactionId());
            boolean trusted=tx!=null&&"SUCCESS".equals(tx.getStatus())&&Objects.equals(tx.getBillId(),row.getId())
                &&Objects.equals(tx.getCustomerId(),customer)&&tx.getCreateTime()!=null;
            List<String> links=row.getConsumptionOrderId()==null?List.of():List.of("C:"+row.getConsumptionOrderId());
            if(row.getConsumptionOrderId()!=null && source.stream().noneMatch(o->o.key().equals("C:"+row.getConsumptionOrderId()))) {
                issues.add(new Issue("LINKED_CONSUMPTION_NOT_OWNED_OR_COMPLETED",List.of("B:"+row.getId())));continue;
            }
            source.add(new Source("B:"+row.getId(),customer,row.getMerchantId(),row.getStoreId(),category(categories,row.getMerchantId()),
                instant(trusted?tx.getCreateTime():row.getUpdateTime()),row.getAmount(),row.getPaidAmount(),
                row.getPaidAmount()==null||row.getAmount()==null||row.getPaidAmount()>row.getAmount()?null:row.getAmount()-row.getPaidAmount(),3,links,!trusted));
        }
        Batch visits=ProfileFacts.visits(customer,source,cutoff);List<Fact> facts=new ArrayList<>(visits.facts());issues.addAll(visits.issues());
        var successful=purchases.selectListByQuery(QueryWrapper.create().eq(PurchaseOrderEntity::getCustomerId,customer)
            .eq(PurchaseOrderEntity::getOrderStatus,"PAID").eq(PurchaseOrderEntity::getPaymentStatus,"SUCCESS"));
        for(var p:successful) {
            Long amount=cents(p.getTotalAmount());Instant at=instant(p.getPaidAt());
            if(amount==null||at==null||at.isAfter(cutoff)){issues.add(new Issue("PURCHASE_AMOUNT_OR_TIME_INVALID",List.of("P:"+p.getId())));continue;}
            facts.add(new Fact("P:"+p.getId(),amount==0?Kind.FREE_CLAIM:Kind.PAID_PURCHASE,customer,p.getMerchantId(),null,
                category(categories,p.getMerchantId()),at,amount,amount,null,List.of("P:"+p.getId()),false));
        }
        Instant start=cutoff.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().minusDays(179).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        var window=facts.stream().filter(f->!f.occurredAt().isBefore(start)).sorted(Comparator.comparing(Fact::occurredAt).thenComparing(Fact::key)).toList();
        List<Content> contents=new ArrayList<>();
        for(Long merchant:window.stream().map(Fact::merchantId).filter(Objects::nonNull).distinct().toList()) {
            var row=merchants.selectOneById(merchant);if(row==null)continue;
            String text=row.getName()+"；"+Objects.toString(row.getIntroduction(),"");
            contents.add(new Content("M:"+merchant,text.substring(0,Math.min(1000,text.length())),
                window.stream().filter(f->merchant.equals(f.merchantId())).map(Fact::key).toList()));
        }
        // 仅从已核销持券的历史规则白名单提取内容，不将券码或整份快照交给模型。
        for(var held:owned) {
            var references=redeemed.stream().filter(r->Objects.equals(r.getCustomerCouponId(),held.getId())).map(r->"R:"+r.getId()).toList();
            var matched=window.stream().filter(f->f.kind()==Kind.VISIT&&f.evidence().stream().anyMatch(references::contains)).map(Fact::key).toList();
            if(matched.isEmpty()||held.getRuleSnapshot()==null)continue;
            try {
                var rule=json.readTree(held.getRuleSnapshot());StringBuilder text=new StringBuilder();
                for(String field:List.of("name","description"))if(rule.path(field).isTextual())text.append(rule.path(field).asText()).append("；");
                for(String field:List.of("contentItems","contents"))if(rule.path(field).isArray())for(var item:rule.path(field)) {
                    if(item.path("name").isTextual())text.append(item.path("name").asText()).append("；");
                }
                if(!text.isEmpty())contents.add(new Content("COUPON_HISTORY:"+held.getId(),text.substring(0,Math.min(1000,text.length())),matched));
            }catch(Exception invalid){issues.add(new Issue("INVALID_COUPON_SNAPSHOT",references));}
        }
        Map<Long,String> names=new LinkedHashMap<>();
        for(Long id:window.stream().map(Fact::categoryId).filter(Objects::nonNull).distinct().toList()) {
            var category=industryCategories.selectOneById(id);names.put(id,category==null?"历史分类":category.getName());
        }
        return new Evidence(new Batch(window,List.copyOf(issues)),ProfileStatistics.summarize(window,cutoff),List.copyOf(contents),names);
    }
    private Long category(Map<Long,Long> cache,Long merchant) {
        if(merchant==null)return null;
        if(!cache.containsKey(merchant)){var row=merchants.selectOneById(merchant);cache.put(merchant,row==null?null:row.getCategoryId());}
        return cache.get(merchant);
    }
    private Instant instant(Date date){return date==null?null:date.toInstant();}
}
