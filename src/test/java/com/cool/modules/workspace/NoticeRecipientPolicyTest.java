package com.cool.modules.workspace;

import com.cool.modules.workspace.service.NoticeRecipientPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证通知收件范围的商家门店及普通店员限制
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 00:19:10
 */
class NoticeRecipientPolicyTest {
    @Test void merchantCannotReachAnotherMerchantOrUnassignedStore() {
        var s = new NoticeRecipientPolicy.Sender(1L,"MERCHANT",10L,null,Set.of(20L),true,false);
        assertTrue(NoticeRecipientPolicy.canSend(s,new NoticeRecipientPolicy.Recipient(2L,"STORE",10L,20L,true,true)));
        assertFalse(NoticeRecipientPolicy.canSend(s,new NoticeRecipientPolicy.Recipient(3L,"STORE",11L,20L,true,true)));
        assertFalse(NoticeRecipientPolicy.canSend(s,new NoticeRecipientPolicy.Recipient(4L,"STORE",10L,21L,true,true)));
    }
    @Test void storeStaffCannotSendAndManagerCannotReachAnotherStore() {
        var employee = new NoticeRecipientPolicy.Recipient(2L,"STORE",10L,20L,true,true);
        assertFalse(NoticeRecipientPolicy.canSend(new NoticeRecipientPolicy.Sender(1L,"STORE",10L,20L,Set.of(),true,true),employee));
        var manager = new NoticeRecipientPolicy.Sender(1L,"STORE",10L,20L,Set.of(),true,false);
        assertTrue(NoticeRecipientPolicy.canSend(manager,employee));
        assertFalse(NoticeRecipientPolicy.canSend(manager,new NoticeRecipientPolicy.Recipient(5L,"STORE",10L,20L,true,false)));
        assertFalse(NoticeRecipientPolicy.canSend(manager,new NoticeRecipientPolicy.Recipient(3L,"STORE",10L,21L,true,true)));
        assertFalse(NoticeRecipientPolicy.canSend(manager,new NoticeRecipientPolicy.Recipient(4L,"STORE",10L,20L,false,true)));
    }
    @Test void platformCanReachBusinessButCannotSendToSelfOrPlatform() {
        var platform = new NoticeRecipientPolicy.Sender(1L,"PLATFORM",null,null,Set.of(),true,false);
        assertTrue(NoticeRecipientPolicy.canSend(platform,new NoticeRecipientPolicy.Recipient(2L,"MERCHANT",10L,null,true,false)));
        assertTrue(NoticeRecipientPolicy.canSend(platform,new NoticeRecipientPolicy.Recipient(3L,"STORE",10L,20L,true,true)));
        assertFalse(NoticeRecipientPolicy.canSend(platform,new NoticeRecipientPolicy.Recipient(1L,"PLATFORM",null,null,true,false)));
        assertFalse(NoticeRecipientPolicy.canSend(platform,new NoticeRecipientPolicy.Recipient(4L,"PLATFORM",null,null,true,false)));
    }
}
