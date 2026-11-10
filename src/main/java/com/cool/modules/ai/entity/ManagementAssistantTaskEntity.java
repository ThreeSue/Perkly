package com.cool.modules.ai.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 持久保存管理助手确认任务及已完成步骤以避免重复执行
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@Getter @Setter @Table(value="ai_management_task",comment="管理助手确认任务")
public class ManagementAssistantTaskEntity extends BaseEntity<ManagementAssistantTaskEntity> {
    @org.dromara.autotable.annotation.Index(type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE)
    @ColumnDefine(length=64,notNull=true) private String token;
    @ColumnDefine(notNull=true) private Long actorId;
    @ColumnDefine(length=16,notNull=true) private String actorKind;
    @ColumnDefine(length=64,notNull=true) private String sessionId;
    @ColumnDefine(length=64,notNull=true) private String loginFingerprint;
    @ColumnDefine(length=20,notNull=true) private String state;
    @ColumnDefine(type="mediumtext",notNull=true) private String steps;
    @ColumnDefine(type="mediumtext",notNull=true) private String previews;
    @ColumnDefine(type="mediumtext",notNull=true) private String results;
    @ColumnDefine(notNull=true,defaultValue="0") private Integer completed;
    private Long expiresAt;
}
