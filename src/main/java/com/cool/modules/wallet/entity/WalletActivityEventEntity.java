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
 * @Description: 持久化活动事件与延时通知任务
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:37
 */
@Getter @Setter @Table("wallet_activity_event")
@org.dromara.autotable.annotation.TableIndex(name="uk_activity_event", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"activityId","activityVersion","kind"})
@org.dromara.autotable.annotation.TableIndex(name="idx_activity_event_due", fields={"status","nextAttemptAt"})
public class WalletActivityEventEntity extends BaseEntity<WalletActivityEventEntity> {
    private Long activityId;
    private Integer activityVersion;
    private String kind;
    private Date dueAt;
    private String status;
    private Integer attempts;
    private Date nextAttemptAt;
    @ColumnDefine(type="mediumtext") private String payload;
    @ColumnDefine(type="text") private String gaps;
    @ColumnDefine(length=500) private String lastError;
}
