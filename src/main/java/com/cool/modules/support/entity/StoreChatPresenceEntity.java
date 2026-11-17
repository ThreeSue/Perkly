package com.cool.modules.support.entity;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存并校验门店客服多设备在线租约
 * @Version: v1.0
 * @Date: 2026-11-13
 */
@lombok.Getter @lombok.Setter
@com.mybatisflex.annotation.Table("support_chat_presence")
public class StoreChatPresenceEntity extends com.cool.core.base.BaseEntity<StoreChatPresenceEntity> {
 private Long actorId;
 private Long storeId;
 private String sessionId;
 private Long expiresAt;
}
