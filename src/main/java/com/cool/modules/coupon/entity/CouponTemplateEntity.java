package com.cool.modules.coupon.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.Ignore;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存商家优惠券模板规则及运营端展示信息
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:12:32
 */
@Getter
@Setter
@Table(value = "coupon_template", comment = "优惠券定义表")
public class CouponTemplateEntity extends BaseEntity<CouponTemplateEntity> {
    @ColumnDefine(comment = "所属商家ID", notNull = true)
    private Long merchantId;

    @ColumnDefine(comment = "券名称", length = 100, notNull = true)
    private String name;

    @ColumnDefine(comment = "券类型编码", length = 20, notNull = true)
    private String type;

    @ColumnDefine(comment = "使用说明", length = 2000)
    private String description;

    @ColumnDefine(comment = "封面图片地址", length = 500)
    private String cover;

    @ColumnDefine(comment = "售价", length = 10, decimalLength = 2, notNull = true, defaultValue = "0.00")
    private BigDecimal salePrice;

    @ColumnDefine(comment = "适用范围：0全部营业门店，1指定门店", type = "tinyint", notNull = true, defaultValue = "0")
    private Integer scopeType;

    @ColumnDefine(comment = "最低消费", length = 10, decimalLength = 2)
    private BigDecimal minSpend;

    @ColumnDefine(comment = "满减金额", length = 10, decimalLength = 2)
    private BigDecimal discountAmount;

    @ColumnDefine(comment = "代金券抵扣金额", length = 10, decimalLength = 2)
    private BigDecimal deductAmount;

    @ColumnDefine(comment = "最高优惠金额", length = 10, decimalLength = 2)
    private BigDecimal maxDiscount;

    @ColumnDefine(comment = "折扣数，8.80表示8.8折", length = 5, decimalLength = 2)
    private BigDecimal discountRate;

    @ColumnDefine(comment = "发行上限，NULL不限量")
    private Long issueLimit;

    @ColumnDefine(comment = "已发放数量", notNull = true, defaultValue = "0")
    private Long issuedCount;

    @ColumnDefine(comment = "每位顾客领取购买总量上限", notNull = true, defaultValue = "1")
    private Integer perCustomerLimit;

    @ColumnDefine(comment = "领取购买开始时间")
    private Date saleStartTime;

    @ColumnDefine(comment = "领取购买结束时间")
    private Date saleEndTime;

    @ColumnDefine(comment = "固定有效期开始")
    private Date validFrom;

    @ColumnDefine(comment = "固定有效期结束")
    private Date validTo;

    @ColumnDefine(comment = "FIXED固定日期，AFTER_ISSUE发券后按天有效", length = 20, notNull = true)
    private String validityType;

    @ColumnDefine(comment = "发券后有效天数")
    private Integer validDays;

    @ColumnDefine(comment = "是否同享其他优惠：0否，1是", type = "tinyint", notNull = true, defaultValue = "0")
    private Integer allowCombine;

    @ColumnDefine(comment = "规则版本", notNull = true, defaultValue = "1")
    private Integer ruleVersion;

    @ColumnDefine(comment = "状态：0草稿，1启用，2停用", type = "tinyint", notNull = true, defaultValue = "0")
    private Integer status;

    @Ignore
    @Column(ignore = true)
    private String merchantName;

    @Ignore
    @Column(ignore = true)
    private List<Long> storeIds;

    @Ignore
    @Column(ignore = true)
    private List<String> storeNames;

    @Ignore
    @Column(ignore = true)
    private List<com.cool.modules.coupon.controller.admin.params.CouponContentParam> contentItems;

    @Ignore
    @Column(ignore = true)
    private java.util.Map<String, Object> publication;
}

