package com.cool.modules.workspace;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.workspace.entity.*;
import com.cool.modules.workspace.mapper.*;
import com.cool.modules.workspace.model.WorkspaceModels.Context;
import com.cool.modules.workspace.service.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 复用通知接口与服务测试的模拟依赖夹具而不重复执行用例
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 02:14:32
 */
abstract class WorkspaceNoticeFixture {
    WorkspaceAuthorization auth=mock(WorkspaceAuthorization.class);
    BaseSysUserMapper users=mock(BaseSysUserMapper.class);
    WorkspaceNoticeMapper notices=mock(WorkspaceNoticeMapper.class);
    WorkspaceNoticeRecipientMapper recipients=mock(WorkspaceNoticeRecipientMapper.class);
    WorkspaceNoticeService service=new WorkspaceNoticeService(auth,users,notices,recipients);
    BaseSysUserEntity sender=new BaseSysUserEntity(), target=new BaseSysUserEntity();
    WorkspaceNoticeService.SendInput input=new WorkspaceNoticeService.SendInput("request_123","标题","正文",List.of(2L));
    @BeforeEach void setup(){
        org.springframework.test.util.ReflectionTestUtils.setField(service,"risk",mock(com.cool.modules.searchrisk.SearchRiskService.class));
        sender.setId(1L);sender.setMerchantId(10L);sender.setAccountType("MERCHANT");sender.setStatus(1);
        target.setId(2L);target.setMerchantId(10L);target.setAccountType("STORE");target.setStatus(1);
        when(users.selectOneById(1L)).thenReturn(sender);when(auth.user(2L)).thenReturn(target);
        when(auth.storeIds(sender)).thenReturn(List.of(20L));
        var role=new BaseSysRoleEntity();role.setLabel("ws_owner");when(auth.role(sender)).thenReturn(role);
        var staff=new BaseSysRoleEntity();staff.setLabel("ws_staff");when(auth.role(target)).thenReturn(staff);
        when(auth.context(2L)).thenReturn(new Context(2L,"staff","员工","STORE",10L,"商家",20L,"门店",3L,"店员","SELF",List.of(20L),null,false,List.of(),List.of(),List.of()));
        doAnswer(a->{((WorkspaceNoticeEntity)a.getArgument(0)).setId(99L);return 1;}).when(notices).insert(any(WorkspaceNoticeEntity.class));
    }
}
