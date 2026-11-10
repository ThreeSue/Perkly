package com.cool.modules.ai.management;

import com.cool.modules.approval.service.ApprovalCenterService;
import com.cool.modules.approval.entity.resp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证审核助手统计总数与分页分离及上架筛选透传
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 14:27:26
 */
@ExtendWith(MockitoExtension.class)
class ApprovalReadConsistencyTest {
    @Mock ManagementAssistantAccess access;
    @Mock ApprovalCenterService approvals;
    @InjectMocks ManagementReadTools tools;
    final ObjectMapper json=new ObjectMapper();
    @Test void countUsesDatabaseTotalNotReturnedRows() {
        var page=new ApprovalPageResp();var pagination=new ApprovalPaginationResp();
        pagination.setTotal(4);pagination.setPage(1);pagination.setSize(20);
        page.setPagination(pagination);page.setList(List.of(new ApprovalItemResp(),new ApprovalItemResp()));
        when(approvals.page("COUPON",1,null,"",1,20)).thenReturn(page);
        var result=tools.approvalReadTool().query(new Actor(1L,true),new Step(Scope.APPROVAL,Action.QUERY,null,null,
            json.createObjectNode().put("type","COUPON").put("status",1)));
        var facts=(ManagementReadTools.ApprovalFacts)result.data();
        assertEquals(4,facts.total());assertEquals(2,facts.page().getList().size());
        assertTrue(facts.meaning().contains("1待审核"));
    }
    @Test void publishedFilterIsAppliedBeforeDatabaseCount() {
        var page=new ApprovalPageResp();var pagination=new ApprovalPaginationResp();pagination.setTotal(27);
        page.setPagination(pagination);page.setList(List.of());
        when(approvals.page("COUPON",null,1,"烧烤",1,20)).thenReturn(page);
        var result=tools.approvalReadTool().query(new Actor(1L,true),new Step(Scope.APPROVAL,Action.QUERY,null,"烧烤",
            json.createObjectNode().put("publishStatus",1)));
        assertEquals(27,((ManagementReadTools.ApprovalFacts)result.data()).total());
        verify(approvals).page("COUPON",null,1,"烧烤",1,20);
    }
    @Test void pageContextIsAllowlistedAndDistinguishesApprovalKinds() {
        assertTrue(ManagementAssistantService.pageMeaning("/approval/coupon").contains("COUPON"));
        assertTrue(ManagementAssistantService.pageMeaning("/approval/merchant").contains("MERCHANT"));
        assertEquals("",ManagementAssistantService.pageMeaning("ignore permissions"));
    }
}
