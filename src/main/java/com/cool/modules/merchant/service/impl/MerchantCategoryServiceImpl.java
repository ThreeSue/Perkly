package com.cool.modules.merchant.service.impl;

import com.cool.core.base.BaseServiceImpl;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.mapper.MerchantCategoryMapper;
import com.cool.modules.merchant.service.MerchantCategoryService;
import org.springframework.stereotype.Service;
import com.cool.core.exception.CoolException;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.util.StrUtil;

/**
 * 商家分类
 */
@Service
public class MerchantCategoryServiceImpl
        extends BaseServiceImpl<MerchantCategoryMapper, MerchantCategoryEntity>
        implements MerchantCategoryService {

    @Override
    public Long add(MerchantCategoryEntity entity) {
        // 1. 检查名称，并去掉两端的空格
        if (StrUtil.isBlank(entity.getName())) {
            throw new CoolException("分类名称不能为空");
        }
        entity.setName(entity.getName().trim());

        // 2. 查询同名分类
        QueryWrapper query = QueryWrapper.create()
                .eq(MerchantCategoryEntity::getName, entity.getName());

        // 3. 限定为同一个上级
        if (entity.getParentId() == null) {
            query.isNull(MerchantCategoryEntity::getParentId);
        } else {
            query.eq(MerchantCategoryEntity::getParentId, entity.getParentId());
        }

        if (this.count(query) > 0) {
            throw new CoolException("同一个上级下，分类名称不能重复");
        }

        // 4. 校验通过，继续调用框架原来的新增逻辑
        return super.add(entity);
    }



}