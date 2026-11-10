package com.cool.modules.workspace.entity;

import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存通知接收账号及独立阅读状态
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 00:21:00
 */
@Getter @Setter @Table("workspace_notice_recipient")
@org.dromara.autotable.annotation.TableIndex(name="uk_workspace_notice_recipient_pair", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"noticeId","recipientId"})
public class WorkspaceNoticeRecipientEntity extends BaseEntity<WorkspaceNoticeRecipientEntity> {
    @ColumnDefine(comment="通知ID", notNull=true) private Long noticeId;
    @ColumnDefine(comment="接收账号ID", notNull=true) private Long recipientId;
    @ColumnDefine(comment="已读时间") private java.util.Date readAt;
    @ColumnDefine(comment="强提醒明确确认时间") private java.util.Date confirmedAt;
}
