package com.cool.modules.space.service.impl;

import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolException;
import com.cool.modules.space.entity.SpaceTypeEntity;
import com.cool.modules.space.mapper.SpaceTypeMapper;
import com.cool.modules.space.service.SpaceTypeService;
import org.springframework.stereotype.Service;

/**
 * 文件空间信息
 */
@Service
public class SpaceTypeServiceImpl extends BaseServiceImpl<SpaceTypeMapper, SpaceTypeEntity>
        implements SpaceTypeService {
    @Override
    public Object info(Long id) {
        var category = mapper.selectOneById(id);
        if (category != null && category.getMerchantId() != null) throw new CoolException("商家分类不属于平台文件空间", 403);
        return category;
    }

    @Override
    public Long add(SpaceTypeEntity entity) {
        requirePlatformInput(entity);
        return super.add(entity);
    }

    @Override
    public boolean update(SpaceTypeEntity entity) {
        info(entity.getId());
        requirePlatformInput(entity);
        return super.update(entity);
    }

    @Override
    public boolean delete(Long... ids) {
        for (Long id : ids) info(id);
        return super.delete(ids);
    }

    private void requirePlatformInput(SpaceTypeEntity entity) {
        if (entity.getMerchantId() != null) throw new CoolException("商家分类不属于平台文件空间", 403);
        if (entity.getParentId() != null && entity.getParentId() > 0) info(entity.getParentId().longValue());
    }
}
