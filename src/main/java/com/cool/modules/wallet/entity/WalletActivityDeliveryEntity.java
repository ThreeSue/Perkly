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
 * @Description: 按活动事件及收件账号持久化投递状态
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:39
 */
@Getter @Setter @Table("wallet_activity_delivery")
@org.dromara.autotable.annotation.TableIndex(name="uk_activity_delivery", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"eventId","recipientId"})
@org.dromara.autotable.annotation.TableIndex(name="idx_activity_delivery_due", fields={"status","nextAttemptAt"})
public class WalletActivityDeliveryEntity extends BaseEntity<WalletActivityDeliveryEntity> {
    private Long eventId;
    private Long recipientId;
    private Long merchantId;
    private Long storeId;
    private String status;
    private Long noticeId;
    private Integer attempts;
    private Date nextAttemptAt;
    @ColumnDefine(length=500) private String lastError;
}
