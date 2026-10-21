package com.cool.modules.merchant.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/** 平台公共门店类型；保留原实体名称以兼容已有接口。 */
@Getter
@Setter
@Table(value = "store_type", comment = "平台公共门店类型")
public class MerchantStoreCategoryEntity extends BaseEntity<MerchantStoreCategoryEntity> {
    @org.dromara.autotable.annotation.Index(type = org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE)
    @ColumnDefine(comment = "类型名称", length = 50, notNull = true)
    private String name;

    @ColumnDefine(comment = "排序值", notNull = true, defaultValue = "0")
    private Integer sort;

    @ColumnDefine(comment = "状态：0停用，1启用", notNull = true, defaultValue = "1")
    private Integer status;

    @org.dromara.autotable.annotation.Ignore
    @com.mybatisflex.annotation.Column(ignore = true)
    private Long storeCount;
}
