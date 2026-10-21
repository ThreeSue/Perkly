package com.cool.modules.merchant;

import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.service.impl.MerchantSummaryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantSummaryServiceTest {
    @Test void countsOnlyRequestedMerchantsAndIncludesZeroStores() {
        var merchants = mock(MerchantInfoMapper.class); var categories = mock(MerchantStoreCategoryMapper.class); var stores = mock(MerchantStoreMapper.class);
        var service = new MerchantSummaryService(merchants, categories, stores);
        var a = new MerchantInfoEntity(); a.setId(3L); var b = new MerchantInfoEntity(); b.setId(8L);
        Row count = new Row(); count.set("merchant_id", 3L); count.set("total", 4L);
        when(stores.selectRowsByQuery(any(QueryWrapper.class))).thenReturn(List.of(count));
        service.merchants(List.of(a, b));
        assertEquals(4L, a.getStoreCount()); assertEquals(0L, b.getStoreCount());
        ArgumentCaptor<QueryWrapper> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(stores).selectRowsByQuery(query.capture());
        String sql = query.getValue().toSQL();
        assertTrue(sql.contains("merchant_id"), sql); assertTrue(sql.contains("COUNT("), sql);
        assertTrue(sql.contains("GROUP BY"), sql); assertTrue(sql.contains(" IN "), sql);
        verifyNoInteractions(merchants, categories);
    }
    @Test void publicTypeCountsAndLabelsAreSharedAcrossMerchants() {
        var merchants = mock(MerchantInfoMapper.class); var categories = mock(MerchantStoreCategoryMapper.class); var stores = mock(MerchantStoreMapper.class);
        var service = new MerchantSummaryService(merchants, categories, stores);
        var merchant = new MerchantInfoEntity(); merchant.setId(3L); merchant.setName("青禾");
        when(merchants.selectListByIds(anyCollection())).thenReturn(List.of(merchant));
        var category = new MerchantStoreCategoryEntity(); category.setId(5L); category.setName("直营");
        when(categories.selectListByIds(anyCollection())).thenReturn(List.of(category));
        Row count = new Row(); count.set("category_id", 5L); count.set("total", 2L);
        when(stores.selectRowsByQuery(any(QueryWrapper.class))).thenReturn(List.of(count));
        service.categories(List.of(category)); assertEquals(2L, category.getStoreCount());
        var store = new MerchantStoreEntity(); store.setMerchantId(3L); store.setCategoryId(5L);
        service.stores(List.of(store)); assertEquals("青禾", store.getMerchantName()); assertEquals("直营", store.getCategoryName());
        store.setMerchantId(99L); service.stores(List.of(store)); assertEquals("直营", store.getCategoryName());
        verify(stores, times(1)).selectRowsByQuery(any(QueryWrapper.class));
    }
}
