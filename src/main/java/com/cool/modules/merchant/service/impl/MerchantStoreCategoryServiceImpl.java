package com.cool.modules.merchant.service.impl;

import cn.hutool.core.util.StrUtil;
import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.mapper.MerchantStoreCategoryMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.service.MerchantInfoService;
import com.cool.modules.merchant.service.MerchantStoreCategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MerchantStoreCategoryServiceImpl
        extends BaseServiceImpl<MerchantStoreCategoryMapper, MerchantStoreCategoryEntity>
        implements MerchantStoreCategoryService {
    private final MerchantInfoService merchantService;
    private final MerchantStoreMapper storeMapper;

    public MerchantStoreCategoryServiceImpl(MerchantInfoService merchantService,
            MerchantStoreMapper storeMapper) {
        this.merchantService = merchantService;
        this.storeMapper = storeMapper;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private MerchantSummaryService summaries;

    @Override
    public Object page(cn.hutool.json.JSONObject params, com.mybatisflex.core.paginate.Page<MerchantStoreCategoryEntity> page, QueryWrapper query) {
        com.mybatisflex.core.paginate.Page<MerchantStoreCategoryEntity> result = (com.mybatisflex.core.paginate.Page<MerchantStoreCategoryEntity>) super.page(params, page, query);
        summaries.categories(result.getRecords());
        return result;
    }

    @Override
    public Object list(cn.hutool.json.JSONObject params, QueryWrapper query) {
        java.util.List<MerchantStoreCategoryEntity> rows = (java.util.List<MerchantStoreCategoryEntity>) super.list(params, query);
        summaries.categories(rows);
        return rows;
    }

    @Override
    public Long add(MerchantStoreCategoryEntity entity) {
        normalize(entity);
        validate(entity, null);
        return super.add(entity);
    }

    @Override
    public boolean update(MerchantStoreCategoryEntity entity) {
        if (entity.getId() == null) throw new CoolException("门店分类ID不能为空");
        MerchantStoreCategoryEntity existing = getById(entity.getId());
        if (existing == null) throw new CoolException("门店分类不存在");
        normalize(entity);
        validate(entity, existing);
        return super.update(entity);
    }

    @Override
    public boolean delete(Long... ids) {
        for (Long id : ids) {
            if (count(QueryWrapper.create().eq("parent_id", id)) > 0) {
                throw new CoolException("请先删除该分类下的子分类");
            }
            if (storeMapper.selectCountByQuery(QueryWrapper.create()
                    .eq("category_id", id)) > 0) {
                throw new CoolException("该分类下还有门店，不能删除");
            }
        }
        return super.delete(ids);
    }

    private void normalize(MerchantStoreCategoryEntity entity) {
        if (entity.getName() != null) entity.setName(entity.getName().trim());
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getShowOnFront() == null) entity.setShowOnFront(1);
        if (entity.getStatus() == null) entity.setStatus(1);
    }

    private void validate(MerchantStoreCategoryEntity entity, MerchantStoreCategoryEntity existing) {
        if (entity.getMerchantId() == null) throw new CoolException("所属商家不能为空");
        MerchantInfoEntity merchant = merchantService.getById(entity.getMerchantId());
        if (merchant == null) throw new CoolException("所属商家不存在");
        boolean requireEnabled = existing == null || (Integer.valueOf(1).equals(entity.getStatus()) && !Integer.valueOf(1).equals(existing.getStatus()))
                || !java.util.Objects.equals(existing.getMerchantId(), entity.getMerchantId());
        if (requireEnabled && !Integer.valueOf(1).equals(merchant.getStatus())) throw new CoolException("请选择启用中的商家");
        if (StrUtil.isBlank(entity.getName())) throw new CoolException("分类名称不能为空");
        if (entity.getSort() < 0) throw new CoolException("排序不能小于0");
        if (entity.getIcon() != null && entity.getIcon().length() > 500) throw new CoolException("图标地址不能超过500个字符");
        if (entity.getName().length() > 50) throw new CoolException("分类名称不能超过50个字符");
        if (!validFlag(entity.getStatus()) || !validFlag(entity.getShowOnFront())) {
            throw new CoolException("分类状态和前台展示值只能为0或1");
        }
        if (existing != null && !existing.getMerchantId().equals(entity.getMerchantId())) {
            boolean hasChildren = count(QueryWrapper.create().eq("parent_id",
                    existing.getId())) > 0;
            boolean hasStores = storeMapper.selectCountByQuery(QueryWrapper.create()
                    .eq("category_id", existing.getId())) > 0;
            if (hasChildren || hasStores) throw new CoolException("分类已关联子分类或门店，不能更换所属商家");
        }
        if (entity.getParentId() != null) {
            Set<Long> visited = new HashSet<>();
            Long parentId = entity.getParentId();
            while (parentId != null) {
                if (entity.getId() != null && parentId.equals(entity.getId())) {
                    throw new CoolException("上级分类不能选择自身或下级分类");
                }
                if (!visited.add(parentId)) throw new CoolException("门店分类层级存在循环关联");
                MerchantStoreCategoryEntity parent = getById(parentId);
                if (parent == null) throw new CoolException("上级门店分类不存在");
                if (!parent.getMerchantId().equals(entity.getMerchantId())) {
                    throw new CoolException("上级分类必须属于同一个商家");
                }
                parentId = parent.getParentId();
            }
        }
        QueryWrapper duplicate = QueryWrapper.create()
                .eq("merchant_id", entity.getMerchantId())
                .eq("name", entity.getName());
        if (entity.getParentId() == null) duplicate.isNull("parent_id");
        else duplicate.eq("parent_id", entity.getParentId());
        if (entity.getId() != null) duplicate.ne("id", entity.getId());
        if (count(duplicate) > 0) throw new CoolException("同一商家、同一上级下分类名称不能重复");
    }

    private boolean validFlag(Integer value) { return Integer.valueOf(0).equals(value) || Integer.valueOf(1).equals(value); }
}
