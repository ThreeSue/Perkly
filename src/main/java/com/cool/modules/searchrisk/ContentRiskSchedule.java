package com.cool.modules.searchrisk;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 可审计的内容风险词库与增量巡检
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 16:24:39
 */

@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("content_risk_schedule")
public class ContentRiskSchedule extends com.cool.core.base.BaseEntity<ContentRiskSchedule> {
 private Boolean enabled;private Integer intervalMinutes;private Integer batchSize;private Integer dailyBudget;
 @com.cool.core.annotation.ColumnDefine(type="text") private String sourcesJson;
 @com.cool.core.annotation.ColumnDefine(type="text") private String cursorsJson;
 private Integer sourceIndex;
 private String budgetDate;private Integer usedCalls;private Long activeJobId;private Long lastJobId;
 private Integer retryCount;private java.util.Date lastRunAt;private java.util.Date nextRunAt;private String lastResult;
}

