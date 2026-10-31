package com.cool.modules.workspace.entity;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 只读映射现有券门店关系以校验指定门店岗位数据范围
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:35:00
 */
@Getter @Setter @Table("coupon_store") @org.dromara.autotable.annotation.Ignore
public class WorkspaceCouponStoreEntity {
    @Id private Long id;
    private Long merchantId;
    private Long couponTemplateId;
    private Long storeId;
}
