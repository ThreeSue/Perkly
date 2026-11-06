package com.cool.modules.space.entity;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import com.cool.core.annotation.ColumnDefine;
import lombok.Getter;
import lombok.Setter;

/**
 * 图片空间信息分类
 */
@Getter
@Setter
@Table(value = "space_type", comment = "图片空间信息分类")
public class SpaceTypeEntity extends BaseEntity<SpaceTypeEntity> {
    @org.dromara.autotable.annotation.Index
    @ColumnDefine(comment = "商家分类归属，空值为原平台分类")
    private Long merchantId;

    @ColumnDefine(comment = "类别名称", notNull = true)
    private String name;

    @ColumnDefine(comment = "父分类ID")
    private Integer parentId;
}
