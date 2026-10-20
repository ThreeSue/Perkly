package com.cool.modules.merchant.controller.app;

import cn.hutool.core.lang.Dict;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.annotation.TokenIgnore;
import com.cool.core.request.R;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.service.MerchantCategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为用户移动端首页提供可展示的商家分类列表
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 08:42:26
 */
@RequiredArgsConstructor
@Tag(name = "用户端商家分类")
@CoolRestController(api = {"list"})
public class AppMerchantCategoryController {
    private final MerchantCategoryService categoryService;

    @TokenIgnore
    @Operation(summary = "首页商家分类列表")
    @GetMapping("/list")
    public R<List<Dict>> list() {
        List<MerchantCategoryEntity> categories = categoryService.list(QueryWrapper.create()
                .eq("status", 1).eq("show_on_front", 1));
        Set<Long> visibleRoots = categories.stream()
                .filter(item -> item.getParentId() == null)
                .map(MerchantCategoryEntity::getId).collect(Collectors.toSet());
        List<Dict> result = categories.stream()
                // 隐藏上级时一并隐藏其子分类，不向首页返回孤立的二级分类。
                .filter(item -> item.getParentId() == null || visibleRoots.contains(item.getParentId()))
                .sorted(Comparator.comparing(MerchantCategoryEntity::getSort,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MerchantCategoryEntity::getId))
                .map(item -> Dict.create().set("id", item.getId())
                        .set("parentId", item.getParentId()).set("name", item.getName())
                        .set("icon", item.getIcon()).set("sort", item.getSort()))
                .toList();
        return R.ok(result);
    }
}
