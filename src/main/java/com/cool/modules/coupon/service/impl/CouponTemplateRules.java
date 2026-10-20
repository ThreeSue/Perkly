package com.cool.modules.coupon.service.impl;

import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 校验六种优惠券的金额、有效期、库存和启停规则
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:13:58
 */
public final class CouponTemplateRules {
    private static final Set<String> TYPES = Set.of("FULL_REDUCTION", "DISCOUNT", "CASH", "PACKAGE", "EXPERIENCE", "EXCHANGE");
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");
    private CouponTemplateRules() {}

    public static void validate(CouponTemplateEntity e) {
        if (e.getName() == null || e.getName().isBlank()) fail("券名称不能为空");
        e.setName(e.getName().trim());
        length(e.getName(), 100, "券名称");
        length(e.getDescription(), 2000, "使用说明");
        length(e.getCover(), 500, "封面地址");
        if (e.getType() == null || !TYPES.contains(e.getType())) fail("请选择有效的优惠券类型");
        if (e.getStatus() == null) e.setStatus(0);
        if (!Set.of(0, 1, 2).contains(e.getStatus())) fail("模板状态只能为草稿、启用或停用");
        if (e.getScopeType() == null) e.setScopeType(0);
        if (!Set.of(0, 1).contains(e.getScopeType())) fail("请选择有效的适用门店范围");
        if (e.getAllowCombine() == null) e.setAllowCombine(0);
        if (!Set.of(0, 1).contains(e.getAllowCombine())) fail("是否同享其他优惠只能为是或否");
        if (e.getSalePrice() == null) e.setSalePrice(BigDecimal.ZERO);
        money(e.getSalePrice(), "售价", false);
        money(e.getMinSpend(), "最低消费", false);
        money(e.getDiscountAmount(), "满减金额", true);
        money(e.getDeductAmount(), "抵扣金额", true);
        money(e.getMaxDiscount(), "最高优惠金额", true);
        if (e.getIssuedCount() == null) e.setIssuedCount(0L);
        if (e.getIssueLimit() != null && (e.getIssueLimit() < 1 || e.getIssueLimit() < e.getIssuedCount())) {
            fail("发行总量必须大于0，且不能小于已发放数量");
        }
        if (e.getPerCustomerLimit() == null) e.setPerCustomerLimit(1);
        if (e.getPerCustomerLimit() < 1) fail("每人限领张数必须大于0");
        switch (e.getType()) {
            case "FULL_REDUCTION" -> {
                if (e.getMinSpend() == null || e.getMinSpend().signum() <= 0 || e.getDiscountAmount() == null) fail("满减券必须填写消费门槛和减免金额");
                if (e.getDiscountAmount().compareTo(e.getMinSpend()) > 0) fail("减免金额不能大于消费门槛");
                e.setDeductAmount(null); e.setDiscountRate(null); e.setMaxDiscount(null);
            }
            case "DISCOUNT" -> {
                BigDecimal rate = e.getDiscountRate();
                if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.TEN) >= 0 || rate.stripTrailingZeros().scale() > 2) fail("折扣数必须大于0且小于10，最多两位小数");
                e.setDiscountAmount(null); e.setDeductAmount(null);
            }
            case "CASH" -> {
                if (e.getDeductAmount() == null) fail("代金券必须填写抵扣金额");
                e.setDiscountAmount(null); e.setDiscountRate(null); e.setMaxDiscount(null);
            }
            default -> {
                e.setMinSpend(null); e.setDiscountAmount(null); e.setDeductAmount(null);
                e.setDiscountRate(null); e.setMaxDiscount(null);
            }
        }
        if ("FIXED".equals(e.getValidityType())) {
            if (e.getValidFrom() == null || e.getValidTo() == null || !e.getValidTo().after(e.getValidFrom())) fail("固定有效期的结束时间必须晚于开始时间");
            if (e.getStatus() == 1 && !e.getValidTo().after(new Date())) fail("有效期已结束，不能启用");
            e.setValidDays(null);
        } else if ("AFTER_ISSUE".equals(e.getValidityType())) {
            if (e.getValidDays() == null || e.getValidDays() < 1) fail("发券后有效天数必须大于0");
            e.setValidFrom(null); e.setValidTo(null);
        } else fail("请选择固定日期或发券后按天数有效");
        if (e.getSaleStartTime() != null && e.getSaleEndTime() != null && !e.getSaleEndTime().after(e.getSaleStartTime())) fail("领取购买结束时间必须晚于开始时间");
        if (e.getStatus() == 1 && e.getSaleEndTime() != null && !e.getSaleEndTime().after(new Date())) fail("领取购买时间已结束，不能启用");
    }

    private static void money(BigDecimal value, String label, boolean positive) {
        if (value != null && (value.signum() < 0 || (positive && value.signum() == 0) ||
                value.compareTo(MAX_MONEY) > 0 || value.stripTrailingZeros().scale() > 2)) {
            fail(label + "超出有效范围或超过两位小数");
        }
    }
    private static void length(String value, int max, String label) {
        if (value != null && value.length() > max) fail(label + "不能超过" + max + "个字符");
    }
    private static void fail(String message) { throw new CoolException(message); }
}

