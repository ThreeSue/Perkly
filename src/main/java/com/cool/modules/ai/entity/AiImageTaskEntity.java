package com.cool.modules.ai.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 持久化单张AI生成编辑任务幂等标识父图和素材保存状态
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:35
 */
@Getter @Setter @Table("ai_image_task")
@org.dromara.autotable.annotation.TableIndex(name="uk_ai_image_task",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"merchantId","actorKind","actorId","requestId"})
public class AiImageTaskEntity extends BaseEntity<AiImageTaskEntity> {
    private Long merchantId;
    private Long actorId;
    private String actorKind;
    private String requestId;
    private String requestHash;
    private Long couponId;
    private Long parentTaskId;
    private Long parentMaterialId;
    private String mode;
    private String model;
    private String status;
    private String quotaDay;
    @ColumnDefine(type="text") private String prompt;
    @ColumnDefine(type="mediumtext") private String draftSnapshot;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ColumnDefine(type="mediumtext") private String providerResult;
    private String providerRequestId;
    private Long materialId;
    @ColumnDefine(length=64) private String imageKey;
    private Integer imageCount;
    private Long durationMs;
    @ColumnDefine(length=12,decimalLength=6) private java.math.BigDecimal estimatedCost;
    @ColumnDefine(length=500) private String errorMessage;
    private Date startedAt;
    private Date completedAt;
    private Date nextAttemptAt;
}
