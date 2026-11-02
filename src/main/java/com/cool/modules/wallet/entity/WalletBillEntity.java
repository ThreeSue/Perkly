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
 * @Description: 门店无券直接消费账单
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:45:36
 */
@Getter @Setter @Table("wallet_bill")
@org.dromara.autotable.annotation.TableIndex(name="uk_wallet_bill_request",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"operatorId","requestId"})
public class WalletBillEntity extends BaseEntity<WalletBillEntity>{
    @org.dromara.autotable.annotation.Ignore @Column(ignore=true) private String storeName;
    @org.dromara.autotable.annotation.Ignore @Column(ignore=true) private String merchantName;
    @org.dromara.autotable.annotation.Index(type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE)
    @ColumnDefine(notNull=true,comment="账单随机编号") private String billNo;
    @ColumnDefine(notNull=true,comment="开单员工") private Long operatorId;
    @ColumnDefine(length=64,notNull=true,comment="开单请求") private String requestId;
    @ColumnDefine(notNull=true,comment="所属商家") private Long merchantId;
    @ColumnDefine(notNull=true,comment="所属门店") private Long storeId;
    @ColumnDefine(notNull=true,comment="消费金额，分") private Long amount;
    @ColumnDefine(notNull=true,comment="PENDING或PAID或CANCELLED") private String status;
    @ColumnDefine(length=200,comment="开单备注") private String remark;
    @ColumnDefine(comment="支付顾客") private Long customerId;
    @ColumnDefine(comment="支付流水") private Long transactionId;
}
