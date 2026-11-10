package com.cool.modules.support.entity;
import com.mybatisflex.annotation.*;import lombok.Getter;import lombok.Setter;import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 只读映射本人持券规则与状态且不映射任何核销凭证
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:16:00
 */
@Getter @Setter @Table("customer_coupon") @org.dromara.autotable.annotation.Ignore
public class SupportOwnedCouponEntity {
 @Id private Long id;
 private Long customerId;private Long merchantId;private Long couponTemplateId;
 private Integer status;private Date validFrom;private Date validTo;private Date issuedAt;private Date usedAt;
 private String ruleSnapshot;
}
