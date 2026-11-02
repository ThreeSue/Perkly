package com.cool.modules.support.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存带发送人快照与幂等标识的会话消息
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:43:31
 */
@Getter @Setter @Table("support_chat_message")
@org.dromara.autotable.annotation.TableIndex(name="uk_support_chat_message",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"senderKind","senderId","requestId"})
public class StoreChatMessageEntity extends BaseEntity<StoreChatMessageEntity>{
    @org.dromara.autotable.annotation.Index
    @ColumnDefine(notNull=true,comment="会话") private Long chatId;
    @ColumnDefine(notNull=true,comment="APP或WORKSPACE") private String senderKind;
    @ColumnDefine(notNull=true,comment="发送人账号") private Long senderId;
    @ColumnDefine(notNull=true,comment="发送人显示名称快照") private String senderName;
    @ColumnDefine(length=64,notNull=true,comment="幂等请求标识") private String requestId;
    @ColumnDefine(type="text",comment="纯文字消息") private String content;
    @ColumnDefine(type="json",comment="图片地址")
    @Column(typeHandler=com.cool.core.mybatis.handler.Fastjson2TypeHandler.class) private List<String> images;
}
