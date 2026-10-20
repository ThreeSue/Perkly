package com.cool.modules.coupon;

import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.impl.CouponTemplateRules;
import java.math.BigDecimal;
import java.util.Date;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证优惠券金额边界、有效期和不同券类型的规则隔离
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:21:21
 */
class CouponTemplateRulesTest {
    private CouponTemplateEntity coupon(String type) {
        CouponTemplateEntity e = new CouponTemplateEntity();
        e.setName(" 测试券 ");
        e.setType(type);
        e.setValidityType("AFTER_ISSUE");
        e.setValidDays(30);
        return e;
    }
    @Test void acceptsFullReductionAndTrimsName() {
        var e = coupon("FULL_REDUCTION");
        e.setMinSpend(new BigDecimal("100"));
        e.setDiscountAmount(new BigDecimal("20"));
        CouponTemplateRules.validate(e);
        assertEquals("测试券", e.getName());
        assertEquals(0, e.getStatus());
    }
    @Test void rejectsReductionGreaterThanThreshold() {
        var e = coupon("FULL_REDUCTION");
        e.setMinSpend(new BigDecimal("10")); e.setDiscountAmount(new BigDecimal("20"));
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
    }
    @Test void rejectsMissingCashAmount() {
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(coupon("CASH")));
    }
    @Test void rejectsInvalidDiscountAndAcceptsEightPointEight() {
        var e = coupon("DISCOUNT");
        for (String rate : new String[]{"0", "-1", "10", "8.888"}) {
            e.setDiscountRate(new BigDecimal(rate));
            assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
        }
        e.setDiscountRate(new BigDecimal("8.8"));
        CouponTemplateRules.validate(e);
    }
    @Test void rejectsNegativeOrExcessPrecisionMoney() {
        var e = coupon("EXPERIENCE");
        for (String amount : new String[]{"-1", "0.001", "100000000"}) {
            e.setSalePrice(new BigDecimal(amount));
            assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
        }
    }
    @Test void clearsFieldsFromPreviousCouponType() {
        var e = coupon("EXCHANGE");
        e.setDiscountAmount(new BigDecimal("10")); e.setDiscountRate(new BigDecimal("8"));
        e.setDeductAmount(new BigDecimal("20")); e.setMinSpend(new BigDecimal("30"));
        CouponTemplateRules.validate(e);
        assertNull(e.getDiscountAmount()); assertNull(e.getDiscountRate());
        assertNull(e.getDeductAmount()); assertNull(e.getMinSpend());
    }
    @Test void rejectsStockBelowIssuedCountAndZeroCustomerLimit() {
        var e = coupon("PACKAGE");
        e.setIssuedCount(10L); e.setIssueLimit(9L);
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
        e.setIssueLimit(10L); e.setPerCustomerLimit(0);
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
    }
    @Test void rejectsMissingOrReversedFixedValidity() {
        var e = coupon("PACKAGE"); e.setValidityType("FIXED");
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
        e.setValidFrom(new Date(2000)); e.setValidTo(new Date(1000));
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
    }
    @Test void permitsExpiredDraftButRejectsEnablingIt() {
        var e = coupon("PACKAGE"); e.setValidityType("FIXED");
        e.setValidFrom(new Date(1000)); e.setValidTo(new Date(2000)); e.setStatus(0);
        CouponTemplateRules.validate(e);
        e.setStatus(1);
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
    }
    @Test void clearsFixedDatesWhenUsingRelativeValidity() {
        var e = coupon("PACKAGE"); e.setValidFrom(new Date()); e.setValidTo(new Date());
        CouponTemplateRules.validate(e);
        assertNull(e.getValidFrom()); assertNull(e.getValidTo());
    }
    @Test void rejectsInvalidTypeStatusAndInvalidSalesRange() {
        var e = coupon("UNKNOWN");
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
        e.setType("EXPERIENCE"); e.setStatus(9);
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
        e.setStatus(0); e.setSaleStartTime(new Date(2000)); e.setSaleEndTime(new Date(1000));
        assertThrows(CoolException.class, () -> CouponTemplateRules.validate(e));
    }
}

