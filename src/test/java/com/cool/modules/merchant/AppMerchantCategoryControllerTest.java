package com.cool.modules.merchant;

import cn.hutool.core.lang.Dict;
import com.cool.modules.merchant.controller.app.AppMerchantCategoryController;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.service.MerchantCategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证首页分类响应的层级可见性、排序与公开字段
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 08:42:54
 */
class AppMerchantCategoryControllerTest {
    @Test
    void hidesChildrenWithoutVisibleRootAndReturnsOnlyPublicFields() {
        MerchantCategoryService service = mock(MerchantCategoryService.class);
        when(service.list(any(QueryWrapper.class))).thenReturn(List.of(
                category(6, 3L, 20), category(9, 8L, 1),
                category(4, null, 10), category(3, null, 10)));
        List<Dict> data = new AppMerchantCategoryController(service).list().getData();
        assertEquals(List.of(3L, 4L, 6L), data.stream().map(item -> item.getLong("id")).toList());
        assertNull(data.get(0).get("parentId"));
        assertEquals(3L, data.get(2).getLong("parentId"));
        assertEquals(java.util.Set.of("id", "parentId", "name", "icon", "sort"), data.get(0).keySet());
    }

    @Test
    void returnsEmptyArrayWhenNoCategoriesAreVisible() {
        MerchantCategoryService service = mock(MerchantCategoryService.class);
        when(service.list(any(QueryWrapper.class))).thenReturn(List.of());
        assertTrue(new AppMerchantCategoryController(service).list().getData().isEmpty());
    }

    private MerchantCategoryEntity category(long id, Long parentId, int sort) {
        MerchantCategoryEntity entity = new MerchantCategoryEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setName("分类" + id);
        entity.setSort(sort);
        entity.setStatus(1);
        entity.setShowOnFront(1);
        return entity;
    }
}
