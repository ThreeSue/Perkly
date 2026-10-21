package com.cool.modules.merchant;

import static org.junit.jupiter.api.Assertions.*;

import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.service.MerchantStoreCategoryService;
import com.cool.modules.merchant.service.StoreTypeInitializer;
import com.cool.modules.merchant.service.impl.MerchantSummaryService;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以事务回滚验证公共门店类型维护删除保护和初始化幂等
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 22:00:53
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=store-type-test-unused", "sms-auth.access-key-secret=store-type-test-unused"
})
@ActiveProfiles("local")
@Transactional
class MerchantStoreTypePersistenceTest {
    @Autowired MerchantStoreCategoryService types;
    @Autowired MerchantStoreMapper stores;
    @Autowired StoreTypeInitializer initializer;
    @Autowired MerchantSummaryService summaries;

    private MerchantStoreCategoryEntity addType() {
        var type = new MerchantStoreCategoryEntity();
        type.setName("测试门店类型_" + UUID.randomUUID());
        types.add(type);
        return type;
    }

    @Test void canCreateReadUpdateAndDeleteUnreferencedType() {
        var type = addType();
        assertEquals(1, types.getById(type.getId()).getStatus());
        assertEquals(0, types.getById(type.getId()).getSort());
        var change = new MerchantStoreCategoryEntity();
        change.setId(type.getId()); change.setName("  改名_" + UUID.randomUUID() + "  ");
        change.setStatus(0); change.setSort(28);
        assertTrue(types.update(change));
        assertEquals(change.getName().trim(), types.getById(type.getId()).getName());
        assertEquals(0, types.getById(type.getId()).getStatus());
        assertEquals(28, types.getById(type.getId()).getSort());
        assertTrue(types.delete(type.getId()));
        assertNull(types.getById(type.getId()));
    }

    @Test void duplicateNamesAreRejectedEvenWhenTheExistingTypeIsDisabled() {
        var existing = addType(); existing.setStatus(0); types.update(existing);
        var duplicate = new MerchantStoreCategoryEntity(); duplicate.setName("  " + existing.getName() + "  ");
        assertThrows(CoolException.class, () -> types.add(duplicate));
        var another = addType(); another.setName(existing.getName());
        assertThrows(CoolException.class, () -> types.update(another));
        assertEquals(1, types.count(QueryWrapper.create().eq(MerchantStoreCategoryEntity::getName, existing.getName())));
    }

    @Test void batchDeleteDoesNotPartiallyDeleteWhenOneTypeHasStores() {
        var linkedStore = stores.selectOneByQuery(QueryWrapper.create().limit(1));
        assertNotNull(linkedStore);
        var unused = addType();
        assertThrows(CoolException.class, () -> types.delete(unused.getId(), linkedStore.getCategoryId()));
        assertNotNull(types.getById(unused.getId()));
        assertNotNull(types.getById(linkedStore.getCategoryId()));
    }

    @Test void renamingAndDisablingKeepStoreLinksAndReadableNames() {
        var store = stores.selectOneByQuery(QueryWrapper.create().limit(1));
        assertNotNull(store);
        var type = types.getById(store.getCategoryId());
        type.setName("改名门店类型_" + UUID.randomUUID()); type.setStatus(0);
        types.update(type);
        var sameStore = stores.selectOneById(store.getId());
        assertEquals(type.getId(), sameStore.getCategoryId());
        summaries.stores(List.of(sameStore));
        assertEquals(type.getName(), sameStore.getCategoryName());
        assertThrows(CoolException.class, () -> types.delete(type.getId()));
    }

    @Test void restartInitializerPreservesCustomNamesStatusAndStoreAssignments() {
        var custom = addType(); custom.setStatus(0); types.update(custom);
        var first = types.list(QueryWrapper.create().orderBy(MerchantStoreCategoryEntity::getId, true)).get(0);
        first.setName("预设改名_" + UUID.randomUUID()); first.setStatus(0); types.update(first);
        var before = stores.selectListByQuery(QueryWrapper.create().orderBy(MerchantStoreEntity::getId, true))
                .stream().map(s -> List.of(s.getId(), s.getCategoryId())).toList();
        initializer.run(null);
        assertEquals(custom.getName(), types.getById(custom.getId()).getName());
        assertEquals(0, types.getById(custom.getId()).getStatus());
        assertEquals(first.getName(), types.getById(first.getId()).getName());
        assertEquals(0, types.getById(first.getId()).getStatus());
        assertEquals(before, stores.selectListByQuery(QueryWrapper.create().orderBy(MerchantStoreEntity::getId, true))
                .stream().map(s -> List.of(s.getId(), s.getCategoryId())).toList());
    }
}
