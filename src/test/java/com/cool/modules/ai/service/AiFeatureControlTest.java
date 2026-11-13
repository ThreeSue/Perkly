package com.cool.modules.ai.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.ai.entity.*;
import com.cool.modules.base.mapper.sys.BaseSysParamMapper;
import com.cool.modules.base.entity.sys.BaseSysParamEntity;
import com.cool.modules.support.service.SupportAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证AI功能关闭和额度用尽时不记录调用
 * @Version: v1.1
 * @Date: 2026-11-13
 * @Time: 15:00:00
 */
class AiFeatureControlTest {
 final BaseSysParamMapper params=mock(BaseSysParamMapper.class);
 final AiSettingsMapper settings=mock(AiSettingsMapper.class);
 final AiAuditMapper audits=mock(AiAuditMapper.class);
 final AiFeatureControl service=new AiFeatureControl(params,settings,audits,new ObjectMapper(),mock(SupportAccess.class));
 void policy(String data){var row=new BaseSysParamEntity();row.setData(data);when(params.selectOneByQuery(any(QueryWrapper.class))).thenReturn(row);when(settings.selectOneByQuery(any(QueryWrapper.class))).thenReturn(new AiSettingsEntity());}
 @Test void disabledDoesNotConsume(){policy("{\"id\":\"user\",\"enabled\":false,\"dailyLimit\":null}");assertThrows(com.cool.core.exception.CoolException.class,()->service.consume("user",1L));verifyNoInteractions(audits);}
 @Test void exhaustedDoesNotInsert(){policy("{\"id\":\"user\",\"enabled\":true,\"dailyLimit\":1}");when(audits.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);assertThrows(com.cool.core.exception.CoolException.class,()->service.consume("user",1L));verify(audits,never()).insert(any(AiAuditEntity.class));}
 @Test void unlimitedRecordsAdmission(){policy("{\"id\":\"user\",\"enabled\":true,\"dailyLimit\":null}");service.consume("user",1L);verify(audits).insert(any(AiAuditEntity.class));}
 @Test void unknownFeatureRejected(){assertThrows(com.cool.core.exception.CoolException.class,()->service.policy("other"));verifyNoInteractions(params);}
}
