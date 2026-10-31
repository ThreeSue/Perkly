package com.cool.modules.workspace.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.workspace.entity.WorkspaceCouponStoreEntity;
import com.cool.modules.workspace.mapper.WorkspaceCouponStoreMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 将商家指定门店范围应用到优惠券和历史购券订单
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:35:00
 */
@Service @RequiredArgsConstructor
public class WorkspaceDataScope {
    private final WorkspaceAuthorization auth;
    private final WorkspaceCouponStoreMapper links;
    private final CouponTemplateMapper coupons;
    private final PurchaseOrderItemMapper items;
    public boolean all(BaseSysUserEntity user){return auth.owner(user)||"MERCHANT".equals(WorkspaceAuthorization.type(user))&&"ALL".equals(user.getStoreScope());}
    public List<Long> couponIds(BaseSysUserEntity user){
        if(all(user))return null;
        Set<Long> allowed=new HashSet<>(auth.storeIds(user));
        var grouped=links.selectListByQuery(QueryWrapper.create().eq(WorkspaceCouponStoreEntity::getMerchantId,user.getMerchantId())).stream()
            .collect(Collectors.groupingBy(WorkspaceCouponStoreEntity::getCouponTemplateId));
        return coupons.selectListByQuery(QueryWrapper.create().eq(CouponTemplateEntity::getMerchantId,user.getMerchantId()).eq(CouponTemplateEntity::getScopeType,1)).stream()
            .filter(c->grouped.containsKey(c.getId())&&!grouped.get(c.getId()).isEmpty()&&grouped.get(c.getId()).stream().allMatch(l->allowed.contains(l.getStoreId())))
            .map(CouponTemplateEntity::getId).toList();
    }
    public List<Long> purchaseIds(BaseSysUserEntity user){
        if(all(user))return null;Set<Long> allowed=new HashSet<>(auth.storeIds(user));
        return items.selectListByQuery(QueryWrapper.create().eq(PurchaseOrderItemEntity::getMerchantId,user.getMerchantId())).stream()
            .collect(Collectors.groupingBy(PurchaseOrderItemEntity::getOrderId)).entrySet().stream()
            .filter(e->e.getValue().stream().allMatch(item->snapshotWithin(item.getRuleSnapshot(),allowed))).map(Map.Entry::getKey).toList();
    }
    private boolean snapshotWithin(String json,Set<Long> allowed){try{
        var rule=cn.hutool.json.JSONUtil.parseObj(json);if(!Integer.valueOf(1).equals(rule.getInt("scopeType")))return false;
        var array=rule.getJSONArray("storeIds");return array!=null&&!array.isEmpty()&&allowed.containsAll(array.toList(Long.class));
    }catch(Exception invalid){return false;}}
    public void coupon(BaseSysUserEntity user,Long id){var allowed=couponIds(user);if(allowed!=null&&!allowed.contains(id))throw new CoolException("优惠券涉及未授权门店，不能访问或修改",403);}
    public void purchase(BaseSysUserEntity user,Long id){var allowed=purchaseIds(user);if(allowed!=null&&!allowed.contains(id))throw new CoolException("购券订单涉及未授权门店，不能访问",403);}
    public void couponInput(BaseSysUserEntity user,CouponTemplateEntity input){if(all(user))return;
        if(!Integer.valueOf(1).equals(input.getScopeType())||input.getStoreIds()==null||input.getStoreIds().isEmpty()||!auth.storeIds(user).containsAll(input.getStoreIds()))throw new CoolException("此岗位只能维护授权门店的专用券",403);
    }
}
