package com.cool.modules.ai.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.workspace.entity.WorkspaceCouponStoreEntity;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.workspace.service.WorkspaceDataScope;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

import static com.cool.modules.ai.service.CouponAssistantService.selected;
import static com.mybatisflex.core.query.QueryMethods.sum;
import static com.cool.modules.coupon.entity.table.PurchaseOrderItemEntityTableDef.PURCHASE_ORDER_ITEM_ENTITY;
import static com.cool.modules.coupon.entity.table.CouponTemplateEntityTableDef.COUPON_TEMPLATE_ENTITY;

import com.cool.modules.ai.service.CouponAssistantService.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 执行优惠券范围过滤和真实交易统计，不解析对话或写入业务
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 19:49:00
 */
@Service
@RequiredArgsConstructor
public class CouponAssistantQueries {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> TYPES = Set.of("CASH", "DISCOUNT", "FULL_REDUCTION", "PACKAGE", "EXPERIENCE", "EXCHANGE");
    private final SupportAccess access;
    private final WorkspaceAuthorization auth;
    private final WorkspaceDataScope scope;
    private final CouponTemplateMapper templates;
    private final PurchaseOrderItemMapper purchases;
    private final CouponRedemptionMapper redemptions;
    private final com.cool.modules.merchant.mapper.MerchantInfoMapper merchants;
    private final com.cool.modules.merchant.mapper.MerchantStoreMapper merchantStores;
    private record Range(LocalDate start, LocalDate end) {
        Date from() { return Date.from(start.atStartOfDay(ZONE).toInstant()); }
        Date to() { return Date.from(end.plusDays(1).atStartOfDay(ZONE).toInstant()); }
        String label() { return start + " 至 " + end + "（北京时间，含首尾日期）"; }
    }

    @Transactional(readOnly = true)
    public void priceFilter(QueryWrapper query, PriceFilter price) {
        if (price == null) return;
        String mode = Objects.requireNonNullElse(price.mode(), "ANY");
        if (!Set.of("ANY", "FREE", "PAID").contains(mode)) throw new CoolException("售价条件无效，请说明免费、付费或价格范围");
        for (BigDecimal value : new BigDecimal[]{price.min(), price.max()})
            if (value != null && (value.signum() < 0 || value.stripTrailingZeros().scale() > 2
                || value.compareTo(new BigDecimal("99999999.99")) > 0)) throw new CoolException("售价范围须为非负金额，最多两位小数");
        if (price.min() != null && price.max() != null && (price.min().compareTo(price.max()) > 0
            || price.min().compareTo(price.max()) == 0 && (Boolean.FALSE.equals(price.minInclusive()) || Boolean.FALSE.equals(price.maxInclusive()))))
            throw new CoolException("售价上下界冲突，请重新说明价格范围");
        if ("FREE".equals(mode)) query.eq(CouponTemplateEntity::getSalePrice, BigDecimal.ZERO);
        if ("PAID".equals(mode)) query.gt(CouponTemplateEntity::getSalePrice, BigDecimal.ZERO);
        if (price.min() != null) {
            if (Boolean.FALSE.equals(price.minInclusive())) query.gt(CouponTemplateEntity::getSalePrice, price.min());
            else query.ge(CouponTemplateEntity::getSalePrice, price.min());
        }
        if (price.max() != null) {
            if (Boolean.FALSE.equals(price.maxInclusive())) query.lt(CouponTemplateEntity::getSalePrice, price.max());
            else query.le(CouponTemplateEntity::getSalePrice, price.max());
        }
    }

    public String priceLabel(PriceFilter price) {
        if (price == null) return "全部售价";
        var labels = new ArrayList<String>();
        if ("FREE".equals(price.mode())) labels.add("免费领取（售价为0元）");
        if ("PAID".equals(price.mode())) labels.add("付费领取（售价大于0元）");
        if (price.min() != null) labels.add("售价" + (Boolean.FALSE.equals(price.minInclusive()) ? "大于" : "不低于") + price.min().toPlainString() + "元");
        if (price.max() != null) labels.add("售价" + (Boolean.FALSE.equals(price.maxInclusive()) ? "小于" : "不高于") + price.max().toPlainString() + "元");
        return labels.isEmpty() ? "全部售价" : String.join("且", labels);
    }

    public Reply countCoupons(Intent intent, QueryWrapper base, Long merchant, Long store, String queryToken) {
        long total = templates.selectCountByQuery(base.clone());
        var filtered = base.clone();
        var price = intent.query() == null ? null : intent.query().price();
        priceFilter(filtered, price);
        long matched = templates.selectCountByQuery(filtered.clone());
        BigDecimal percentage = total == 0 ? null : BigDecimal.valueOf(matched).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        String group = intent.query() == null ? "TYPE" : Objects.requireNonNullElse(intent.query().groupBy(), "TYPE");
        var groups = new ArrayList<GroupCount>();
        if ("TYPE".equals(group)) {
            var counts = new TreeMap<String, Long>();
            templates.selectListByQuery(filtered.clone().select(CouponTemplateEntity::getType))
                .forEach(row -> counts.merge(Objects.toString(row.getType(), "未分类"), 1L, Long::sum));
            counts.forEach((type, count) -> groups.add(new GroupCount(typeName(type), count)));
        } else if ("PRICE".equals(group)) {
            groups.add(new GroupCount("免费领取", templates.selectCountByQuery(filtered.clone().eq(CouponTemplateEntity::getSalePrice, BigDecimal.ZERO))));
            groups.add(new GroupCount("付费领取", templates.selectCountByQuery(filtered.clone().gt(CouponTemplateEntity::getSalePrice, BigDecimal.ZERO))));
        } else if ("STATUS".equals(group)) {
            for (int status = 0; status <= 2; status++)
                groups.add(new GroupCount(statusName(status), templates.selectCountByQuery(filtered.clone().eq(CouponTemplateEntity::getStatus, status))));
        }
        var conditions = new ArrayList<String>();
        conditions.add(scopeLabel(merchant, store));
        if (intent.couponName() != null && !intent.couponName().isBlank()) conditions.add("券名含「" + intent.couponName() + "」");
        if (intent.type() != null) conditions.add(typeName(intent.type()));
        if (intent.status() != null) conditions.add(statusName(intent.status()));
        if (intent.auditStatus() != null) conditions.add(List.of("未提交", "待审核", "审核通过", "已驳回").get(intent.auditStatus()));
        if (intent.publishStatus() != null) conditions.add(List.of("未上架", "已上架", "已下架").get(intent.publishStatus()));
        String range = String.join("、", conditions);
        String text = "统计范围：" + range + "。\n" + priceLabel(price) + "：" + matched + " 款。"
            + "\n基准总数：" + total + " 款（保留上述范围，仅去掉售价条件）。"
            + (total == 0 ? "\n基准总数为0，占比不适用。" : "\n占比：" + percentage.toPlainString() + "%（" + matched + " / " + total + "）。")
            + "\n这里统计优惠券款数，不是发行或领取张数。"
            + (groups.isEmpty() ? "" : "\n符合条件的分布：" + String.join("，", groups.stream().map(g -> g.label() + " " + g.count() + " 款").toList()) + "。");
        return new Reply(text, List.of(), null, List.of(), null, null, false, List.of(), null, null,
            queryToken, new CountResult(matched, total, percentage, priceLabel(price), range, groups));
    }

    public void filters(QueryWrapper query, Long merchant, Intent intent) {
        if (intent.type() != null) {
            if (!TYPES.contains(intent.type())) throw new CoolException("请选择有效的优惠券类型");
            query.eq(CouponTemplateEntity::getType, intent.type());
        }
        if (intent.couponName() != null && !intent.couponName().isBlank())
            query.like(CouponTemplateEntity::getName, intent.couponName().strip());
        if (intent.status() != null) {
            if (!Set.of(0, 1, 2).contains(intent.status())) throw new CoolException("模板状态无效");
            query.eq(CouponTemplateEntity::getStatus, intent.status());
        }
        if (intent.auditStatus() == null && intent.publishStatus() == null) return;
        int audit = Objects.requireNonNullElse(intent.auditStatus(), 0);
        int publish = Objects.requireNonNullElse(intent.publishStatus(), 0);
        if (!Set.of(0, 1, 2, 3).contains(audit) || !Set.of(0, 1, 2).contains(publish))
            throw new CoolException("审核或上架状态无效");
        var rows = QueryWrapper.create().select(CouponPublicationEntity::getCouponTemplateId).from(CouponPublicationEntity.class)
            .eq(CouponPublicationEntity::getChannel, "PLATFORM");
        if (merchant != null) rows.eq(CouponPublicationEntity::getMerchantId, merchant);
        var all = rows.clone();
        if (intent.auditStatus() != null) rows.eq(CouponPublicationEntity::getAuditStatus, audit);
        if (intent.publishStatus() != null) rows.eq(CouponPublicationEntity::getPublishStatus, publish);
        var condition = COUPON_TEMPLATE_ENTITY.ID.in(rows);
        if (audit == 0 && publish == 0) condition = condition.or(COUPON_TEMPLATE_ENTITY.ID.notIn(all));
        query.and(condition);
    }

    public QueryWrapper visible(BaseSysUserEntity account, Long merchant, Long store) {
        var query = QueryWrapper.create();
        if (merchant != null) query.eq(CouponTemplateEntity::getMerchantId, merchant);
        var allowed = account == null ? null : scope.couponIds(account);
        if (allowed != null) query.in(CouponTemplateEntity::getId, allowed.isEmpty() ? List.of(-1L) : allowed);
        if (store != null) {
            var branch = QueryWrapper.create().select(WorkspaceCouponStoreEntity::getCouponTemplateId).from(WorkspaceCouponStoreEntity.class)
                .eq(WorkspaceCouponStoreEntity::getStoreId, store);
            query.and(COUPON_TEMPLATE_ENTITY.SCOPE_TYPE.eq(0).or(COUPON_TEMPLATE_ENTITY.ID.in(branch)));
        }
        return query;
    }

    public Reply statistics(Long actor, boolean platform, BaseSysUserEntity account, CouponTemplateEntity target,
                             Long store, String action, Intent intent) {
        Range range = range(intent);
        boolean sales = "SALES".equals(action);
        requireMetric(actor, platform, store, sales);
        long amount = metric(account, target, store, sales, range);
        return say(label(target) + " · " + range.label() + "，" + scopeLabel(target.getMerchantId(), store)
            + metricName(sales) + " " + amount + (sales ? " 张。" : " 次。") + metricBasis(sales));
    }

    private Range range(Intent intent) {
        try {
            LocalDate today = LocalDate.now(ZONE);
            Integer days = intent.query() == null ? null : intent.query().relativeDays();
            if (days != null) {
                if (days < 1 || days > 366) throw new CoolException("请指定1至366天的相对日期范围。");
                return new Range(today.minusDays(days - 1L), today);
            }
            var result = new Range(intent.startDay() == null ? today.minusDays(29) : LocalDate.parse(intent.startDay()),
                intent.endDay() == null ? today : LocalDate.parse(intent.endDay()));
            if (result.start().isAfter(result.end()) || result.start().plusDays(365).isBefore(result.end()))
                throw new CoolException("请指定不超过366天的有效日期范围。");
            return result;
        } catch (DateTimeException invalid) { throw new CoolException("请提供有效的起止日期。"); }
    }

    private String metricName(boolean sales) { return sales ? "已支付销量" : "成功核销"; }
    private String metricBasis(boolean sales) {
        return sales ? "按支付成功时间及订单明细购买数量统计。" : "按成功核销记录的核销时间统计。";
    }

    private void requireMetric(Long actor, boolean platform, Long store, boolean sales) {
        if (platform) access.platform(sales ? "coupon:purchaseOrder:page" : "coupon:consumptionOrder:page");
        else auth.require(actor, sales ? "purchase:view" : "redeem:use");
        if (sales && store != null)
            throw new CoolException("购券订单没有成交门店归属，请清除门店筛选后查询已支付销量。");
    }

    private record Ranked(CouponTemplateEntity coupon, long value) {}

    public Reply ranking(Long actor, boolean platform, BaseSysUserEntity account, Chat input,
                          String action, Intent intent, QueryWrapper query) {
        boolean sales = action.endsWith("SALES");
        requireMetric(actor, platform, input.storeId(), sales);
        var range = range(intent);
        var ids = selected(input);
        if (action.startsWith("COMPARE") && ids.size() < 2) return say("请在列表勾选至少两款券后发起对比。");
        if (!ids.isEmpty()) query.in(CouponTemplateEntity::getId, ids);
        var rows = templates.selectListByQuery(query.limit(31));
        if (rows.size() > 30) return say("当前范围超过30款，请缩小商家、门店或券类型范围后再排名。");
        if (!ids.isEmpty() && rows.size() != ids.size()) throw new CoolException("部分已选券不在查询范围", 403);
        if (rows.isEmpty()) return say("当前范围没有可统计的优惠券。");
        var ranked = rows.stream().map(row -> new Ranked(row, metric(account, row, input.storeId(), sales, range)))
            .sorted(Comparator.comparingLong(Ranked::value).reversed().thenComparing(r -> r.coupon().getId())).toList();
        var lines = ranked.stream().map(r -> label(r.coupon()) + "  " + r.value() + (sales ? " 张" : " 次")).toList();
        String caption = range.label() + " · " + scopeLabel(input.merchantId(), input.storeId()) + " · "
            + metricName(sales) + "降序。" + metricBasis(sales);
        return new Reply(caption + "\n" + String.join("\n", lines), ranked.stream().map(r -> candidate(r.coupon())).toList(),
            null, List.of(), null, null, true, List.of(), null);
    }

    private long metric(BaseSysUserEntity account, CouponTemplateEntity target, Long store, boolean sales, Range range) {
        if (sales) {
            var orders = QueryWrapper.create().select(PurchaseOrderEntity::getId).from(PurchaseOrderEntity.class)
                .eq(PurchaseOrderEntity::getMerchantId, target.getMerchantId())
                .eq(PurchaseOrderEntity::getOrderStatus, "PAID").eq(PurchaseOrderEntity::getPaymentStatus, "SUCCESS")
                .ge(PurchaseOrderEntity::getPaidAt, range.from()).lt(PurchaseOrderEntity::getPaidAt, range.to());
            var allowed = account == null ? null : scope.purchaseIds(account);
            if (allowed != null) orders.in(PurchaseOrderEntity::getId, allowed.isEmpty() ? List.of(-1L) : allowed);
            Number total = (Number) purchases.selectObjectByQuery(QueryWrapper.create().select(sum(PURCHASE_ORDER_ITEM_ENTITY.QUANTITY))
                .eq(PurchaseOrderItemEntity::getCouponTemplateId, target.getId())
                .eq(PurchaseOrderItemEntity::getMerchantId, target.getMerchantId()).in(PurchaseOrderItemEntity::getOrderId, orders));
            return total == null ? 0 : total.longValue();
        }
        var issued = QueryWrapper.create().select(CustomerCouponEntity::getId).from(CustomerCouponEntity.class)
            .eq(CustomerCouponEntity::getCouponTemplateId, target.getId());
        var query = QueryWrapper.create().eq(CouponRedemptionEntity::getMerchantId, target.getMerchantId())
            .in(CouponRedemptionEntity::getCustomerCouponId, issued)
            .ge(CouponRedemptionEntity::getRedeemedAt, range.from()).lt(CouponRedemptionEntity::getRedeemedAt, range.to());
        if (store != null) query.eq(CouponRedemptionEntity::getStoreId, store);
        if (account != null && !scope.all(account)) {
            var allowed = auth.storeIds(account);
            query.in(CouponRedemptionEntity::getStoreId, allowed.isEmpty() ? List.of(-1L) : allowed);
        }
        return redemptions.selectCountByQuery(query);
    }

    public Candidate candidate(CouponTemplateEntity row) {
        return new Candidate(row.getId(), row.getName(), merchantName(row.getMerchantId()),
            typeName(row.getType()), statusName(row.getStatus()), row.getCreateTime());
    }

    public String label(CouponTemplateEntity row) {
        return "「" + row.getName() + "」 · " + merchantName(row.getMerchantId());
    }

    public String statusName(Integer status) {
        return switch (Objects.requireNonNullElse(status, -1)) {
            case 0 -> "草稿"; case 1 -> "启用"; case 2 -> "停用"; default -> "未知";
        };
    }

    public String merchantName(Long id) {
        var merchant = merchants.selectOneById(id);
        return merchant == null ? "历史商家" : merchant.getName();
    }

    public String scopeLabel(Long merchant, Long store) {
        var branch = store == null ? null : merchantStores.selectOneById(store);
        return (merchant == null ? "当前授权商家范围" : merchantName(merchant))
            + (branch == null ? "（授权门店范围）" : "（" + branch.getName() + "）");
    }

    public String typeName(String type) {
        return switch (Objects.toString(type, "")) {
            case "CASH" -> "代金券"; case "DISCOUNT" -> "折扣券"; case "FULL_REDUCTION" -> "满减券";
            case "PACKAGE" -> "套餐券"; case "EXPERIENCE" -> "体验券"; case "EXCHANGE" -> "兑换券";
            default -> "其他";
        };
    }

    private Reply say(String text) { return new Reply(text, List.of(), null, List.of(), null); }
}
