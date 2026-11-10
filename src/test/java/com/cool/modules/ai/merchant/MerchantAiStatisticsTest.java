package com.cool.modules.ai.merchant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.coupon.entity.CouponRedemptionEntity;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证核销统计权限日期去重未知金额及无结果语义
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:18:58
 */
class MerchantAiStatisticsTest {
    final LocalDate today=LocalDate.of(2026,11,9);
    final MerchantAiAccess.Scope scope=new MerchantAiAccess.Scope(1L,2L,3L,1L);
    final MerchantAiStatistics.Dates dates=new MerchantAiStatistics.Dates(today,today);
    CouponRedemptionEntity row(long id,Long order,String amount) {
        var row=new CouponRedemptionEntity();row.setId(id);row.setConsumptionOrderId(order);
        row.setConsumptionAmount(amount==null?null:new BigDecimal(amount));return row;
    }
    @Test void deduplicatesOrdersAndDoesNotInventFailedAttempts() {
        var result=MerchantAiStatistics.summarize(dates,scope,List.of(row(1,7L,"150"),row(2,7L,"150.00"),row(3,8L,null)));
        assertEquals(3,result.successfulRedemptions());assertEquals(0,new BigDecimal("150").compareTo(result.knownConsumptionYuan()));
        assertEquals(1,result.unknownAmountGroups());assertNull(result.failedAttempts());
    }
    @Test void unknownAndConflictingAmountsAreNotZero() {
        assertNull(MerchantAiStatistics.summarize(dates,scope,List.of(row(1,7L,null))).knownConsumptionYuan());
        assertNull(MerchantAiStatistics.summarize(dates,scope,List.of(row(1,7L,"100"),row(2,7L,"200"))).knownConsumptionYuan());
    }
    @Test void emptyResultIsRealZero() {
        var result=MerchantAiStatistics.summarize(dates,scope,List.of());
        assertEquals(0,result.successfulRedemptions());assertEquals(BigDecimal.ZERO,result.knownConsumptionYuan());
    }
    @Test void inclusiveCalendarRangesAndInvalidDates() {
        assertEquals(today.minusDays(6),MerchantAiStatistics.dates(new MerchantAiStatistics.Input("LAST_7_DAYS",null,null,null),today).startDate());
        assertEquals(today.minusDays(29),MerchantAiStatistics.dates(new MerchantAiStatistics.Input("LAST_30_DAYS",null,null,null),today).startDate());
        assertThrows(CoolException.class,()->MerchantAiStatistics.dates(new MerchantAiStatistics.Input("CUSTOM",today,today.minusDays(1),null),today));
        assertThrows(CoolException.class,()->MerchantAiStatistics.dates(new MerchantAiStatistics.Input("CUSTOM",today,today.plusDays(1),null),today));
    }
    @Test void selfScopeCannotRequestAnotherEmployee() {
        var authorization=mock(WorkspaceAuthorization.class);var user=new BaseSysUserEntity();
        user.setId(1L);user.setMerchantId(2L);user.setAccountType("STORE");
        var store=new MerchantStoreEntity();store.setId(3L);store.setStatus(1);
        when(authorization.user(1L)).thenReturn(user);when(authorization.boundStore(user)).thenReturn(store);
        when(authorization.ownRecordsOnly(user)).thenReturn(true);
        var access=new MerchantAiAccess(new com.cool.modules.merchant.report.MerchantReportAccess(authorization));
        assertEquals(1L,access.require(1L,null).operatorId());
        assertThrows(CoolException.class,()->access.require(1L,99L));
        verify(authorization,atLeastOnce()).require(1L,"bill:view");
        user.setAccountType("MERCHANT");assertThrows(CoolException.class,()->access.require(1L,null));
    }
}
