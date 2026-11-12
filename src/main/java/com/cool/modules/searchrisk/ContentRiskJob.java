package com.cool.modules.searchrisk;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 有界内容风险辅助复核任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("content_risk_job")
public class ContentRiskJob extends com.cool.core.base.BaseEntity<ContentRiskJob> {
 private String phase;private Boolean scheduled;
 private Long operatorId; private String source; private String status; private Long afterId; private Long cursorId; private Integer limitCount; private Integer modelBudget; private Integer checkedCount; private Integer modelCalls; private Long maxId; private String error; private java.util.Date startedAt; private java.util.Date finishedAt;
}

