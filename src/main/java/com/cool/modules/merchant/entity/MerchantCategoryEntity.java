package com.cool.modules.merchant.entity;


import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description : [商家分类实体类]
 * @Version : [v1.1]
 * @Date: 2026/10/9
 * @Time: 23:33
 */

@Getter
@Setter
@Table(value = "merchant_category", comment = "商家分类")
public class MerchantCategoryEntity
        extends BaseEntity<MerchantCategoryEntity> {

    @ColumnDefine(comment = "上级分类ID，顶级分类为空")
    private Long parentId;

    @ColumnDefine(comment = "分类名称", length = 50, notNull = true)
    private String name;

    @ColumnDefine(comment = "分类图标地址", length = 500)
    private String icon;

    @ColumnDefine(comment = "排序值，越小越靠前",
            notNull = true, defaultValue = "0")
    private Integer sort;

    @ColumnDefine(comment = "前台展示：0否，1是",
            notNull = true, defaultValue = "1")
    private Integer showOnFront;

    @ColumnDefine(comment = "状态：0停用，1启用",
            notNull = true, defaultValue = "1")
    private Integer status;
}