package com.cool.modules.merchant.service.impl;

import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.mapper.MerchantCategoryMapper;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.service.MerchantCategoryService;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MerchantCategoryServiceImpl extends BaseServiceImpl<MerchantCategoryMapper, MerchantCategoryEntity>
        implements MerchantCategoryService {
    @Autowired
    private MerchantInfoMapper merchantMapper;

    @Override
    public Long add(MerchantCategoryEntity entity) {
        validate(entity);
        return super.add(entity);
    }

    @Override
    public boolean update(MerchantCategoryEntity entity) {
        if (entity.getId() == null || getById(entity.getId()) == null) throw new CoolException("商家分类不存在");
        validate(entity);
        return super.update(entity);
    }

    @Override
    public boolean delete(Long... ids) {
        for (Long id : ids) {
            if (count(QueryWrapper.create().eq("parent_id", id)) > 0)
                throw new CoolException("该分类下还有子分类，不能删除");
            if (merchantMapper.selectCountByQuery(QueryWrapper.create().eq("category_id", id)) > 0)
                throw new CoolException("该分类下还有商家，不能删除");
        }
        return super.delete(ids);
    }

    private void validate(MerchantCategoryEntity entity) {
        if (StrUtil.isBlank(entity.getName())) throw new CoolException("分类名称不能为空");
        entity.setName(entity.getName().trim());
        if (entity.getName().length() > 50) throw new CoolException("分类名称不能超过50个字符");
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getShowOnFront() == null) entity.setShowOnFront(1);
        if (entity.getIcon() != null && entity.getIcon().length() > 500) throw new CoolException("分类图标地址不能超过500个字符");
        if (entity.getSort() < 0) throw new CoolException("排序不能小于0");
        if (!flag(entity.getStatus()) || !flag(entity.getShowOnFront())) throw new CoolException("状态和前台展示值只能为0或1");
        if (entity.getParentId() != null) {
            if (entity.getParentId().equals(entity.getId())) throw new CoolException("上级分类不能是自身");
            MerchantCategoryEntity parent = getById(entity.getParentId());
            if (parent == null) throw new CoolException("上级分类不存在");
            if (parent.getParentId() != null) throw new CoolException("商家分类最多支持两级，不能选择下级分类作为上级");
            if (entity.getId() != null && count(QueryWrapper.create().eq("parent_id", entity.getId())) > 0)
                throw new CoolException("该分类下还有子分类，不能调整为二级分类");
        }
        QueryWrapper duplicate = QueryWrapper.create().eq("name", entity.getName());
        if (entity.getParentId() == null) duplicate.isNull("parent_id");
        else duplicate.eq("parent_id", entity.getParentId());
        if (entity.getId() != null) duplicate.ne("id", entity.getId());
        if (count(duplicate) > 0) throw new CoolException("同一个上级下，分类名称不能重复");
    }
    private boolean flag(Integer value) { return Integer.valueOf(0).equals(value) || Integer.valueOf(1).equals(value); }
}
