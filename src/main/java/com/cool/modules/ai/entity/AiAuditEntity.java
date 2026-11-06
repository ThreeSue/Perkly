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
 * @Description: 记录AI控制台额度策略及任务补偿操作审计
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:36
 */
@Getter @Setter @Table("ai_audit")

public class AiAuditEntity extends BaseEntity<AiAuditEntity> {
    private Long actorId;
    private Long merchantId;
    private Long taskId;
    private String action;
    @ColumnDefine(type="text") private String detail;
}
