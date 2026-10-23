package com.cool.modules.merchant.entity;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 表示店长或员工账号唯一所属的门店授权
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 10:28:14
 */
@Getter @Setter
@Table("base_sys_user_store")
public class MerchantUserStoreEntity {
    @Id(keyType = KeyType.Auto) private Long id;
    private Long merchantId;
    private Long userId;
    private Long storeId;
}
