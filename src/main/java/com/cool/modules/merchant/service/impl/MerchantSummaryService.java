package com.cool.modules.merchant.service.impl;

import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.mybatisflex.core.query.QueryMethods.count;

/** Batch display fields and grouped counts for only the records in the current result. */
@Service
@RequiredArgsConstructor
public class MerchantSummaryService {
    private final MerchantInfoMapper merchants;
    private final MerchantStoreCategoryMapper categories;
    private final MerchantStoreMapper stores;

    private Map<Long, Long> counts(String field, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Row> rows = stores.selectRowsByQuery(QueryWrapper.create().select(field)
                .select(count().as("total")).in(field, ids).groupBy(field));
        Map<Long, Long> result = new HashMap<>();
        for (Row row : rows) result.put(row.getLong(field), row.getLong("total"));
        return result;
    }
    private Map<Long, String> merchantNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return merchants.selectListByIds(ids).stream().collect(Collectors.toMap(MerchantInfoEntity::getId, MerchantInfoEntity::getName));
    }
    public void merchants(List<MerchantInfoEntity> rows) {
        Map<Long, Long> counts = counts("merchant_id", rows.stream().map(MerchantInfoEntity::getId).toList());
        rows.forEach(row -> row.setStoreCount(counts.getOrDefault(row.getId(), 0L)));
    }
    public void stores(List<MerchantStoreEntity> rows) {
        if (rows.isEmpty()) return;
        Map<Long, String> names = merchantNames(rows.stream().map(MerchantStoreEntity::getMerchantId).distinct().toList());
        List<Long> categoryIds = rows.stream().map(MerchantStoreEntity::getCategoryId).distinct().toList();
        Map<Long, MerchantStoreCategoryEntity> categoryMap = categories.selectListByIds(categoryIds).stream()
                .collect(Collectors.toMap(MerchantStoreCategoryEntity::getId, row -> row));
        rows.forEach(row -> {
            row.setMerchantName(names.getOrDefault(row.getMerchantId(), "商家不存在"));
            MerchantStoreCategoryEntity category = categoryMap.get(row.getCategoryId());
            row.setCategoryName(category != null ? category.getName() : "门店类型不存在");
        });
    }
    public void categories(List<MerchantStoreCategoryEntity> rows) {
        if (rows.isEmpty()) return;
        Map<Long, Long> counts = counts("category_id", rows.stream().map(MerchantStoreCategoryEntity::getId).toList());
        rows.forEach(row -> {
            row.setStoreCount(counts.getOrDefault(row.getId(), 0L));
        });
    }
}
