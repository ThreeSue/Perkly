package com.cool.modules.ai.management;

import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.service.*;
import com.cool.modules.coupon.service.*;
import com.cool.modules.coupon.controller.admin.req.PurchaseOrderQuery;
import com.cool.modules.workspace.service.*;
import com.cool.modules.workspace.model.WorkspaceModels.Filter;
import com.cool.modules.wallet.service.WalletActivityService;
import com.cool.modules.approval.service.ApprovalCenterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.BiFunction;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 将既有只读业务服务注册为有限管理助手工具
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@Configuration @RequiredArgsConstructor
public class ManagementReadTools {
    private final ManagementAssistantAccess access;
    private final MerchantWorkspaceService workspace;
    private final WorkspaceAuthorization authorization;
    private final MerchantInfoService merchants;
    private final MerchantStoreService stores;
    private final ConsumptionOrderService consumption;
    private final PurchaseOrderReadService purchases;
    private final CouponTradeService trade;
    private final WorkspaceManagementService management;
    private final WorkspaceNoticeService notices;
    private final WalletActivityService activities;
    private final ApprovalCenterService approvals;
    private final com.cool.modules.merchant.report.MerchantReportService reports;
    private final ObjectMapper json;
    private final com.cool.modules.coupon.mapper.CouponRedemptionMapper redemptionRows;
    private final com.cool.modules.coupon.mapper.ConsumptionOrderMapper consumptionRows;
    private final com.cool.modules.base.mapper.sys.BaseSysUserMapper users;
    public record RedemptionRow(Long id,String merchantName,String storeName,String couponName,String orderNo,
        String operatorName,java.util.Date redeemedAt,java.math.BigDecimal consumptionYuan,java.math.BigDecimal discountYuan) {}
    public record RedemptionPage(Object range,long successfulRedemptions,List<RedemptionRow> list,int page,int size,String note) {}
    public record StatusCount(String name,long count) {}
    public record Distribution(long total,List<StatusCount> statuses,String note) {}
    private ManagementAssistantTool tool(Scope scope,BiFunction<Actor,Step,Object> query) {
        return new ManagementAssistantTool() {
            public Scope scope(){return scope;}
            public Result query(Actor actor,Step step) {
                if(step.action()!=Action.QUERY)throw new CoolException("此模块仅支持查询",403);
                access.identity(actor);
                if(step.id()!=null && step.id()<=0)throw new CoolException("目标ID无效",400);
                try{return new Result(scope,"READ",scope==Scope.STAFF?"完整授权范围结果，无分页；岗位及授权条目的名称来自原权限目录":"真实授权范围数据；分页结果不能当全量统计，金额单位按原接口",query.apply(actor,step));}
                catch(IllegalArgumentException invalid){throw new CoolException("查询字段或日期格式无效，请调整条件",400);}
            }
        };
    }
    @Bean public ManagementAssistantTool merchantReadTool() {return tool(Scope.MERCHANT,(a,s)->{
        fields(s,Set.of("status","categoryId","statistics"));access.require(a,"merchant:info:"+(s.id()==null?"page":"info"),"profile:view");
        if(!a.platform()) {
            fields(s,Set.of());
            if(s.id()!=null && !s.id().equals(authorization.context(a.id()).merchantId()))throw new CoolException("不能查询其他商家",403);
            if(s.keyword()!=null)throw new CoolException("商家身份仅可读取本商家资料，不能按其他商家名称筛选",400);
            return workspace.profile(a.id());
        }
        if(s.id()!=null)return merchants.info(s.id());
        var q=QueryWrapper.create();if(s.keyword()!=null)q.like(MerchantInfoEntity::getName,s.keyword());
        if(field(s,"status")!=null)q.eq(MerchantInfoEntity::getStatus,field(s,"status").asInt());
        if(field(s,"categoryId")!=null)q.eq(MerchantInfoEntity::getCategoryId,longField(s,"categoryId"));
        if(field(s,"statistics")!=null && field(s,"statistics").asBoolean()) {
            long total=merchants.count(q);
            return new Distribution(total,List.of(new StatusCount("停用",merchants.count(q.clone().eq(MerchantInfoEntity::getStatus,0))),
                new StatusCount("启用",merchants.count(q.clone().eq(MerchantInfoEntity::getStatus,1)))),"按当前名称、分类及状态条件统计数据库全量；非订单收入统计");
        }
        return merchants.page(new JSONObject(),new Page<MerchantInfoEntity>(1,20),q.orderBy(MerchantInfoEntity::getId,false));
    });}
    @Bean public ManagementAssistantTool storeReadTool() {return tool(Scope.STORE,(a,s)->{
        fields(s,Set.of("merchantId","status","categoryId","statistics"));access.require(a,"merchant:store:"+(s.id()==null?"page":"info"),"store:view");
        if(!a.platform()){fields(s,Set.of());return s.id()==null?workspace.storePage(a.id(),1,20,s.keyword()):workspace.storeInfo(a.id(),s.id());}
        if(s.id()!=null)return stores.info(s.id());
        var q=QueryWrapper.create();if(s.keyword()!=null)q.like(MerchantStoreEntity::getName,s.keyword());
        if(field(s,"merchantId")!=null)q.eq(MerchantStoreEntity::getMerchantId,longField(s,"merchantId"));
        if(field(s,"categoryId")!=null)q.eq(MerchantStoreEntity::getCategoryId,longField(s,"categoryId"));
        if(field(s,"status")!=null)q.eq(MerchantStoreEntity::getStatus,field(s,"status").asInt());
        if(field(s,"statistics")!=null && field(s,"statistics").asBoolean()) {
            return new Distribution(stores.count(q),List.of(new StatusCount("停用",stores.count(q.clone().eq(MerchantStoreEntity::getStatus,0))),
                new StatusCount("营业",stores.count(q.clone().eq(MerchantStoreEntity::getStatus,1))),
                new StatusCount("休息",stores.count(q.clone().eq(MerchantStoreEntity::getStatus,2)))),"按当前名称、商家、类型及状态条件统计数据库全量");
        }
        return stores.page(new JSONObject(),new Page<MerchantStoreEntity>(1,20),q.orderBy(MerchantStoreEntity::getId,false));
    });}
    @Bean public ManagementAssistantTool orderReadTool() {return tool(Scope.ORDER,(a,s)->{
        access.require(a,"coupon:purchaseOrder:"+(s.id()==null?"page":"info"),"purchase:view");
        if(!a.platform()) {
            fields(s,Set.of());noKeyword(s);
            return s.id()==null?workspace.orderPage(a.id(),1,20):workspace.orderInfo(a.id(),s.id());
        }
        fields(s,Set.of("merchantId","status","orderNo","startTime","endTime"));noKeyword(s);
        if(s.id()!=null)return purchases.info(s.id());
        PurchaseOrderQuery input=convert(s,PurchaseOrderQuery.class);input.setPage(1);input.setSize(20);return purchases.page(input);
    });}
    @Bean public ManagementAssistantTool consumptionReadTool() {return tool(Scope.CONSUMPTION,(a,s)->{
        access.require(a,"coupon:consumptionOrder:"+(s.id()==null?"page":"info"),"bill:view");
        fields(s,Set.of("merchantId","storeId","status","orderNo","customerCouponId","startTime","endTime"));noKeyword(s);
        if(s.id()!=null)return a.platform()?consumption.adminInfo(s.id()):consumption.info(a.id(),s.id());
        var f=convert(s,ConsumptionOrderService.PageInput.class);
        var input=new ConsumptionOrderService.PageInput(1,20,f.merchantId(),f.storeId(),f.status(),f.orderNo(),f.customerCouponId(),f.startTime(),f.endTime());
        return a.platform()?consumption.adminPage(input):consumption.page(a.id(),input);
    });}
    @Bean public ManagementAssistantTool redemptionReadTool() {return tool(Scope.REDEMPTION,(a,s)->{
        if(a.platform()) {
            access.require(a,"coupon:consumptionOrder:page",null);
            fields(s,Set.of("merchantId","storeId","period","startDate","endDate"));noKeyword(s);
            if(s.id()!=null)throw new CoolException("请按商家、门店与日期筛选核销流水",400);
            var dateFields=s.fields()==null?json.createObjectNode():(com.fasterxml.jackson.databind.node.ObjectNode)s.fields().deepCopy();
            dateFields.remove(List.of("merchantId","storeId"));
            var f=json.convertValue(dateFields,com.cool.modules.merchant.report.MerchantReportModels.Filter.class);
            var zone=java.time.ZoneId.of("Asia/Shanghai");
            var range=com.cool.modules.merchant.report.MerchantReportService.range(f,java.time.LocalDate.now(zone));
            var q=QueryWrapper.create().ge(com.cool.modules.coupon.entity.CouponRedemptionEntity::getRedeemedAt,Date.from(range.startDate().atStartOfDay(zone).toInstant()))
                .lt(com.cool.modules.coupon.entity.CouponRedemptionEntity::getRedeemedAt,Date.from(range.endDate().plusDays(1).atStartOfDay(zone).toInstant()));
            if(longField(s,"merchantId")!=null)q.eq(com.cool.modules.coupon.entity.CouponRedemptionEntity::getMerchantId,longField(s,"merchantId"));
            if(longField(s,"storeId")!=null)q.eq(com.cool.modules.coupon.entity.CouponRedemptionEntity::getStoreId,longField(s,"storeId"));
            long total=redemptionRows.selectCountByQuery(q.clone());
            var list=redemptionRows.selectListByQuery(q.orderBy(com.cool.modules.coupon.entity.CouponRedemptionEntity::getId,false).limit(20)).stream().map(r->{
                var order=r.getConsumptionOrderId()==null?null:consumptionRows.selectOneById(r.getConsumptionOrderId());
                var merchant=merchants.getOne(QueryWrapper.create().eq(MerchantInfoEntity::getId,r.getMerchantId()));var store=stores.getOne(QueryWrapper.create().eq(MerchantStoreEntity::getId,r.getStoreId()));
                var operator=r.getOperatorId()==null?null:users.selectOneById(r.getOperatorId());
                return new RedemptionRow(r.getId(),order!=null?order.getMerchantName():merchant==null?"历史商家":merchant.getName(),
                    order!=null?order.getStoreName():store==null?"历史门店":store.getName(),order==null?"历史优惠券":order.getCouponName(),
                    order==null?null:order.getOrderNo(),operator==null?"历史人员":operator.getName(),r.getRedeemedAt(),r.getConsumptionAmount(),r.getDiscountAmount());
            }).toList();
            return new RedemptionPage(range,total,list,1,20,"成功次数为全部筛选范围；明细最多20条，金额单位元。多张券可能属于同一笔消费，不能累加明细作为收入。失败数、利润不提供。");
        }
        if(!a.platform() && "STORE".equals(authorization.context(a.id()).accountType())) {
            fields(s,Set.of("period","startDate","endDate","operatorId","orderNo","couponName"));noKeyword(s);
            if(s.id()!=null)throw new CoolException("请选择报表日期或订单号，不能按记录ID查询报表",400);
            var f=convert(s,com.cool.modules.merchant.report.MerchantReportModels.Filter.class);
            return reports.query(a.id(),new com.cool.modules.merchant.report.MerchantReportModels.Filter(
                f.period(),f.startDate(),f.endDate(),f.operatorId(),f.orderNo(),f.couponName(),1,20));
        }
        fields(s,Set.of("storeId","scope"));noKeyword(s);
        access.require(a,"coupon:template:page","bill:view");
        Long storeId=field(s,"storeId")==null?null:field(s,"storeId").asLong();
        String range=field(s,"scope")==null?"self":field(s,"scope").asText();
        if(s.id()!=null)throw new CoolException("核销记录暂仅提供授权列表，不能按此ID取详情",400);
        return trade.redemptions(a.id(),1,20,storeId,range);
    });}
    @Bean public ManagementAssistantTool staffReadTool() {return tool(Scope.STAFF,(a,s)->{
        fields(s,Set.of("merchantId","storeId","accountType","roleId","roles","grantSubjectType","grantSubjectId"));
        if(s.id()!=null)throw new CoolException("请按员工姓名或岗位筛选，当前不支持员工详情ID",400);
        if(field(s,"grantSubjectType")!=null || field(s,"grantSubjectId")!=null) {
            fields(s,Set.of("grantSubjectType","grantSubjectId"));noKeyword(s);
            String type=textField(s,"grantSubjectType");Long id=longField(s,"grantSubjectId");
            if(type==null || !Set.of("MERCHANT","STORE","USER").contains(type) || id==null || id<=0)throw new CoolException("请指定要查看授权的商家、门店或账号",400);
            var grant=management.grant(a.platform()?null:a.id(),type,id);
            return grant.availableMenus().stream().filter(m->grant.menuIds().contains(m.getId())).map(m->new PermissionName(m.getName(),m.getPerms())).toList();
        }
        var f=convertFilter(s);
        if(field(s,"roles")!=null && field(s,"roles").asBoolean()) {
            var roles=management.roleList(a.platform()?null:a.id(),f);var catalog=authorization.catalog();
            return roles.stream().map(r->new RolePermissions(r,catalog.stream().filter(m->r.menuIds().contains(m.getId())).map(m->new PermissionName(m.getName(),m.getPerms())).toList())).toList();
        }
        return management.accounts(a.platform()?null:a.id(),f);
    });}
    public record PermissionName(String name,String permission) {}
    public record RolePermissions(com.cool.modules.workspace.model.WorkspaceModels.RoleView role,List<PermissionName> permissions) {}
    @Bean public ManagementAssistantTool approvalReadTool() {return tool(Scope.APPROVAL,(a,s)->{
        fields(s,Set.of("type","status","publishStatus"));
        if(!a.platform()) {
            access.approvalRead(a);
            if(s.id()==null)throw new CoolException("请提供本商家的优惠券目标以查询审批详情",400);
            return workspace.couponApproval(a.id(),s.id());
        }
        access.approvalRead(a);
        String type=field(s,"type")==null?"COUPON":field(s,"type").asText();
        if(s.id()!=null){fields(s,Set.of("type"));return approvals.info(type,s.id());}
        Integer status=integerField(s,"status");
        Integer publishStatus=integerField(s,"publishStatus");
        var page=approvals.page(type,status,publishStatus,Objects.toString(s.keyword(),""),1,20);
        return new ApprovalFacts(type,status,publishStatus,Objects.toString(s.keyword(),""),
            page.getPagination().getTotal(),page,
            "total为当前全部筛选条件下的数据库总数，不是本页条数。审批状态：0已撤回/未提交，1待审核，2已通过，3已驳回；上架状态：0未上架，1已上架，2已下架。审核通过不等于已上架。");
    });}
    public record ApprovalFacts(String type,Integer status,Integer publishStatus,String keyword,long total,
        com.cool.modules.approval.entity.resp.ApprovalPageResp page,String meaning) {}
    @Bean public ManagementAssistantTool activityReadTool() {return tool(Scope.ACTIVITY,(a,s)->{
        fields(s,Set.of());noKeyword(s);
        if(s.id()!=null) {
            if(a.platform())throw new CoolException("运营活动详情请从返回的活动列表选择查看",400);
            return activities.detail(a.id(),s.id());
        }
        return activities.page(a.id(),a.platform(),1,20);
    });}
    @Bean public ManagementAssistantTool noticeReadTool() {return tool(Scope.NOTICE,(a,s)->{
        fields(s,Set.of("sent","receipts","unread"));noKeyword(s);
        if(field(s,"unread")!=null && field(s,"unread").asBoolean())return notices.unread(a.id());
        if(field(s,"receipts")!=null && field(s,"receipts").asBoolean()) {
            if(s.id()==null)throw new CoolException("请选择本人发送的通知",400);return notices.receipts(a.id(),s.id());
        }
        if(s.id()!=null)return notices.detail(a.id(),s.id());
        return notices.page(a.id(),field(s,"sent")!=null && field(s,"sent").asBoolean(),1,20);
    });}
    private Filter convertFilter(Step s) {
        return new Filter(longField(s,"merchantId"),longField(s,"storeId"),textField(s,"accountType"),s.keyword(),1,20,null,longField(s,"roleId"));
    }
    private Long longField(Step s,String key){return field(s,key)==null?null:field(s,key).asLong();}
    private Integer integerField(Step s,String key){
        var value=field(s,key);if(value==null)return null;
        if(!value.isIntegralNumber() || !value.canConvertToInt())throw new CoolException("状态必须使用工具目录规定的整数值",400);
        return value.intValue();
    }
    private String textField(Step s,String key){return field(s,key)==null?null:field(s,key).asText();}
    private JsonNode field(Step s,String key){return s.fields()==null || !s.fields().hasNonNull(key)?null:s.fields().get(key);}
    private <T> T convert(Step s,Class<T> type){return json.convertValue(s.fields()==null?json.createObjectNode():s.fields(),type);}
    private void noKeyword(Step s){if(s.keyword()!=null && !s.keyword().isBlank())throw new CoolException("该工具不支持关键词筛选，请指定支持的目标或条件",400);}
    private void fields(Step s,Set<String> allowed) {
        if(s.fields()==null)return;
        if(!s.fields().isObject())throw new CoolException("查询条件必须为对象",400);
        s.fields().fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new CoolException("暂不支持此筛选："+k,400);});
    }
}
