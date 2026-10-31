package com.cool.modules.coupon.service;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.coupon.entity.ConsumptionOrderEntity;
import com.cool.modules.coupon.mapper.ConsumptionOrderMapper;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.merchant.service.MerchantStaffService;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.paginate.Page;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.cool.modules.coupon.service.CouponTradeRules.require;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 串联验券消费账单线下收款确认及单次核销并隔离门店数据
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 10:35:36
 */
@Service @RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ConsumptionOrderService {
    private final ConsumptionOrderMapper orders;
    private final CouponTradeService trade;
    private final MerchantAccountService accounts;
    private final MerchantStaffService staff;
    private final BaseSysUserMapper users;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.workspace.service.WorkspaceAuthorization workspaceAuthorization;
    public record PrepareInput(Long storeId, String code, Long consumptionAmount, String remark, String requestId) {}
    public record ConfirmInput(Long orderId, Integer version, Long receivedAmount) {}
    public record ReviseInput(Long orderId, Integer version, Long consumptionAmount, String remark) {}
    public record PageInput(Integer page, Integer size, Long merchantId, Long storeId, String status, String orderNo, Long customerCouponId, @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date startTime, @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date endTime) {
        public PageInput(Integer page,Integer size,Long merchantId,Long storeId,String status,String orderNo,Long customerCouponId){this(page,size,merchantId,storeId,status,orderNo,customerCouponId,null,null);}
    }
    public record OrderView(Long id, String orderNo, Long merchantId, String merchantName, Long storeId, String storeName,
        Long operatorId, String operatorName, Long customerCouponId, String couponName, String couponType, JSONObject couponSnapshot,
        String status, String statusText, Long consumptionAmount, Long discountAmount, Long payableAmount, Long receivedAmount,
        String paymentMode, String remark, Integer version, Long redemptionId, Date createdAt, Date completedAt, Long confirmedBy, String confirmedByName) {}
    private OrderView view(ConsumptionOrderEntity o) {
        return new OrderView(o.getId(),o.getOrderNo(),o.getMerchantId(),o.getMerchantName(),o.getStoreId(),o.getStoreName(),
            o.getOperatorId(),o.getOperatorName(),o.getCustomerCouponId(),o.getCouponName(),o.getCouponType(),JSONUtil.parseObj(o.getCouponSnapshot()),
            o.getStatus(),switch(o.getStatus()){case "PENDING" -> "待结算";case "COMPLETED" -> "已完成";default -> "已取消";},
            o.getConsumptionAmount(),o.getDiscountAmount(),o.getPayableAmount(),o.getReceivedAmount(),o.getPaymentMode(),o.getRemark(),
            o.getVersion(),o.getRedemptionId(),o.getCreatedAt(),o.getCompletedAt(),o.getConfirmedBy(),o.getConfirmedByName());
    }
    private String remark(String value) {
        String result=value==null ? "" : value.trim(); require(result.length()<=200,"备注不能超过200字"); return result;
    }
    private QueryWrapper scope(Long userId) {
        var user=accounts.current(userId);
        var q=QueryWrapper.create().eq(ConsumptionOrderEntity::getMerchantId,user.getMerchantId());
        Long store=staff.requireStore(user,null);
        if(store!=null) q.eq(ConsumptionOrderEntity::getStoreId,store);
        if("SELF".equals(user.getStoreScope()) || (user.getStoreScope()==null && "STAFF".equals(user.getMerchantRole()))) q.eq(ConsumptionOrderEntity::getOperatorId,userId);
        if("MEMBER".equals(user.getMerchantRole())) {
            var ids=workspaceAuthorization.storeIds(user);
            if(ids.isEmpty())q.eq(ConsumptionOrderEntity::getId,-1L);else q.in(ConsumptionOrderEntity::getStoreId,ids);
        }
        return q;
    }
    private ConsumptionOrderEntity own(Long userId, Long id, boolean lock) {
        require(id!=null && id>0,"请提供有效账单ID");
        var q=scope(userId).eq(ConsumptionOrderEntity::getId,id);
        if(lock) q.forUpdate();
        var o=orders.selectOneByQuery(q);
        if(o==null) throw new CoolException("账单不存在或无权访问",403);
        return o;
    }
    public OrderView prepare(Long userId, PrepareInput input) {
        return view(build(userId,input,true));
    }
    // 内容券在同一个事务内建单并核销；重试以requestId命中原账单。
    public OrderView redeemContent(Long userId, PrepareInput input) {
        var o=build(userId,input,false);
        return finish(userId,o,new ConfirmInput(o.getId(),o.getVersion(),0L));
    }
    private ConsumptionOrderEntity build(Long userId, PrepareInput p, boolean monetary) {
        var user=accounts.current(userId);
        Long storeId=staff.requireStore(user,p.storeId());
        require(storeId!=null && storeId>0,"请选择门店");
        require(p.code()!=null && p.code().matches("[a-f0-9]{64}"),"请输入有效券码");
        require(p.requestId()!=null && p.requestId().matches("[A-Za-z0-9_-]{8,64}"),"请提供8至64位唯一requestId");
        String note=remark(p.remark());
        // 同一操作人的并发重试串行检查，数据库唯一索引提供最终兜底。
        users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId,userId).forUpdate());
        var existing=orders.selectOneByQuery(QueryWrapper.create().eq(ConsumptionOrderEntity::getOperatorId,userId)
            .eq(ConsumptionOrderEntity::getRequestId,p.requestId()).forUpdate());
        if(existing!=null) {
            require(Objects.equals(existing.getStoreId(),storeId) && Objects.equals(existing.getCouponCode(),p.code())
                && Objects.equals(existing.getConsumptionAmount(),p.consumptionAmount()) && Objects.equals(existing.getRemark(),note)
                && monetary==isMonetary(existing.getCouponType()),"同一requestId不能用于不同账单内容，请查询原账单");
            return existing;
        }
        var inspection=trade.inspect(userId,storeId,p.code());
        require(Boolean.TRUE.equals(inspection.get("requiresConsumptionAmount"))==monetary,
            monetary ? "内容类券请使用确认使用接口自动开单" : "金额类券请填写账单后结算");
        if(!monetary) require(p.consumptionAmount()==null,"内容类券不接受手填消费金额");
        var preview=trade.preview(userId,storeId,p.code(),p.consumptionAmount());
        Long couponId=((Number)inspection.get("customerCouponId")).longValue();
        require(orders.selectCountByQuery(QueryWrapper.create().eq(ConsumptionOrderEntity::getActiveCouponId,couponId))==0,
            "该券已有待结算账单，请处理原账单或取消后重试");
        var o=new ConsumptionOrderEntity();
        o.setOrderNo("XF"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT));
        o.setMerchantId(user.getMerchantId());o.setStoreId(storeId);o.setMerchantName((String)inspection.get("merchantName"));
        o.setStoreName((String)inspection.get("storeName"));o.setOperatorId(userId);o.setOperatorName(staff.operatorName(userId));
        o.setRequestId(p.requestId());o.setCustomerCouponId(couponId);o.setCouponCode(p.code());o.setCouponName((String)inspection.get("couponName"));
        o.setCouponType((String)inspection.get("type"));o.setCouponSnapshot(JSONUtil.toJsonStr(inspection.get("rule")));
        o.setStatus("PENDING");o.setConsumptionAmount(p.consumptionAmount());o.setDiscountAmount(((Number)preview.get("discountAmount")).longValue());
        o.setPayableAmount(monetary ? p.consumptionAmount()-o.getDiscountAmount() : 0L);o.setReceivedAmount(0L);
        o.setRemark(note);o.setVersion(1);o.setActiveCouponId(couponId);o.setCreatedAt(new Date());
        try { orders.insert(o); }
        catch(org.springframework.dao.DuplicateKeyException conflict) {
            throw new CoolException("该券或请求已关联消费账单，请查询原账单后重试");
        }
        return o;
    }
    private boolean isMonetary(String type) { return Set.of("FULL_REDUCTION","DISCOUNT","CASH").contains(type); }
    private void pending(ConsumptionOrderEntity o, Integer version) {
        require("PENDING".equals(o.getStatus()),"账单已完成或取消");
        require(Objects.equals(o.getVersion(),version),"账单已更新，请刷新后重试");
    }
    public OrderView revise(Long userId, ReviseInput p) {
        var o=own(userId,p.orderId(),true); pending(o,p.version());
        require(isMonetary(o.getCouponType()),"内容类账单不能修改消费金额");
        var preview=trade.preview(userId,o.getStoreId(),o.getCouponCode(),p.consumptionAmount());
        o.setConsumptionAmount(p.consumptionAmount());o.setDiscountAmount(((Number)preview.get("discountAmount")).longValue());
        o.setPayableAmount(p.consumptionAmount()-o.getDiscountAmount());o.setRemark(remark(p.remark()));o.setVersion(o.getVersion()+1);
        orders.update(o); return view(o);
    }
    public OrderView confirm(Long userId, ConfirmInput p) {
        return finish(userId,own(userId,p.orderId(),true),p);
    }
    private OrderView finish(Long userId, ConsumptionOrderEntity o, ConfirmInput p) {
        if("COMPLETED".equals(o.getStatus())) {
            require(Objects.equals(o.getReceivedAmount(),p.receivedAmount()),"确认金额与已完成账单不一致"); return view(o);
        }
        pending(o,p.version());
        require(Objects.equals(p.receivedAmount(),o.getPayableAmount()),"请确认已收到与应补款相等的金额，单位为分");
        // 再次验证券状态、门店、门槛；核销与账单完成共用一个事务。
        var result=trade.redeemForOrder(userId,o.getStoreId(),o.getCouponCode(),o.getConsumptionAmount(),o.getId());
        require(Objects.equals(o.getDiscountAmount(),result.get("discountAmount")),"优惠试算已变化，请重新核对账单");
        o.setRedemptionId(((Number)result.get("redemptionId")).longValue());o.setStatus("COMPLETED");
        o.setPaymentMode(o.getPayableAmount()==0 ? "NONE" : "OFFLINE");o.setReceivedAmount(p.receivedAmount());
        o.setConfirmedBy(userId);o.setConfirmedByName(staff.operatorName(userId));
        o.setCompletedAt(new Date());o.setVersion(o.getVersion()+1);orders.update(o);return view(o);
    }
    public OrderView cancel(Long userId, Long id) {
        var o=own(userId,id,true);
        if("CANCELLED".equals(o.getStatus())) return view(o);
        require("PENDING".equals(o.getStatus()),"已完成账单不能取消");
        o.setStatus("CANCELLED");o.setActiveCouponId(null);o.setVersion(o.getVersion()+1);
        orders.update(o,false);return view(o);
    }
    @Transactional(readOnly=true) public OrderView info(Long userId,Long id) { return view(own(userId,id,false)); }
    @Transactional(readOnly=true) public OrderView adminInfo(Long id) {
        require(id!=null && id>0,"请提供有效账单ID");var o=orders.selectOneById(id);require(o!=null,"账单不存在");return view(o);
    }
    @Transactional(readOnly=true) public PageResult<OrderView> page(Long userId, PageInput p) {
        return find(scope(userId),p);
    }
    @Transactional(readOnly=true) public PageResult<OrderView> adminPage(PageInput p) {
        var q=QueryWrapper.create();
        if(p.merchantId()!=null) {require(p.merchantId()>0,"商家筛选无效");q.eq(ConsumptionOrderEntity::getMerchantId,p.merchantId());}
        return find(q,p);
    }
    private PageResult<OrderView> find(QueryWrapper q, PageInput p) {
        int page=p.page()==null?1:p.page(), size=p.size()==null?20:p.size();
        require(page>=1 && page<=100000 && size>=1 && size<=100,"分页参数不正确");
        if(p.startTime()!=null && p.endTime()!=null) require(!p.startTime().after(p.endTime()),"开始时间不能晚于结束时间");
        if(p.startTime()!=null)q.ge(ConsumptionOrderEntity::getCreatedAt,p.startTime());
        if(p.endTime()!=null)q.le(ConsumptionOrderEntity::getCreatedAt,p.endTime());
        if(p.storeId()!=null) {require(p.storeId()>0,"门店筛选无效");q.eq(ConsumptionOrderEntity::getStoreId,p.storeId());}
        if(p.customerCouponId()!=null) q.eq(ConsumptionOrderEntity::getCustomerCouponId,p.customerCouponId());
        if(p.status()!=null) {require(Set.of("PENDING","COMPLETED","CANCELLED").contains(p.status()),"账单状态无效");q.eq(ConsumptionOrderEntity::getStatus,p.status());}
        if(p.orderNo()!=null && !p.orderNo().isBlank()) {require(p.orderNo().length()<=40,"账单号过长");q.eq(ConsumptionOrderEntity::getOrderNo,p.orderNo());}
        var data=orders.paginate(new Page<ConsumptionOrderEntity>(page,size),q.orderBy(ConsumptionOrderEntity::getId,false));
        var result=new PageResult<OrderView>();result.setList(data.getRecords().stream().map(this::view).toList());
        result.getPagination().setPage(data.getPageNumber());result.getPagination().setSize(data.getPageSize());result.getPagination().setTotal(data.getTotalRow());return result;
    }
}
