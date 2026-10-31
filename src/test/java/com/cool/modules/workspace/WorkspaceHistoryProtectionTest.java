package com.cool.modules.workspace;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.service.*;
import com.cool.modules.merchant.service.impl.*;
import com.cool.modules.coupon.mapper.ConsumptionOrderMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证执照锁定及有员工优惠券账单的门店禁止删除
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 01:12:00
 */
class WorkspaceHistoryProtectionTest {
    @Test void platformCannotChangeExistingLicense(){var mapper=mock(MerchantInfoMapper.class);var service=new MerchantInfoServiceImpl(mock(MerchantCategoryService.class));ReflectionTestUtils.setField(service,"mapper",mapper);var old=new MerchantInfoEntity();old.setId(1L);old.setLicenseNumber("91350100123456789X");when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(old);var input=new MerchantInfoEntity();input.setId(1L);input.setLicenseNumber("91350100987654321X");assertThrows(CoolException.class,()->service.update(input));verify(mapper,never()).update(any(MerchantInfoEntity.class));}
    MerchantStoreMapper stores=mock(MerchantStoreMapper.class);
    MerchantUserStoreMapper bindings=mock(MerchantUserStoreMapper.class);
    ConsumptionOrderMapper orders=mock(ConsumptionOrderMapper.class);
    MerchantStoreServiceImpl service=new MerchantStoreServiceImpl(mock(MerchantInfoService.class),mock(MerchantStoreCategoryService.class));
    @BeforeEach void setup(){ReflectionTestUtils.setField(service,"mapper",stores);ReflectionTestUtils.setField(service,"employeeBindings",bindings);ReflectionTestUtils.setField(service,"consumptionOrders",orders);when(stores.lockForCouponCheck(1L)).thenReturn(1L);when(bindings.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());}
    @Test void employeeBindingBlocksStoreDeletion(){when(bindings.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(new MerchantUserStoreEntity()));assertThrows(CoolException.class,()->service.delete(1L));verify(stores,never()).deleteBatchByIds(anyCollection());}
    @Test void couponReferenceBlocksStoreDeletion(){when(stores.couponReferenceCount(1L)).thenReturn(1L);assertThrows(CoolException.class,()->service.delete(1L));verify(stores,never()).deleteBatchByIds(anyCollection());}
    @Test void historicalBillBlocksStoreDeletion(){when(orders.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);assertThrows(CoolException.class,()->service.delete(1L));verify(stores,never()).deleteBatchByIds(anyCollection());}
    @Test void storeStatusKeepsLegacyCodesAndReadableNames(){assertEquals(0,StoreStatus.DISABLED);assertEquals(1,StoreStatus.OPEN);assertEquals("停业",StoreStatus.text(2));assertEquals("关闭",StoreStatus.text(3));assertFalse(StoreStatus.valid(4));}
}
