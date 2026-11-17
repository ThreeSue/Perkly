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
 private String detectionEntry;private String originType;private Long originId;private String originField;private String contentVersion;
 private String submittedByType;private Long submittedById;private String provenanceStatus;private Long duplicateOfId;
 private String detectionResult;
 @com.cool.core.annotation.ColumnDefine(type="json")
 @com.mybatisflex.annotation.Column(typeHandler=com.cool.core.mybatis.handler.Fastjson2TypeHandler.class)
 private ChatContext chatContext;
 @com.cool.core.annotation.ColumnDefine(type="json")
 @com.mybatisflex.annotation.Column(typeHandler=com.cool.core.mybatis.handler.Fastjson2TypeHandler.class)
 private CouponContext couponContext;
 public record CouponContext(Long merchantId,String merchantName,String couponName) {}
 public record ChatContext(Long chatId, Long storeId, String storeName, Long merchantId, String merchantName,
                           Long customerId, String customerName, String state, Long assigneeId, String assigneeName,
                           Integer round, Boolean humanRequested) {}
 @com.mybatisflex.annotation.Column(ignore=true) @org.dromara.autotable.annotation.Ignore
 private String subjectName;
 @com.mybatisflex.annotation.Column(ignore=true) @org.dromara.autotable.annotation.Ignore
 private String operatorName;
 @com.mybatisflex.annotation.Column(ignore=true) @org.dromara.autotable.annotation.Ignore
 private String originName;
 @com.mybatisflex.annotation.Column(ignore=true) @org.dromara.autotable.annotation.Ignore
 private String originMerchantName;

 String effectiveDetectionResult() {
  if(detectionResult!=null)return detectionResult;
  if("RULE_RESCAN".equals(detectionEntry)||"SCHEDULED_SCAN".equals(detectionEntry)||jobId!=null)return "SCAN_HIT";
  if("BLOCK".equals(mode))return "BLOCKED";
  if("REVIEW".equals(mode)&&java.util.Set.of("WRITE","SEARCH_INPUT","GENERATED_CONTENT").contains(java.util.Objects.toString(detectionEntry,"")))return "ALLOWED_RECORDED";
  return "UNKNOWN";
 }

 public String getAiSuggestedStatus() {
  return "PENDING".equals(status) && duplicateOfId==null ? ContentRiskModel.suggestedResolution(aiAdvice) : null;
 }
}
