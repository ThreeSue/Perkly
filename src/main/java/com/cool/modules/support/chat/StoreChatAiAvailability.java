package com.cool.modules.support.chat;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 只读检查门店AI接待开关模型与额度准入
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 16:00:00
 */
@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class StoreChatAiAvailability {
 private final StoreChatSettingsService settings;
 private final com.cool.modules.ai.service.AiFeatureControl control;
 private final com.cool.modules.ai.config.AiProperties properties;
 @org.springframework.beans.factory.annotation.Value("${perkly.support.ai-dispatch-enabled:true}") private boolean enabled;
 public String reason(Long store){
  if(!settings.read(store,false).aiEnabled())return "STORE_DISABLED";
  if(!control.policy("support").enabled())return "PLATFORM_DISABLED";
  if(!enabled || !properties.configured())return "MODEL_UNAVAILABLE";
  if(!control.hasCapacity("support",store))return "QUOTA_EXHAUSTED";
  return null;
 }
}
