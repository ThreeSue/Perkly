package com.cool.modules.merchant.service;

import cn.hutool.json.JSONUtil;
import com.cool.modules.base.entity.sys.BaseSysConfEntity;
import com.cool.modules.base.entity.sys.BaseSysMenuEntity;
import com.cool.modules.base.mapper.sys.BaseSysConfMapper;
import com.cool.modules.base.mapper.sys.BaseSysMenuMapper;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantStoreCategoryMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 初始化六种平台门店类型并备份旧关联后一次性随机迁移现有门店
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 17:12:00
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class StoreTypeInitializer implements ApplicationRunner {
    public static final String MIGRATION_KEY = "merchant.store-types.v1";
    public static final List<String> NAMES = List.of("直营店", "加盟店", "联营店", "独立店", "旗舰店", "VIP店");
    private final MerchantStoreCategoryMapper types;
    private final MerchantStoreMapper stores;
    private final BaseSysConfMapper configuration;
    private final BaseSysMenuMapper menus;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        var marker = configuration.selectOneByQuery(QueryWrapper.create()
                .eq(BaseSysConfEntity::getCKey, MIGRATION_KEY).forUpdate());
        if (marker != null) {
            // 六种类型只作为首次安装的初始数据；迁移后允许运营人员维护。
            // 重启不能覆盖名称、状态、数量，也不能重新随机分配门店。
            return;
        }

        // AutoTable已建立新表；历史分类不再映射为业务实体，仅在本次迁移中完整存档。
        var backup = new LinkedHashMap<String, Object>();
        boolean hasLegacy = Boolean.TRUE.equals(jdbc.execute((java.sql.Connection c) -> {
            try (var tables = c.getMetaData().getTables(c.getCatalog(), null, "store_category", new String[]{"TABLE"})) {
                return tables.next();
            }
        }));
        if (hasLegacy) backup.put("legacyCategories", jdbc.queryForList("SELECT * FROM store_category"));
        var existing = stores.selectListByQuery(QueryWrapper.create()
                .orderBy(MerchantStoreEntity::getId, true).forUpdate());
        backup.put("storeAssociations", existing.stream().map(row -> {
            var association = new LinkedHashMap<String, Object>();
            association.put("storeId", row.getId());
            association.put("merchantId", row.getMerchantId());
            association.put("oldCategoryId", row.getCategoryId());
            return association;
        }).toList());
        backup.put("strategy", "independent-random-six-types");
        var state = new BaseSysConfEntity();
        state.setCKey(MIGRATION_KEY);
        state.setCValue(JSONUtil.toJsonStr(backup));
        // 唯一配置键同时阻止并发实例重复迁移；失败时整个事务回滚。
        configuration.insert(state);

        if (types.selectCountByQuery(QueryWrapper.create()) != 0) {
            throw new IllegalStateException("首次迁移要求store_type为空，请核对迁移状态");
        }
        for (int index = 0; index < NAMES.size(); index++) {
            var type = new MerchantStoreCategoryEntity();
            type.setId((long) index + 1);
            type.setName(NAMES.get(index));
            type.setSort(index);
            type.setStatus(1);
            types.insertSelectiveWithPk(type);
        }
        for (var row : existing) {
            var change = new MerchantStoreEntity();
            change.setId(row.getId());
            change.setCategoryId(randomTypeId());
            stores.update(change);
        }
        for (var menu : menus.selectListByQuery(QueryWrapper.create()
                .in(BaseSysMenuEntity::getName, List.of("门店分类", "门店分类管理")))) {
            menu.setName("门店类型");
            menus.update(menu);
        }
        log.info("已初始化六种公共门店类型，随机迁移{}家门店，原始数据备份于系统配置{}", existing.size(), MIGRATION_KEY);
    }

    public static long randomTypeId() {
        return ThreadLocalRandom.current().nextLong(1, NAMES.size() + 1L);
    }
}

