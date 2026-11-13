package com.cool.modules.support.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按账号身份保存门店聊天阅读进度
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 11:20:05
 */
@Getter @Setter @Table("support_chat_read")
@org.dromara.autotable.annotation.TableIndex(name="uk_chat_reader",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"chatId","readerKind","readerId"})
public class StoreChatReadEntity extends BaseEntity<StoreChatReadEntity> {
    @ColumnDefine(notNull=true) private Long chatId;
    @ColumnDefine(notNull=true,length=16) private String readerKind;
    @ColumnDefine(notNull=true) private Long readerId;
    @ColumnDefine(notNull=true,defaultValue="0") private Long lastReadMessageId;
}
