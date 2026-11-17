package com.cool.modules.wallet.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.Index;
import org.dromara.autotable.annotation.enums.IndexTypeEnum;

import java.util.Date;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存一次性储值付款码的摘要、有效期及结清账单
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 13:33:52
 */
@Getter
@Setter
@Table("wallet_payment_code")
public class WalletPaymentCodeEntity extends BaseEntity<WalletPaymentCodeEntity> {
    @Index(type = IndexTypeEnum.UNIQUE)
    @ColumnDefine(length = 64, notNull = true, comment = "付款码 SHA-256 摘要")
    private String tokenHash;
    @ColumnDefine(notNull = true, comment = "授权顾客")
    private Long customerId;
    @ColumnDefine(notNull = true, comment = "有效截止时间")
    private Date expiresAt;
    @ColumnDefine(comment = "已结清的账单")
    private Long billId;
}
