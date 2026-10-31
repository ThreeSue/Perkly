package com.cool.modules.coupon.controller.admin.resp;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 返回只读购券订单名称金额与明细且不暴露用户私密凭证
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */

public record PurchaseOrderView(Long id,String orderNo,String merchantName,String customerName,String channel,
    String status,String statusText,String paymentStatus,BigDecimal totalAmount,Date createdAt,Date paidAt,Date expiresAt,
    List<Item> items) {
    public record Item(String couponName,Integer quantity,BigDecimal unitPrice,BigDecimal subtotal) {}
}
