package com.cool.modules.wallet.entity;

import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.math.BigDecimal;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 平台储值账务流水与支付快照
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:45:35
 */
@Getter
@Setter
@Table("wallet_transaction")
@org.dromara.autotable.annotation.TableIndex(name = "uk_wallet_transaction_request", type = org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields = {"customerId", "requestId"})
public class WalletTransactionEntity extends BaseEntity<WalletTransactionEntity> {
    @ColumnDefine(notNull = true, comment = "顾客账号")
    private Long customerId;
    @ColumnDefine(length = 64, notNull = true, comment = "幂等请求")
    private String requestId;
    @ColumnDefine(notNull = true, comment = "RECHARGE或DIRECT或PURCHASE")
    private String type;
    @ColumnDefine(notNull = true, comment = "SUCCESS或FAILED")
    private String status;
    @org.dromara.autotable.annotation.Index(type = org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE)
    @ColumnDefine(comment = "关联购券订单")
    private Long purchaseOrderId;
    @org.dromara.autotable.annotation.Index(type = org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE)
    @ColumnDefine(comment = "关联无券消费账单")
    private Long billId;
    @ColumnDefine(comment = "交易商家")
    private Long merchantId;
    @ColumnDefine(comment = "交易门店")
    private Long storeId;
    @ColumnDefine(comment = "门店名称快照")
    private String storeName;
    @ColumnDefine(comment = "商家名称快照")
    private String merchantName;
    @ColumnDefine(notNull = true, comment = "原金额，分")
    private Long originalAmount;
    @ColumnDefine(notNull = true, comment = "实际充值或扣款金额，分")
    private Long amount;
    @ColumnDefine(notNull = true, comment = "活动优惠金额，分")
    private Long discountAmount;
    @ColumnDefine(comment = "命中活动")
    private Long activityId;
    @ColumnDefine(comment = "活动名称快照")
    private String activityTitle;
    @ColumnDefine(comment = "折扣快照，10表示无折扣")
    private BigDecimal discountRate;
    @ColumnDefine(notNull = true, comment = "交易后余额，分")
    private Long balanceAfter;
    @ColumnDefine(comment="充值活动")
    private Long rechargeActivityId;
    @ColumnDefine(comment="充值赠额，分")
    private Long rechargeBonus;
    @ColumnDefine(comment="充值总到账，分")
    private Long rechargeTotal;
    @ColumnDefine(type="mediumtext",comment="充值赠送活动与档位快照")
    private String rechargeSnapshot;
}
