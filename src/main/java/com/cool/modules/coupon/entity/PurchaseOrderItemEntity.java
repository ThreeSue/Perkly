package com.cool.modules.coupon.entity;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 映射现有购券数据供只读监管查询使用
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */

@Getter @Setter @Table("purchase_order_item") @org.dromara.autotable.annotation.Ignore
public class PurchaseOrderItemEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private Long merchantId;
    private Long orderId;
    private Long couponTemplateId;
    private String couponName;
    private String ruleSnapshot;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}
