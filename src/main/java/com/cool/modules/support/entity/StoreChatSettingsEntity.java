package com.cool.modules.support.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 持久保存门店AI接待开关FAQ和配置版本
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:16:00
 */
@Getter @Setter @Table("support_chat_settings")
@org.dromara.autotable.annotation.TableIndex(name="uk_chat_settings_store",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"storeId"})
public class StoreChatSettingsEntity extends BaseEntity<StoreChatSettingsEntity>{
 @ColumnDefine(notNull=true) private Long storeId;
 @ColumnDefine(notNull=true,defaultValue="false") private Boolean aiEnabled;
 @ColumnDefine(notNull=true,defaultValue="0") private Integer version;
 @ColumnDefine(type="text") private String faqs;
}
