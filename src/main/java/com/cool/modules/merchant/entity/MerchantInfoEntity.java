package com.cool.modules.merchant.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/** 平台运营端管理的商家资料。 */
@Getter
@Setter
@Table(value = "merchant_info", comment = "商家资料")
public class MerchantInfoEntity extends BaseEntity<MerchantInfoEntity> {
    @ColumnDefine(comment = "商家名称", length = 100, notNull = true)
    private String name;
    @org.dromara.autotable.annotation.Index
    @ColumnDefine(comment = "所属商家分类ID", notNull = true)
    private Long categoryId;
    @ColumnDefine(comment = "商家Logo地址", length = 500)
    private String logo;
    @ColumnDefine(comment = "联系人姓名", length = 50, notNull = true)
    private String contactName;
    @ColumnDefine(comment = "联系电话", length = 30, notNull = true)
    private String contactPhone;
    @ColumnDefine(comment = "省", length = 50)
    private String province;
    @ColumnDefine(comment = "市", length = 50)
    private String city;
    @ColumnDefine(comment = "区县", length = 50)
    private String district;
    @ColumnDefine(comment = "详细地址", length = 500)
    private String address;
    @ColumnDefine(comment = "商家简介", length = 2000)
    private String introduction;
    @ColumnDefine(comment = "平台管控状态：0停用，1启用", notNull = true, defaultValue = "1")
    private Integer status;
    @ColumnDefine(comment = "平台内部备注", length = 1000)
    private String remark;


    @org.dromara.autotable.annotation.Ignore
    @com.mybatisflex.annotation.Column(ignore = true)
    private Long storeCount;
}
