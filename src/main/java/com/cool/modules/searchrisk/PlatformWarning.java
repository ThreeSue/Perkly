package com.cool.modules.searchrisk;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 平台风险警告及顾客独立回执
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("platform_warning")
public class PlatformWarning extends com.cool.core.base.BaseEntity<PlatformWarning> {
 private Long riskId; private Long senderId; private String requestId; private String subjectType; private Long subjectId; private String title; @com.cool.core.annotation.ColumnDefine(type="text") private String content; private String noticeType; private Long workspaceNoticeId; private java.util.Date readAt; private java.util.Date confirmedAt;
}

