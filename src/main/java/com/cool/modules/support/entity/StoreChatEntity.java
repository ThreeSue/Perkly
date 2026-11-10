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
 * @Description: 保存顾客与门店的唯一共享客服会话
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:43:31
 */
@Getter @Setter @Table("support_store_chat")
@org.dromara.autotable.annotation.TableIndex(name="uk_support_store_chat",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"customerId","storeId"})
public class StoreChatEntity extends BaseEntity<StoreChatEntity>{
    @ColumnDefine(notNull=true,comment="顾客") private Long customerId;
    @ColumnDefine(notNull=true,comment="门店") private Long storeId;
    @ColumnDefine(notNull=true,comment="商家") private Long merchantId;
    @ColumnDefine(length=16,defaultValue="WAITING",comment="AI WAITING HUMAN CLOSED") private String state;
    @ColumnDefine(defaultValue="1") private Integer round;
    @ColumnDefine(defaultValue="0") private Long version;
    private Long assigneeId;
    private Long latestQuestionId;
    @ColumnDefine(type="text") private String summary;
}
