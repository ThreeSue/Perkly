package com.cool.modules.merchant.service.impl;

import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.mapper.MerchantStoreCategoryMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.service.MerchantStoreCategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Objects;

/**
 * 由运营人员统一维护公共门店类型，商家共用，关联门店后禁止删除。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MerchantStoreCategoryServiceImpl
        extends BaseServiceImpl<MerchantStoreCategoryMapper, MerchantStoreCategoryEntity>
        implements MerchantStoreCategoryService {
    @org.springframework.beans.factory.annotation.Autowired
    private MerchantSummaryService summaries;
    @org.springframework.beans.factory.annotation.Autowired
    private MerchantStoreMapper stores;

    @Override
    public Object page(cn.hutool.json.JSONObject params, com.mybatisflex.core.paginate.Page<MerchantStoreCategoryEntity> page, QueryWrapper query) {
        var result = (com.mybatisflex.core.paginate.Page<MerchantStoreCategoryEntity>) super.page(params, page, ordered(query));
        summaries.categories(result.getRecords());
        return result;
    }

    @Override
    public Object list(cn.hutool.json.JSONObject params, QueryWrapper query) {
        var rows = (java.util.List<MerchantStoreCategoryEntity>) super.list(params, ordered(query));
        summaries.categories(rows);
        return rows;
    }

    private QueryWrapper ordered(QueryWrapper query) {
        return query.orderBy(MerchantStoreCategoryEntity::getSort, true)
                .orderBy(MerchantStoreCategoryEntity::getId, true);
    }

    @Override
    public Long add(MerchantStoreCategoryEntity entity) {
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        validate(entity);
        try {
            return super.add(entity);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new CoolException("门店类型名称不能重复");
        }
    }

    @Override
    public boolean update(MerchantStoreCategoryEntity entity) {
        MerchantStoreCategoryEntity existing = lock(entity.getId());
        if (entity.getName() == null) entity.setName(existing.getName());
        if (entity.getSort() == null) entity.setSort(existing.getSort());
        if (entity.getStatus() == null) entity.setStatus(existing.getStatus());
        validate(entity);
        try {
            return super.update(entity);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new CoolException("门店类型名称不能重复");
        }
    }

    @Override
    public boolean delete(Long... ids) {
        if (ids == null || ids.length == 0 || Arrays.stream(ids).anyMatch(id -> id == null || id <= 0))
            throw new CoolException("请选择有效的门店类型");
        Long[] ordered = Arrays.stream(ids).distinct().sorted().toArray(Long[]::new);
        for (Long id : ordered) {
            MerchantStoreCategoryEntity type = lock(id);
            // 使用锁定读，避免批量删除事务中的旧快照漏掉刚提交的新门店。
            if (stores.selectOneByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getCategoryId, id)
                    .limit(1).forUpdate()) != null)
                throw new CoolException("门店类型“" + type.getName() + "”下还有门店，请先调整门店归属后再删除");
        }
        return super.delete(ordered);
    }

    private MerchantStoreCategoryEntity lock(Long id) {
        if (id == null || id <= 0) throw new CoolException("请选择有效的门店类型");
        MerchantStoreCategoryEntity row = mapper.selectOneByQuery(QueryWrapper.create()
                .eq(MerchantStoreCategoryEntity::getId, id).forUpdate());
        if (row == null) throw new CoolException("门店类型不存在");
        return row;
    }

    private void validate(MerchantStoreCategoryEntity entity) {
        String name = entity.getName();
        if (name == null || name.isBlank()) throw new CoolException("门店类型名称不能为空");
        entity.setName(name.trim());
        if (entity.getName().length() > 50) throw new CoolException("门店类型名称不能超过50个字符");
        if (entity.getSort() < 0) throw new CoolException("排序不能小于0");
        if (!Objects.equals(entity.getStatus(), 0) && !Objects.equals(entity.getStatus(), 1))
            throw new CoolException("状态只能为启用或停用");
        QueryWrapper duplicate = QueryWrapper.create().eq(MerchantStoreCategoryEntity::getName, entity.getName());
        if (entity.getId() != null) duplicate.ne(MerchantStoreCategoryEntity::getId, entity.getId());
        if (mapper.selectCountByQuery(duplicate) > 0) throw new CoolException("门店类型名称不能重复");
    }
}
