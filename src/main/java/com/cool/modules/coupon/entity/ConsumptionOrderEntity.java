package com.cool.modules.coupon.entity;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 映射到店消费账单及金额快照并提供类型安全的查询字段
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 10:35:36
 */
@Getter @Setter @Table("consumption_order")
public class ConsumptionOrderEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private String orderNo;
    private Long merchantId;
    private Long storeId;
    private String merchantName;
    private String storeName;
    private Long operatorId;
    private String operatorName;
    private String requestId;
    private Long customerCouponId;
    private String couponCode;
    private String couponName;
    private String couponType;
    private String couponSnapshot;
    private String status;
    private Long consumptionAmount;
    private Long discountAmount;
    private Long payableAmount;
    private Long receivedAmount;
    private String paymentMode;
    private String remark;
    private Integer version;
    private Long activeCouponId;
    private Long redemptionId;
    private Long confirmedBy;
    private String confirmedByName;
    private Date createdAt;
    private Date completedAt;
}
