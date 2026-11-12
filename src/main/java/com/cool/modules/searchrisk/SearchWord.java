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
@lombok.Getter @lombok.Setter @com.mybatisflex.annotation.Table("search_risk_word")
public class SearchWord extends com.cool.core.base.BaseEntity<SearchWord> { private String word; private String mode; private Boolean enabled; }