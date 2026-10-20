package com.cool.modules.coupon.controller.admin.params;

import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收和展示优惠券包含的菜品商品或服务及数量
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 16:25:00
 */
@Data
public class CouponContentParam {
    private Long productId;
    private String name;
    private String type;
    private Integer quantity;
    private String itemDescription;
}
