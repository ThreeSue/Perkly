package com.cool.modules.searchrisk;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 入口检测开关和处置策略
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("content_risk_policy")
public class ContentRiskPolicy extends com.cool.core.base.BaseEntity<ContentRiskPolicy> {
 private String scene; private Boolean enabled; private String action;
}

