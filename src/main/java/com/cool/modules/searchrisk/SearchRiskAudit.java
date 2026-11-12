package com.cool.modules.searchrisk;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保留词库及人工复核变更审计
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:30:00
 */
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("search_risk_audit")
public class SearchRiskAudit extends com.cool.core.base.BaseEntity<SearchRiskAudit> {
 private Long operatorId; private Long targetId; private String action;
 @com.cool.core.annotation.ColumnDefine(type="text") private String detail;
}
