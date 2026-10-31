package com.cool.modules.coupon.service.impl;
import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.coupon.controller.admin.req.PurchaseOrderQuery;
import com.cool.modules.coupon.controller.admin.resp.PurchaseOrderView;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.coupon.service.PurchaseOrderReadService;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.entity.UserInfoEntity;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用实体getter及批量名称查询提供不改变订单状态的运营监管数据
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PurchaseOrderReadServiceImpl implements PurchaseOrderReadService {
    private final PurchaseOrderMapper orders;
    private final PurchaseOrderItemMapper items;
    private final MerchantInfoMapper merchants;
    private final UserInfoMapper customers;
    public PageResult<PurchaseOrderView> page(PurchaseOrderQuery p) {
        p.validate();Date now=new Date();var q=QueryWrapper.create();
        if(p.getMerchantId()!=null)q.eq(PurchaseOrderEntity::getMerchantId,p.getMerchantId());
        if(p.getOrderNo()!=null && !p.getOrderNo().isBlank())q.eq(PurchaseOrderEntity::getOrderNo,p.getOrderNo());
        if(p.getStartTime()!=null)q.ge(PurchaseOrderEntity::getCreateTime,p.getStartTime());
        if(p.getEndTime()!=null)q.le(PurchaseOrderEntity::getCreateTime,p.getEndTime());
        if(p.getStatus()!=null){
            q.eq(PurchaseOrderEntity::getOrderStatus,"EXPIRED".equals(p.getStatus())?"PENDING":p.getStatus());
            if(Set.of("PENDING","EXPIRED").contains(p.getStatus())){
                // 旧订单缺expiresAt时沿用发单后15分钟失效的规则。
                Date createdBoundary=new Date(now.getTime()-15*60_000L);
                var expiry=com.mybatisflex.core.query.QueryMethods.column(PurchaseOrderEntity::getExpiresAt);
                var created=com.mybatisflex.core.query.QueryMethods.column(PurchaseOrderEntity::getCreateTime);
                q.and("PENDING".equals(p.getStatus()) ? expiry.gt(now).or(expiry.isNull().and(created.gt(createdBoundary)))
                    : expiry.le(now).or(expiry.isNull().and(created.le(createdBoundary))));
            }
        }
        var data=orders.paginate(p.getPage(),p.getSize(),q.orderBy(PurchaseOrderEntity::getId,false));
        var result=new PageResult<PurchaseOrderView>();result.setList(display(data.getRecords(),now));
        result.getPagination().setPage(data.getPageNumber());result.getPagination().setSize(data.getPageSize());result.getPagination().setTotal(data.getTotalRow());return result;
    }
    public PurchaseOrderView info(Long id){
        if(id==null || id<1)throw new CoolException("请提供有效订单ID");
        var row=orders.selectOneById(id);if(row==null)throw new CoolException("订单不存在");
        return display(List.of(row),new Date()).get(0);
    }
    private List<PurchaseOrderView> display(List<PurchaseOrderEntity> rows,Date now){
        if(rows.isEmpty())return List.of();
        var ids=rows.stream().map(PurchaseOrderEntity::getId).toList();
        var allItems=items.selectListByQuery(QueryWrapper.create().in(PurchaseOrderItemEntity::getOrderId,ids).orderBy(PurchaseOrderItemEntity::getId,true))
            .stream().collect(Collectors.groupingBy(PurchaseOrderItemEntity::getOrderId));
        var merchantIds=rows.stream().map(PurchaseOrderEntity::getMerchantId).filter(Objects::nonNull).distinct().toList();
        var customerIds=rows.stream().map(PurchaseOrderEntity::getCustomerId).filter(Objects::nonNull).distinct().toList();
        var names=merchantIds.isEmpty()?Map.<Long,MerchantInfoEntity>of():merchants.selectListByQuery(QueryWrapper.create().select(MerchantInfoEntity::getId,MerchantInfoEntity::getName).in(MerchantInfoEntity::getId,merchantIds)).stream().collect(Collectors.toMap(MerchantInfoEntity::getId,Function.identity()));
        var people=customerIds.isEmpty()?Map.<Long,UserInfoEntity>of():customers.selectListByQuery(QueryWrapper.create().select(UserInfoEntity::getId,UserInfoEntity::getNickName).in(UserInfoEntity::getId,customerIds)).stream().collect(Collectors.toMap(UserInfoEntity::getId,Function.identity()));
        return rows.stream().map(o->{
            Date expires=o.getExpiresAt()!=null?o.getExpiresAt():new Date(o.getCreateTime().getTime()+15*60_000L);
            String status="PENDING".equals(o.getOrderStatus()) && !expires.after(now)?"EXPIRED":o.getOrderStatus();
            String text=switch(status){case "PENDING"->"待支付";case "PAID"->"已完成";case "CANCELLED"->"已取消";case "FAILED"->"支付失败";case "EXPIRED"->"已超时";default->"未知状态";};
            return new PurchaseOrderView(o.getId(),o.getOrderNo(),names.containsKey(o.getMerchantId())?names.get(o.getMerchantId()).getName():"商家资料已失效",
                people.containsKey(o.getCustomerId())?people.get(o.getCustomerId()).getNickName():"用户资料已失效",o.getChannel(),status,text,o.getPaymentStatus(),o.getTotalAmount(),o.getCreateTime(),o.getPaidAt(),expires,
                allItems.getOrDefault(o.getId(),List.of()).stream().map(i->new PurchaseOrderView.Item(i.getCouponName(),i.getQuantity(),i.getUnitPrice(),i.getSubtotal())).toList());
        }).toList();
    }
}
