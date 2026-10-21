package com.cool.modules.merchant.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.Index;

/** 商家旗下的经营门店。 */
@Getter
@Setter
@Table(value = "store", comment = "门店资料")
public class MerchantStoreEntity extends BaseEntity<MerchantStoreEntity> {
    @Index
    @ColumnDefine(comment = "所属商家ID", notNull = true)
    private Long merchantId;

    @Index
    @ColumnDefine(comment = "平台公共门店类型ID，关联store_type.id", notNull = true)
    private Long categoryId;

    @ColumnDefine(comment = "门店名称", length = 100, notNull = true)
    private String name;

    @ColumnDefine(comment = "门店封面图片地址", length = 500)
    private String cover;

    @ColumnDefine(comment = "门店详细地址", length = 500, notNull = true)
    private String address;

    @ColumnDefine(comment = "门店经度", length = 10, decimalLength = 7)
    private java.math.BigDecimal longitude;

    @ColumnDefine(comment = "门店纬度", length = 10, decimalLength = 7)
    private java.math.BigDecimal latitude;

    @ColumnDefine(comment = "门店联系电话", length = 30, notNull = true)
    private String phone;

    @ColumnDefine(comment = "营业时间描述，可保存结构化JSON", type = "text")
    private String businessHours;

    @Index
    @ColumnDefine(comment = "门店状态：0停用，1营业", notNull = true, defaultValue = "1")
    private Integer status;

    @org.dromara.autotable.annotation.Ignore
    @com.mybatisflex.annotation.Column(ignore = true)
    private String merchantName;

    @org.dromara.autotable.annotation.Ignore
    @com.mybatisflex.annotation.Column(ignore = true)
    private String categoryName;
}
