package com.cool.modules.merchant;

import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.service.*;
import com.cool.modules.merchant.service.impl.*;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantManagementRulesTest {
    private MerchantCategoryEntity category(long id, Long parent, String name) {
        MerchantCategoryEntity row = new MerchantCategoryEntity(); row.setId(id); row.setParentId(parent); row.setName(name); return row;
    }
    private MerchantCategoryServiceImpl categories(MerchantCategoryMapper mapper, MerchantInfoMapper merchants) {
        MerchantCategoryServiceImpl service = spy(new MerchantCategoryServiceImpl());
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "merchantMapper", merchants);
        doReturn(0L).when(service).count(any(QueryWrapper.class));
        return service;
    }
    @Test void categoryUpdateRejectsDuplicateAndSelfParent() {
        var mapper = mock(MerchantCategoryMapper.class); var service = categories(mapper, mock(MerchantInfoMapper.class));
        doReturn(category(1L, null, "餐饮")).when(service).getById(1L);
        doReturn(1L).when(service).count(any(QueryWrapper.class));
        assertThrows(CoolException.class, () -> service.update(category(1L, null, "重名")));
        assertThrows(CoolException.class, () -> service.update(category(1L, 1L, "餐饮")));
        verify(mapper, never()).update(any(MerchantCategoryEntity.class));
    }
    @Test void categoryRejectsMissingParentThirdLevelAndMovingParentWithChildren() {
        var service = categories(mock(MerchantCategoryMapper.class), mock(MerchantInfoMapper.class));
        assertThrows(CoolException.class, () -> service.add(category(7L, 99L, "不存在上级")));
        doReturn(category(2L, 1L, "二级分类")).when(service).getById(2L);
        assertThrows(CoolException.class, () -> service.add(category(7L, 2L, "三级分类")));
        doReturn(category(1L, null, "餐饮")).when(service).getById(1L);
        doReturn(category(3L, null, "服务")).when(service).getById(3L);
        doReturn(1L).when(service).count(any(QueryWrapper.class));
        assertThrows(CoolException.class, () -> service.update(category(1L, 3L, "餐饮")));
    }
    @Test void categoryDeleteChecksChildrenAndMerchantsBeforeAnyDeletion() {
        var mapper = mock(MerchantCategoryMapper.class); var merchants = mock(MerchantInfoMapper.class);
        var service = categories(mapper, merchants);
        doReturn(1L).when(service).count(any(QueryWrapper.class));
        assertThrows(CoolException.class, () -> service.delete(1L));
        verifyNoInteractions(merchants);
        doReturn(0L).when(service).count(any(QueryWrapper.class));
        when(merchants.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);
        assertThrows(CoolException.class, () -> service.delete(new cn.hutool.json.JSONObject(), 1L));
        verify(mapper, never()).deleteBatchByIds(anyCollection());
    }
    @Test void categoryUpdateAllowsKeepingOwnNameAndNormalizesInput() {
        var mapper = mock(MerchantCategoryMapper.class); var service = categories(mapper, mock(MerchantInfoMapper.class));
        doReturn(category(1L, null, "餐饮")).when(service).getById(1L);
        when(mapper.update(any(MerchantCategoryEntity.class))).thenReturn(1);
        var data = category(1L, null, "  餐饮  ");
        assertTrue(service.update(data)); assertEquals("餐饮", data.getName()); assertEquals(1, data.getStatus());
    }
    private MerchantStoreEntity store(int status) {
        MerchantStoreEntity row = new MerchantStoreEntity(); row.setId(5L); row.setMerchantId(1L); row.setCategoryId(2L);
        row.setName("门店"); row.setAddress("地址"); row.setPhone("010-12345678"); row.setStatus(status); return row;
    }
    @Test void disabledMerchantAllowsMaintainingAndDisablingExistingStoreButRejectsReenable() {
        var merchants = mock(MerchantInfoService.class); var categories = mock(MerchantStoreCategoryService.class);
        var merchant = new MerchantInfoEntity(); merchant.setStatus(0); when(merchants.getById(1L)).thenReturn(merchant);
        var category = new MerchantStoreCategoryEntity(); category.setStatus(0); when(categories.getOne(any(QueryWrapper.class))).thenReturn(category);
        var service = spy(new MerchantStoreServiceImpl(merchants, categories)); var mapper = mock(MerchantStoreMapper.class);
        ReflectionTestUtils.setField(service,"history",mock(com.cool.modules.approval.service.ApprovalHistoryService.class));
        ReflectionTestUtils.setField(service, "mapper", mapper); when(mapper.update(any(MerchantStoreEntity.class))).thenReturn(1);
        when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(store(1));
        assertTrue(service.update(store(0))); assertTrue(service.update(store(1)));
        when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(store(0));
        assertThrows(CoolException.class, () -> service.update(store(1)));
        assertThrows(CoolException.class, () -> service.add(store(0)));
    }
    @Test void storeRejectsMissingTypeInvalidPhoneAndIncompleteCoordinates() {
        var merchants = mock(MerchantInfoService.class); var categories = mock(MerchantStoreCategoryService.class);
        var merchant = new MerchantInfoEntity(); merchant.setStatus(1); when(merchants.getById(1L)).thenReturn(merchant);
        var category = new MerchantStoreCategoryEntity(); category.setStatus(1); when(categories.getOne(any(QueryWrapper.class))).thenReturn(category);
        var service = new MerchantStoreServiceImpl(merchants, categories);
        when(categories.getOne(any(QueryWrapper.class))).thenReturn(null);
        assertThrows(CoolException.class, () -> service.add(store(1)));
        when(categories.getOne(any(QueryWrapper.class))).thenReturn(category);
        var invalid = store(1); invalid.setPhone("随便填写");
        assertThrows(CoolException.class, () -> service.add(invalid));
        final var coordinates = store(1); coordinates.setLongitude(java.math.BigDecimal.ONE);
        assertThrows(CoolException.class, () -> service.add(coordinates));
    }
    @Test void storeTypeRejectsInvalidNameSortAndStatus() {
        var mapper = mock(MerchantStoreCategoryMapper.class);
        var service = new MerchantStoreCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        var data = new MerchantStoreCategoryEntity(); data.setName("   ");
        assertThrows(CoolException.class, () -> service.add(data));
        data.setName("有效类型"); data.setSort(-1);
        assertThrows(CoolException.class, () -> service.add(data));
        data.setSort(0); data.setStatus(2);
        assertThrows(CoolException.class, () -> service.add(data));
        verify(mapper, never()).insertSelective(any(MerchantStoreCategoryEntity.class));
        assertThrows(CoolException.class, () -> service.delete(new Long[]{}));
        assertThrows(CoolException.class, () -> service.delete(-1L));
    }
}
