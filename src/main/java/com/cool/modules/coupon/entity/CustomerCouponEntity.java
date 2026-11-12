package com.cool.modules.coupon.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 读取既有持券记录的归属及券码用于消费结算
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 20:56:06
 */
@Getter @Setter @Table("customer_coupon")
@org.dromara.autotable.annotation.Ignore
public class CustomerCouponEntity {
    @Id private Long id;
    private Long customerId;
    private Long couponTemplateId;
    private String code;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String ruleSnapshot;
}
