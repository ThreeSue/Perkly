package com.cool.modules.wallet.entity;

import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存商家和门店额外活动负责人
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:36
 */
@Getter @Setter @Table("wallet_activity_contact")
@org.dromara.autotable.annotation.TableIndex(name="uk_activity_contact", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"merchantId","storeId","recipientId"})
public class WalletActivityContactEntity extends BaseEntity<WalletActivityContactEntity> {
    private Long merchantId;
    private Long storeId;
    private Long recipientId;
}
