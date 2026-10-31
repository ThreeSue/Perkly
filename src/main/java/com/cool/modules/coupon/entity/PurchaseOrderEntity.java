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

@Getter @Setter @Table("purchase_order") @org.dromara.autotable.annotation.Ignore
public class PurchaseOrderEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private String orderNo;
    private Long merchantId;
    private Long customerId;
    private String channel;
    private String orderStatus;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private Date expiresAt;
    private Date paidAt;
    private Date createTime;
}
