package com.cool.modules.support.chat;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.StoreChatSettingsEntity;
import com.cool.modules.support.mapper.StoreChatSettingsMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证门店自动接管配置兼容旧客户端并保留权限与版本保护
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 15:49:21
 */
class StoreChatSettingsServiceTest {
 final StoreChatSettingsMapper rows=mock(StoreChatSettingsMapper.class);
 final MerchantStoreMapper stores=mock(MerchantStoreMapper.class);
 final StoreChatAccess access=mock(StoreChatAccess.class);
 final StoreChatSettingsService service=new StoreChatSettingsService(rows,stores,access,new ObjectMapper());
 StoreChatSettingsEntity existing(){
  var r=new StoreChatSettingsEntity();r.setStoreId(3L);r.setVersion(7);r.setAiEnabled(true);r.setAutoTakeoverEnabled(false);r.setFaqs("[]");
  when(access.staff(1L,"chat:manage")).thenReturn(3L);
  when(rows.selectOneByQuery(any(QueryWrapper.class))).thenReturn(r);return r;
 }
 @Test void oldClientMustNotReenableDisabledAutomaticTakeover(){
  var r=existing();var result=service.save(1L,new StoreChatModels.SettingsInput(7,true,List.of()));
  assertFalse(r.getAutoTakeoverEnabled());assertFalse(result.autoTakeoverEnabled());assertEquals(8,result.version());
 }
 @Test void explicitChoiceSaved(){
  existing();assertTrue(service.save(1L,new StoreChatModels.SettingsInput(7,true,List.of(),true)).autoTakeoverEnabled());
 }
 @Test void staleConfigCannotOverrideChoice(){
  existing();assertThrows(CoolException.class,()->service.save(1L,new StoreChatModels.SettingsInput(6,true,List.of(),true)));
  verify(rows,never()).update(any(StoreChatSettingsEntity.class));
 }
 @Test void unprivilegedCannotChangeSettings(){
  when(access.staff(1L,"chat:manage")).thenThrow(new CoolException("denied",403));
  assertThrows(CoolException.class,()->service.save(1L,new StoreChatModels.SettingsInput(0,true,List.of(),true)));
  verifyNoInteractions(rows,stores);
 }
 @Test void newStoreDefaultsToAutomaticTakeoverButAiIsStillOff(){
  var result=service.read(3L,false);assertTrue(result.autoTakeoverEnabled());assertFalse(result.aiEnabled());
 }
}
