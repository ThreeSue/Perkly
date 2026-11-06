package com.cool.modules.space.service.impl;

import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolException;
import com.cool.modules.space.entity.SpaceInfoEntity;
import com.cool.modules.space.mapper.SpaceInfoMapper;
import com.cool.modules.space.mapper.SpaceTypeMapper;
import com.cool.modules.space.service.SpaceInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文件空间信息
 */
@Service
@RequiredArgsConstructor
public class SpaceInfoServiceImpl extends BaseServiceImpl<SpaceInfoMapper, SpaceInfoEntity>
        implements SpaceInfoService {
    private final SpaceTypeMapper categories;

    @Override
    public Object info(Long id) {
        var item = mapper.selectOneById(id);
        if (item != null && item.getMerchantId() != null) throw new CoolException("商家素材不属于平台文件空间", 403);
        return item;
    }

    @Override
    public Long add(SpaceInfoEntity entity) {
        requirePlatformInput(entity);
        return super.add(entity);
    }

    @Override
    public boolean update(SpaceInfoEntity entity) {
        info(entity.getId());
        requirePlatformInput(entity);
        return super.update(entity);
    }

    @Override
    public boolean delete(Long... ids) {
        for (Long id : ids) info(id);
        return super.delete(ids);
    }

    private void requirePlatformInput(SpaceInfoEntity entity) {
        if (entity.getMerchantId() != null || entity.getUploaderId() != null || entity.getMaterialDeleted() != null) {
            throw new CoolException("商家素材不属于平台文件空间", 403);
        }
        if (entity.getClassifyId() != null && entity.getClassifyId() > 0) {
            var category = categories.selectOneById(entity.getClassifyId());
            if (category != null && category.getMerchantId() != null) {
                throw new CoolException("商家分类不属于平台文件空间", 403);
            }
        }
    }
}
