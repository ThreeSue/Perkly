package com.cool.modules.ai.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.TableIndex;
import org.dromara.autotable.annotation.enums.IndexTypeEnum;
import java.util.Date;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录 AI 功能受理、模型调用和生成用量
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 23:20:03
 */
@Getter
@Setter
@Table("ai_usage_event")
@TableIndex(name = "idx_ai_usage_day", type = IndexTypeEnum.NORMAL, fields = {"dayKey", "feature"})
public class AiUsageEvent extends BaseEntity<AiUsageEvent> {
    private Date occurredAt;
    @ColumnDefine(length = 10)
    private String dayKey;
    @ColumnDefine(length = 40)
    private String feature;
    @ColumnDefine(length = 20)
    private String actorType;
    private Long actorId;
    @ColumnDefine(length = 255)
    private String actorName;
    @ColumnDefine(length = 100)
    private String accountName;
    @ColumnDefine(length = 100)
    private String modelName;
    private Integer requests;
    private Integer calls;
    private Integer failures;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer missingUsage;
    private Integer images;
}
