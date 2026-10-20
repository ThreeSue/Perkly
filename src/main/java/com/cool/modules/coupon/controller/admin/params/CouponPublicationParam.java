package com.cool.modules.coupon.controller.admin.params;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收优惠券发布操作的模板标识和预期规则版本
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 16:45:37
 */
@Data
public class CouponPublicationParam {
    @NotNull @Positive
    private Long id;
    @NotNull @Positive
    private Integer version;
    private Boolean approved;
}
