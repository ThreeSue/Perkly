package com.cool.modules.workspace;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.workspace.entity.*;
import com.cool.modules.workspace.mapper.*;
import com.cool.modules.workspace.model.WorkspaceModels.Context;
import com.cool.modules.workspace.service.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证通知发送整批校验重试冲突与阅读账号隔离
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:25:43
 */
class WorkspaceNoticeServiceTest extends WorkspaceNoticeFixture {
    @Test void sendStoresOneReceiptPerDistinctAccount(){
        assertEquals(99L,service.send(1L,new WorkspaceNoticeService.SendInput("request_123","标题","正文",List.of(2L,2L))));
        verify(recipients,times(1)).insert(any(WorkspaceNoticeRecipientEntity.class));
    }
    @Test void illegalRecipientRejectsEntireBatch(){
        when(auth.user(3L)).thenThrow(new CoolException("非法账号",403));
        assertThrows(CoolException.class,()->service.send(1L,new WorkspaceNoticeService.SendInput("request_123","标题","正文",List.of(2L,3L))));
        verify(notices,never()).insert(any(WorkspaceNoticeEntity.class));verifyNoInteractions(recipients);
    }
    @Test void revokedPermissionStopsSendBeforeInsert(){
        doThrow(new CoolException("未授权",403)).when(auth).require(1L,"notice:send");
        assertThrows(CoolException.class,()->service.send(1L,input));verifyNoInteractions(notices,recipients);
    }
    @Test void retryReturnsExistingIdAndRejectsChangedContent(){
        var n=new WorkspaceNoticeEntity();n.setId(9L);n.setTitle("标题");n.setContent("正文");
        when(notices.selectOneByQuery(any(QueryWrapper.class))).thenReturn(n);
        var r=new WorkspaceNoticeRecipientEntity();r.setRecipientId(2L);
        when(recipients.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(r));
        assertEquals(9L,service.send(1L,input));
        assertThrows(CoolException.class,()->service.send(1L,new WorkspaceNoticeService.SendInput("request_123","变更","正文",List.of(2L))));
        verify(notices,never()).insert(any(WorkspaceNoticeEntity.class));
    }
    @Test void missingReceiptCannotReadOrMarkRead(){
        assertThrows(CoolException.class,()->service.detail(1L,9L));
        assertThrows(CoolException.class,()->service.read(1L,9L));verify(recipients,never()).update(any(WorkspaceNoticeRecipientEntity.class));
    }
    @Test void readIsIdempotentAndQueryContainsAccount(){
        var r=new WorkspaceNoticeRecipientEntity();r.setId(8L);r.setReadAt(new Date(1000));
        when(recipients.selectOneByQuery(any(QueryWrapper.class))).thenAnswer(a->{
            var sql=((QueryWrapper)a.getArgument(0)).toSQL();assertTrue(sql.contains("recipient_id"));assertTrue(sql.contains("notice_id"));return r;
        });
        service.read(1L,9L);assertEquals(1000,r.getReadAt().getTime());verify(recipients,never()).update(any(WorkspaceNoticeRecipientEntity.class));
    }
    @Test void otherSenderCannotViewReceipts(){var n=new WorkspaceNoticeEntity();n.setSenderId(2L);when(notices.selectOneById(9L)).thenReturn(n);assertThrows(CoolException.class,()->service.receipts(1L,9L));}
    @Test void invalidParametersDoNotAccessDatabase(){assertThrows(CoolException.class,()->service.send(1L,new WorkspaceNoticeService.SendInput("x","","",List.of())));verifyNoInteractions(users,notices,recipients);}
}
