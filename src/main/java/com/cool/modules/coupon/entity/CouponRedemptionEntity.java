package com.cool.modules.coupon.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 映射成功核销流水并为记录权限过滤提供实体字段引用
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 10:52:35
 */
@Getter
@Setter
@Table("coupon_redemption")
public class CouponRedemptionEntity {
    @Id(keyType = KeyType.Auto) private Long id;
    private Long merchantId;
    private Long customerCouponId;
    private Long storeId;
    private String operatorType;
    private Long operatorId;
    private BigDecimal consumptionAmount;
    private BigDecimal discountAmount;
    private Date redeemedAt;
}
