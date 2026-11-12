package com.cool.modules.searchrisk;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 搜索风险检测与运营复核
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:00:23
 */
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("search_risk_record")
public class SearchRisk extends com.cool.core.base.BaseEntity<SearchRisk> {
 private Long userId;
 @com.cool.core.annotation.ColumnDefine(type="text") private String content;
 @com.cool.core.annotation.ColumnDefine(type="text") private String matchedWords;
 @com.cool.core.annotation.ColumnDefine(type="text") private String matchedDetails;
 private String source; private String scene; private String subjectType; private Long subjectId;
 private String businessType; private Long businessId; private String contentHash;
 private String mode; private String status; private Long operatorId; private String reason; private java.util.Date handledAt;
 @com.cool.core.annotation.ColumnDefine(type="text") private String aiAdvice;
 private Long jobId; private String dedupKey;
 @com.mybatisflex.annotation.Column(ignore=true) @org.dromara.autotable.annotation.Ignore
 private String subjectName;
 @com.mybatisflex.annotation.Column(ignore=true) @org.dromara.autotable.annotation.Ignore
 private String operatorName;
}
