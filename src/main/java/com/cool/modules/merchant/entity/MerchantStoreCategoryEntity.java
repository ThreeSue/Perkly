package com.cool.modules.merchant.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.Index;

/** 商家自有的门店分类。 */
@Getter
@Setter
@Table(value = "store_category", comment = "门店分类")
public class MerchantStoreCategoryEntity extends BaseEntity<MerchantStoreCategoryEntity> {
    @Index
    @ColumnDefine(comment = "所属商家ID", notNull = true)
    private Long merchantId;

    @ColumnDefine(comment = "上级门店分类ID")
    private Long parentId;

    @ColumnDefine(comment = "分类名称", length = 50, notNull = true)
    private String name;

    @ColumnDefine(comment = "分类图标", length = 500)
    private String icon;

    @ColumnDefine(comment = "排序值，越小越靠前", notNull = true, defaultValue = "0")
    private Integer sort;

    @ColumnDefine(comment = "前台展示：0否，1是", notNull = true, defaultValue = "1")
    private Integer showOnFront;

    @ColumnDefine(comment = "状态：0停用，1启用", notNull = true, defaultValue = "1")
    private Integer status;

    @org.dromara.autotable.annotation.Ignore
    @com.mybatisflex.annotation.Column(ignore = true)
    private String merchantName;

    @org.dromara.autotable.annotation.Ignore
    @com.mybatisflex.annotation.Column(ignore = true)
    private Long storeCount;
}
