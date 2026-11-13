package com.cool.modules.profile;
import com.cool.modules.profile.mapper.*;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.ai.service.AiSettingsService;
import com.cool.modules.support.service.SupportAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证重复画像请求合并及模型失败不覆盖旧快照
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:45:00
 */
class ProfileServiceTest {
    ProfileSnapshotMapper snapshots=mock(ProfileSnapshotMapper.class);ProfileJobMapper jobs=mock(ProfileJobMapper.class);
    ProfileSourceReader source=mock(ProfileSourceReader.class);ProfileModel model=mock(ProfileModel.class);
    AiSettingsService settings=mock(AiSettingsService.class,RETURNS_DEEP_STUBS);
    SupportAccess access=mock(SupportAccess.class);
    com.cool.modules.ai.service.AiFeatureControl controls=mock(com.cool.modules.ai.service.AiFeatureControl.class);
    ProfileService service(){var tm=mock(PlatformTransactionManager.class);when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());var result=new ProfileService(snapshots,jobs,mock(ProfileHistoryMapper.class),mock(ProfileCorrectionMapper.class),source,model,new ObjectMapper().findAndRegisterModules(),new TransactionTemplate(tm),new AiProperties(),settings,access);org.springframework.test.util.ReflectionTestUtils.setField(result,"featureControl",controls);return result;}
    ProfileJobEntity job(){var j=new ProfileJobEntity();j.setCustomerId(1L);j.setState("PENDING");j.setAttempts(0);j.setRequestedVersion(1L);j.setRequestedAt(new Date());return j;}
    @Test void duplicatePendingRequestIsCoalesced(){var j=job();when(jobs.selectOneByQuery(any())).thenReturn(j);service().request(1L,true);verify(jobs,never()).update(any(ProfileJobEntity.class),anyBoolean());verifyNoInteractions(model,source);}
    @Test void modelFailureKeepsOldSnapshotAndRetriesAreBounded(){var j=job();j.setAttempts(2);when(jobs.selectOneByQuery(any())).thenReturn(j);when(controls.hasCapacity("profile",0L)).thenReturn(true);when(source.read(anyLong(),any())).thenThrow(new IllegalStateException("unavailable"));assertTrue(service().runOne());assertEquals("FAILED",j.getState());assertEquals(3,j.getAttempts());verifyNoInteractions(snapshots);}
    @Test void disabledAiNeverClaimsWork(){when(controls.hasCapacity("profile",0L)).thenReturn(false);assertFalse(service().runOne());verifyNoInteractions(jobs,source,model);}
}
