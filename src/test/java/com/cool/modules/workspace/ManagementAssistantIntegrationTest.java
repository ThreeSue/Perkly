package com.cool.modules.workspace;

import com.cool.modules.ai.management.*;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.*;
import java.util.*;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证管理助手分类写入幂等引用拒绝以及权限重校验的数据库集成
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
class ManagementAssistantIntegrationTest extends FeatureModulesIntegrationTest {
    @Autowired CategoryAssistantTool categoryTool;
    @Autowired StoreTypeAssistantTool storeTypeTool;
    @Autowired ManagementAssistantTasks tasks;
    @Autowired ManagementReadTools readTools;
    @Autowired ObjectMapper json;
    @Autowired javax.sql.DataSource source;
    @Autowired com.cool.modules.approval.service.ApprovalCenterService approvalCenter;
    @Autowired ManagementAssistantService assistantService;
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PERKLY_AI_CONSISTENCY_LIVE",matches="true")
    void liveModelMustMatchApprovalCountsAndRequeryDiscrepancy() {
        approvalCountsMatchPageAcrossStatusesAndMoreThanOnePage();
        var actor=new Actor(admin,true);
        for(int attempt=0;attempt<2;attempt++) {
            var reply=assistantService.chat(actor,"consistency-test",new Request(null,UUID.randomUUID().toString(),Scope.APPROVAL,
                "只看名称包含"+prefix+"的优惠券，还有多少待审核的？",null,"/approval/coupon"));
            assertApprovalTotal(reply,4,1,null);
            reply=assistantService.chat(actor,"consistency-test",new Request(reply.sessionId(),UUID.randomUUID().toString(),Scope.APPROVAL,
                "可是我页面看到有4条，请重新查一下，保持刚才关键词和待审核条件。",null,"/approval/coupon"));
            assertApprovalTotal(reply,4,1,null);
            reply=assistantService.chat(actor,"consistency-test",new Request(reply.sessionId(),UUID.randomUUID().toString(),Scope.APPROVAL,
                "保留刚才关键词，不限审批状态，有多少已上架的？",null,"/approval/coupon"));
            assertApprovalTotal(reply,1,null,1);
        }
    }
    private void assertApprovalTotal(Reply reply,long total,Integer status,Integer publishStatus) {
        assertFalse(reply.degraded());
        var facts=reply.results().stream().filter(r->r.data() instanceof ManagementReadTools.ApprovalFacts)
            .map(r->(ManagementReadTools.ApprovalFacts)r.data()).reduce((a,b)->b).orElseThrow();
        assertEquals(total,facts.total());assertEquals(status,facts.status());assertEquals(publishStatus,facts.publishStatus());
        assertEquals("COUPON",facts.type());
        assertTrue(reply.text().contains(Long.toString(total)) || reply.text().contains(total==4?"四":"一"),reply.text());
    }
    @Test void approvalCountsMatchPageAcrossStatusesAndMoreThanOnePage() {
        for(int i=0;i<27;i++) {
            var c=new com.cool.modules.coupon.entity.CouponTemplateEntity();
            c.setMerchantId(merchant);c.setName(prefix+"审核一致性"+i);c.setType("CASH");
            c.setDeductAmount(java.math.BigDecimal.TEN);c.setMinSpend(java.math.BigDecimal.ZERO);
            c.setSalePrice(java.math.BigDecimal.ZERO);c.setScopeType(0);c.setValidityType("AFTER_ISSUE");
            c.setValidDays(7);c.setPerCustomerLimit(1);c.setAllowCombine(0);c.setStatus(1);c.setRuleVersion(1);c.setIssuedCount(0L);
            coupons.insertSelective(c);
            var p=new com.cool.modules.coupon.entity.CouponPublicationEntity();
            p.setMerchantId(merchant);p.setCouponTemplateId(c.getId());p.setChannel("PLATFORM");
            p.setAuditStatus(i<4?1:2);p.setPublishStatus(i==4?1:0);p.setSubmittedVersion(1);p.setSubmittedAt(new Date());
            publications.insertSelective(p);
        }
        var actor=new Actor(admin,true);
        var all=(ManagementReadTools.ApprovalFacts)readTools.approvalReadTool().query(actor,
            new Step(Scope.APPROVAL,Action.QUERY,null,prefix,json.createObjectNode().put("type","COUPON"))).data();
        assertEquals(27,all.total());assertEquals(20,all.page().getList().size());
        var pending=(ManagementReadTools.ApprovalFacts)readTools.approvalReadTool().query(actor,
            new Step(Scope.APPROVAL,Action.QUERY,null,prefix,json.createObjectNode().put("status",1))).data();
        assertEquals(4,pending.total());
        assertEquals(approvalCenter.page("COUPON",1,prefix,1,20).getPagination().getTotal(),pending.total());
        var published=(ManagementReadTools.ApprovalFacts)readTools.approvalReadTool().query(actor,
            new Step(Scope.APPROVAL,Action.QUERY,null,prefix,json.createObjectNode().put("publishStatus",1))).data();
        assertEquals(1,published.total());assertEquals(1,published.page().getList().get(0).getPublishStatus());
        assertThrows(CoolException.class,()->approvalCenter.page("MERCHANT",null,1,prefix,1,20));
    }
    @BeforeEach void assistantPermissions() throws Exception {
        try(var c=source.getConnection()) {
            if(!c.getMetaData().getURL().contains("perkly_ai_regression_20261106"))
                throw new IllegalStateException("Requires dedicated AI regression database");
        }
        var u=principal(admin,UserTypeEnum.ADMIN,"merchant:category:page","merchant:category:info","merchant:category:add",
            "merchant:category:update","merchant:category:delete","merchant:storeCategory:page","merchant:storeCategory:info",
            "merchant:storeCategory:add","merchant:storeCategory:update","merchant:storeCategory:delete",
            "merchant:info:page","merchant:info:info","merchant:store:page","merchant:store:info",
            "coupon:purchaseOrder:page","coupon:consumptionOrder:page","workspace:administration:manage","approval:center:page","activity:manage","feedback:manage");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
    }
    @Test void confirmedCreationIsDurableAndReplayDoesNotCreateAgain() {
        Actor actor=new Actor(admin,true);
        Step step=new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode().put("name",prefix+"AI分类"));
        Result before=categoryTool.preview(actor,step);
        Preview task=tasks.create(actor,"session","fingerprint",List.of(step),List.of(before));
        var first=tasks.confirm(actor,"session","fingerprint",task.token());
        var second=tasks.confirm(actor,"session","fingerprint",task.token());
        assertEquals(json.valueToTree(first),json.valueToTree(second));
        assertEquals(1,categories.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create()
            .eq(MerchantCategoryEntity::getName,prefix+"AI分类")));
        assertThrows(CoolException.class,()->tasks.confirm(actor,"session","different-login",task.token()));
    }
    @Test void referencedTypeCannotBeDeletedAndPriorCompletedStepIsRetained() {
        Actor actor=new Actor(admin,true);
        Step create=new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode().put("name",prefix+"独立分类"));
        Step delete=new Step(Scope.STORE_TYPE,Action.DELETE,stores.selectOneById(store).getCategoryId(),null,null);
        Preview task=tasks.create(actor,"session","fingerprint",List.of(create,delete),
            List.of(categoryTool.preview(actor,create),storeTypeTool.preview(actor,delete)));
        assertThrows(CoolException.class,()->tasks.confirm(actor,"session","fingerprint",task.token()));
        assertEquals(1,tasks.progress(actor,"session","fingerprint",task.token()).size());
        assertThrows(CoolException.class,()->tasks.confirm(actor,"session","fingerprint",task.token()));
        assertEquals(1,categories.selectCountByQuery(com.mybatisflex.core.query.QueryWrapper.create()
            .eq(MerchantCategoryEntity::getName,prefix+"独立分类")));
        assertNotNull(storeTypes.selectOneById(delete.id()));
    }
    @Test void changedTargetAndLostPermissionBlockExecution() {
        Actor actor=new Actor(admin,true);
        Long id=merchants.selectOneById(merchant).getCategoryId();
        Step step=new Step(Scope.CATEGORY,Action.UPDATE,id,null,json.createObjectNode().put("name",prefix+"新名称"));
        Result preview=categoryTool.preview(actor,step);
        var row=categories.selectOneById(id);row.setSort(77);categories.update(row);
        assertThrows(CoolException.class,()->categoryTool.execute(actor,step,preview));
        var u=principal(admin,UserTypeEnum.ADMIN,"merchant:category:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
        assertThrows(CoolException.class,()->categoryTool.execute(actor,step,preview));
        assertEquals(prefix,categories.selectOneById(id).getName());
    }
    @Test void explicitlyClearingParentAndIconMatchesPreview() {
        Actor actor=new Actor(admin,true);
        Long parent=merchants.selectOneById(merchant).getCategoryId();
        Step create=new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode()
            .put("name",prefix+"子分类").put("parentId",parent).put("icon","/test-icon.png"));
        Result created=categoryTool.execute(actor,create,categoryTool.preview(actor,create));
        Long id=((CatalogAssistantTool.Change)created.data()).after().id();
        Step clear=new Step(Scope.CATEGORY,Action.UPDATE,id,null,json.createObjectNode().putNull("parentId").putNull("icon"));
        categoryTool.execute(actor,clear,categoryTool.preview(actor,clear));
        assertNull(categories.selectOneById(id).getParentId());
        assertNull(categories.selectOneById(id).getIcon());
    }
    @Test void readonlyScopesAndWholeRangeCountsUseBusinessAuthorization() {
        Result result=readTools.merchantReadTool().query(new Actor(admin,true),new Step(Scope.MERCHANT,Action.QUERY,null,prefix,
            json.createObjectNode().put("statistics",true)));
        var counts=(ManagementReadTools.Distribution)result.data();
        assertEquals(1,counts.total());assertEquals(1,counts.statuses().stream().mapToLong(ManagementReadTools.StatusCount::count).sum());
        assertThrows(CoolException.class,()->readTools.merchantReadTool().query(new Actor(owner,false),
            new Step(Scope.MERCHANT,Action.QUERY,Long.MAX_VALUE,null,null)));
        assertThrows(CoolException.class,()->categoryTool.preview(new Actor(owner,false),
            new Step(Scope.CATEGORY,Action.CREATE,null,null,json.createObjectNode().put("name","越权分类"))));
        assertThrows(CoolException.class,()->readTools.storeReadTool().preview(new Actor(admin,true),
            new Step(Scope.STORE,Action.DELETE,store,null,null)));
        var report=(com.cool.modules.merchant.report.MerchantReportModels.Report)readTools.redemptionReadTool().query(
            new Actor(staff,false),new Step(Scope.REDEMPTION,Action.QUERY,null,null,json.createObjectNode().put("period","TODAY"))).data();
        assertEquals(store,report.storeId());assertEquals(staff,report.operatorId());assertNull(report.summary().failedAttempts());
    }
    @Autowired com.cool.modules.coupon.mapper.CouponRedemptionMapper redemptionRows;
    @Test void platformRedemptionUsesRealCountsDatesAndNamesWithoutCredentials() throws Exception {
        var row=new com.cool.modules.coupon.entity.CouponRedemptionEntity();
        row.setMerchantId(merchant);row.setStoreId(store);row.setCustomerCouponId(999999L);row.setOperatorId(staff);row.setOperatorType("MERCHANT");
        row.setRedeemedAt(new Date());row.setConsumptionAmount(new java.math.BigDecimal("100.00"));row.setDiscountAmount(java.math.BigDecimal.TEN);
        redemptionRows.insert(row);
        var result=(ManagementReadTools.RedemptionPage)readTools.redemptionReadTool().query(new Actor(admin,true),
            new Step(Scope.REDEMPTION,Action.QUERY,null,null,json.createObjectNode().put("merchantId",merchant).put("period","TODAY"))).data();
        assertEquals(1,result.successfulRedemptions());assertEquals(prefix,result.list().get(0).merchantName());
        assertEquals(0,new java.math.BigDecimal("100").compareTo(result.list().get(0).consumptionYuan()));
        assertFalse(json.writeValueAsString(result).contains("customerCouponId"));
        assertThrows(CoolException.class,()->readTools.redemptionReadTool().query(new Actor(admin,true),
            new Step(Scope.REDEMPTION,Action.QUERY,null,null,json.createObjectNode().put("period","CUSTOM").put("startDate","2026-11-11").put("endDate","2026-11-01"))));
    }
    @Test void allReadModulesExposeResultsButCannotWrite() {
        var actor=new Actor(admin,true);
        for(var tool:List.of(readTools.orderReadTool(),readTools.consumptionReadTool(),readTools.staffReadTool(),readTools.approvalReadTool(),readTools.activityReadTool(),readTools.noticeReadTool())) {
            var result=tool.query(actor,new Step(tool.scope(),Action.QUERY,null,null,null));assertNotNull(result.data(),tool.scope().name());
            assertThrows(CoolException.class,()->tool.preview(actor,new Step(tool.scope(),Action.UPDATE,1L,null,json.createObjectNode().put("status",1))));
        }
    }
    @Test void approvalReviewPermissionAlsoAllowsReadingButUnrelatedPermissionDoesNot() {
        var u=principal(admin,UserTypeEnum.ADMIN,"approval:center:review");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
        assertNotNull(readTools.approvalReadTool().query(new Actor(admin,true),new Step(Scope.APPROVAL,Action.QUERY,null,null,null)).data());
        var denied=principal(admin,UserTypeEnum.ADMIN,"merchant:category:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(denied,null,denied.getAuthorities()));
        assertThrows(CoolException.class,()->readTools.approvalReadTool().query(new Actor(admin,true),new Step(Scope.APPROVAL,Action.QUERY,null,null,null)));
    }
    @Test void staffRoleAndGrantQueriesUseOriginalAuthorization() {
        var actor=new Actor(admin,true);
        assertNotNull(readTools.staffReadTool().query(actor,new Step(Scope.STAFF,Action.QUERY,null,null,json.createObjectNode().put("roles",true))).data());
        var grant=json.createObjectNode().put("grantSubjectType","MERCHANT").put("grantSubjectId",merchant);
        assertNotNull(readTools.staffReadTool().query(actor,new Step(Scope.STAFF,Action.QUERY,null,null,grant)).data());
        assertThrows(CoolException.class,()->readTools.staffReadTool().query(new Actor(staff,false),new Step(Scope.STAFF,Action.QUERY,null,null,grant)));
    }
    @Test void noticeQueryDoesNotReadConfirmOrExposeOtherReceipt() {
        var n=new com.cool.modules.workspace.entity.WorkspaceNoticeEntity();n.setSenderId(admin);n.setRequestId(prefix);n.setTitle("测试提醒");n.setContent("未读原文");n.setNoticeType("STRONG");noticeRows.insert(n);
        var recipient=new com.cool.modules.workspace.entity.WorkspaceNoticeRecipientEntity();recipient.setNoticeId(n.getId());recipient.setRecipientId(staff);noticeRecipients.insert(recipient);
        var result=readTools.noticeReadTool().query(new Actor(staff,false),new Step(Scope.NOTICE,Action.QUERY,n.getId(),null,null));
        assertNotNull(result.data());var stored=noticeRecipients.selectOneById(recipient.getId());assertNull(stored.getReadAt());assertNull(stored.getConfirmedAt());
        assertThrows(CoolException.class,()->readTools.noticeReadTool().query(new Actor(otherStaff,false),new Step(Scope.NOTICE,Action.QUERY,n.getId(),null,null)));
        assertThrows(CoolException.class,()->readTools.noticeReadTool().query(new Actor(staff,false),new Step(Scope.NOTICE,Action.QUERY,n.getId(),null,json.createObjectNode().put("receipts",true))));
    }
}
