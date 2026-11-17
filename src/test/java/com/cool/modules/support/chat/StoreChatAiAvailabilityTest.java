package com.cool.modules.support.chat;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证接待准入检查原因及只读不消耗额度
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 16:10:00
 */
class StoreChatAiAvailabilityTest {
 @Test void switchesModelAndQuotaAreCheckedWithoutConsumption(){
  var settings=mock(StoreChatSettingsService.class);var control=mock(com.cool.modules.ai.service.AiFeatureControl.class);var properties=mock(com.cool.modules.ai.config.AiProperties.class);
  var service=new StoreChatAiAvailability(settings,control,properties);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"enabled",true);
  when(settings.read(3L,false)).thenReturn(new StoreChatModels.Settings(0,false,List.of()));assertEquals("STORE_DISABLED",service.reason(3L));
  when(settings.read(3L,false)).thenReturn(new StoreChatModels.Settings(0,true,List.of()));
  when(control.policy("support")).thenReturn(new com.cool.modules.ai.service.AiFeatureControl.Policy("support",false,null));assertEquals("PLATFORM_DISABLED",service.reason(3L));
  when(control.policy("support")).thenReturn(new com.cool.modules.ai.service.AiFeatureControl.Policy("support",true,null));assertEquals("MODEL_UNAVAILABLE",service.reason(3L));
  when(properties.configured()).thenReturn(true);assertEquals("QUOTA_EXHAUSTED",service.reason(3L));
  when(control.hasCapacity("support",3L)).thenReturn(true);assertNull(service.reason(3L));
  verify(control,never()).consume(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong());
 }
}
