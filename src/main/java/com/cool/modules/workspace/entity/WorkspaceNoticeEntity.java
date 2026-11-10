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
 * @Description: 保存站内通知内容与发送人
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 00:21:00
 */
@Getter @Setter @Table("workspace_notice")
@org.dromara.autotable.annotation.TableIndex(name="uk_workspace_notice_pair", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"senderId","requestId"})
public class WorkspaceNoticeEntity extends BaseEntity<WorkspaceNoticeEntity> {
    @ColumnDefine(comment="发送人账号ID", notNull=true) private Long senderId;
    @ColumnDefine(comment="请求幂等标识", length=64, notNull=true) private String requestId;
    @ColumnDefine(comment="通知标题", length=100, notNull=true) private String title;
    @ColumnDefine(comment="通知正文", type="text", notNull=true) private String content;
    @ColumnDefine(comment="通知类型 LIGHT轻通知 STRONG强提醒", length=16, defaultValue="LIGHT") private String noticeType;
    @ColumnDefine(comment="业务来源") private String businessType;
    @ColumnDefine(comment="业务ID") private Long businessId;
    @ColumnDefine(comment="业务版本") private Integer businessVersion;
    @ColumnDefine(comment="站内详情入口") private String detailPath;
    @ColumnDefine(comment="站内操作入口") private String actionPath;
}
