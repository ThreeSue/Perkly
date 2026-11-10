package com.cool.modules.merchant.report;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.MerchantUserStoreMapper;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证独立门店报表零桶去重搜索与权限
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 08:40:09
 */
class MerchantReportTest {
 final MerchantReportAccess access=mock(MerchantReportAccess.class);
 final CouponRedemptionMapper records=mock(CouponRedemptionMapper.class);
 final ConsumptionOrderMapper orders=mock(ConsumptionOrderMapper.class);
 final MerchantReportService service=new MerchantReportService(access,records,orders,mock(BaseSysUserMapper.class),mock(MerchantUserStoreMapper.class));
 MerchantReportTest(){when(access.require(1L,null)).thenReturn(new MerchantReportAccess.Scope(1L,2L,3L,null));}
 MerchantReportModels.Filter filter(String keyword){return new MerchantReportModels.Filter("TODAY",null,null,null,null,keyword,1,1);}
 @Test void noAiDependencyAndEmptyBuckets(){when(records.selectListByQuery(any())).thenReturn(List.of());var r=service.query(1L,filter(null));assertEquals(24,r.trend().size());assertEquals(0,r.total());assertEquals(BigDecimal.ZERO,r.summary().knownConsumptionYuan());assertNull(r.summary().failedAttempts());}
 CouponRedemptionEntity row(long id){var r=new CouponRedemptionEntity();r.setId(id);r.setConsumptionOrderId(7L);r.setConsumptionAmount(new BigDecimal("150"));r.setDiscountAmount(new BigDecimal("10"));r.setRedeemedAt(new Date());return r;}
 @Test void duplicateOrdersStablePageAndHistoricalType(){var o=new ConsumptionOrderEntity();o.setId(7L);o.setCouponType("VOUCHER");o.setCouponName("历史券");when(orders.selectListByQuery(any())).thenReturn(List.of(o));when(records.selectListByQuery(any())).thenReturn(List.of(row(2),row(1)),List.of());var r=service.query(1L,filter(null));assertEquals(2,r.total());assertEquals(1,r.summary().linkedOrders());assertEquals(new BigDecimal("150"),r.summary().knownConsumptionYuan());assertEquals(2L,r.list().get(0).id());assertEquals("VOUCHER",r.types().get(0).type());}
 @Test void searchFiltersSummaryAndDetailsTogether(){when(records.selectListByQuery(any())).thenReturn(List.of(row(1)),List.of());when(orders.selectListByQuery(any())).thenReturn(List.of());var r=service.query(1L,filter("不存在"));assertEquals(0,r.total());assertTrue(r.list().isEmpty());assertEquals(0,r.summary().successfulRedemptions());}
 @Test void deniedBeforeQuery(){when(access.require(1L,null)).thenThrow(new com.cool.core.exception.CoolException("拒绝",403));assertThrows(com.cool.core.exception.CoolException.class,()->service.query(1L,filter(null)));verifyNoInteractions(records,orders);}
 @Test void customDatesAndUnknownAmounts(){assertThrows(com.cool.core.exception.CoolException.class,()->MerchantReportService.range(new MerchantReportModels.Filter("CUSTOM",LocalDate.now(),LocalDate.now().minusDays(1),null,null,null,null,null),LocalDate.now()));var a=new MerchantReportService.Amount();a.add(null);a.add(BigDecimal.TEN);assertTrue(a.unknown);}
}
