package com.cool.modules.searchrisk;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 标记已保存业务字段的内容版本和实际提交者
 * @Version: v1.1
 * @Date: 2026-11-15
 * @Time: 03:45:00
 */
@Getter
@Setter
@Table("risk_content_author")
public class RiskContentAuthor {
    @Id
    private String fieldKey;
    private String contentVersion;
    private String authorType;
    private Long authorId;
}
