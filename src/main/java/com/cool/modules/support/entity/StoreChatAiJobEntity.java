package com.cool.modules.support.entity;
import com.cool.core.base.BaseEntity;import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;import lombok.Getter;import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存客服AI回复后台任务与提交栅栏防止迟到回复
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:16:00
 */
@Getter @Setter @Table("support_chat_ai_job")
@org.dromara.autotable.annotation.TableIndex(name="uk_chat_ai_message",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"messageId"})
public class StoreChatAiJobEntity extends BaseEntity<StoreChatAiJobEntity>{
 @ColumnDefine(notNull=true) private Long chatId;
 @ColumnDefine(notNull=true) private Long messageId;
 @ColumnDefine(notNull=true) private Long chatVersion;
 @ColumnDefine(notNull=true) private Integer round;
 @org.dromara.autotable.annotation.Index @ColumnDefine(length=20,notNull=true) private String state;
 private Long leaseUntil;
}
