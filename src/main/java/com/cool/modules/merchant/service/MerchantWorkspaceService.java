package com.cool.modules.merchant.service;

import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.CouponPublicationService;
import com.cool.modules.coupon.service.CouponTemplateService;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.mapper.MerchantWorkspaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在商家可信身份范围内查询修改门店优惠券并只读查看订单
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 20:57:31
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MerchantWorkspaceService {
    private final MerchantAccountService accounts;
    private final MerchantStoreService stores;
    private final MerchantStoreCategoryService categories;
    private final CouponTemplateService coupons;
    private final CouponPublicationService publications;
    private final MerchantInfoMapper merchants;
    private final MerchantWorkspaceMapper workspaceMapper;

    public Long merchant(Long userId) { return accounts.current(userId).getMerchantId(); }

    public Object me(Long userId) {
        var user = accounts.current(userId);
        return Map.of("id", user.getId(), "username", user.getUsername(), "name", user.getNickName(),
                "merchantId", user.getMerchantId(), "merchantRole", user.getMerchantRole(),
                "merchantName", merchants.selectOneById(user.getMerchantId()).getName());
    }

    public Object profile(Long userId) {
        return workspaceMapper.profile(merchant(userId));
    }

    public Object storePage(Long userId, int page, int size, String keyword) {
        QueryWrapper query = QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId, merchant(userId));
        if (keyword != null && !keyword.isBlank()) query.like(MerchantStoreEntity::getName, keyword.trim());
        query.orderBy(MerchantStoreEntity::getId, false);
        return stores.page(new JSONObject(), new Page<MerchantStoreEntity>(Math.max(1, page), limit(size)), query);
    }

    public Object storeInfo(Long userId, Long id) { own("store", id, merchant(userId)); return stores.info(id); }

    public Object storeSave(Long userId, MerchantStoreEntity input) {
        Long merchantId = merchant(userId);
        if (input.getId() != null) own("store", input.getId(), merchantId);
        input.setMerchantId(merchantId);
        if (input.getId() == null) return stores.add(input);
        input.setCreateTime(stores.getById(input.getId()).getCreateTime());
        return stores.update(input);
    }

    public Object storeDelete(Long userId, List<Long> ids) {
        checkIds(ids); Long merchantId = merchant(userId);
        ids.stream().distinct().sorted().forEach(id -> own("store", id, merchantId));
        return stores.delete(ids.toArray(Long[]::new));
    }

    public Object categoryOptions(Long userId) {
        merchant(userId);
        return categories.list(QueryWrapper.create()
                .eq(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getStatus, 1)
                .orderBy(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getSort, true));
    }

    public Object storeOptions(Long userId) {
        return workspaceMapper.storeOptions(merchant(userId));
    }

    public Object couponPage(Long userId, int page, int size, String keyword) {
        QueryWrapper query = QueryWrapper.create().eq(CouponTemplateEntity::getMerchantId, merchant(userId));
        if (keyword != null && !keyword.isBlank()) query.like(CouponTemplateEntity::getName, keyword.trim());
        query.orderBy(CouponTemplateEntity::getId, false);
        return coupons.page(new JSONObject(), new Page<CouponTemplateEntity>(Math.max(1, page), limit(size)), query);
    }

    public Object couponInfo(Long userId, Long id) { own("coupon_template", id, merchant(userId)); return coupons.info(id); }

    public Object couponSave(Long userId, CouponTemplateEntity input) {
        Long merchantId = merchant(userId);
        if (input.getId() != null) own("coupon_template", input.getId(), merchantId);
        input.setMerchantId(merchantId);
        // 关联门店和商品的同商家检查由原有模板服务再次校验。
        return input.getId() == null ? coupons.add(input) : coupons.update(input);
    }

    public Object couponDelete(Long userId, List<Long> ids) {
        checkIds(ids); Long merchantId = merchant(userId);
        ids.stream().distinct().sorted().forEach(id -> own("coupon_template", id, merchantId));
        return coupons.delete(ids.toArray(Long[]::new));
    }

    public Object publication(Long userId, Long id, Integer version, String action) {
        own("coupon_template", id, merchant(userId));
        return switch (action) {
            case "submitAudit" -> publications.submitAudit(id, version);
            case "withdrawAudit" -> publications.withdrawAudit(id, version);
            case "publish" -> publications.publish(id, version);
            case "unpublish" -> publications.unpublish(id, version);
            default -> throw new CoolException("不支持此商家操作", 403);
        };
    }

    public Object products(Long userId) { return coupons.productOptions(merchant(userId)); }

    public Object orderPage(Long userId, int page, int size) {
        return workspaceMapper.orderPage(merchant(userId), Math.max(1, page), limit(size));
    }

    public Object orderInfo(Long userId, Long id) {
        Long merchantId = merchant(userId); own("purchase_order", id, merchantId);
        return Map.of("order", workspaceMapper.order(merchantId, id), "items", workspaceMapper.orderItems(merchantId, id));
    }

    private void own(String table, Long id, Long merchantId) {
        if (!Set.of("store", "coupon_template", "purchase_order").contains(table) || id == null || id <= 0) throw new CoolException("数据不存在或无权访问", 403);
        // 所有写入先锁行并核对归属；事务内再调用现有业务校验，避免先查后改的归属竞态。
        if (!workspaceMapper.lockOwned(table, id, merchantId)) throw new CoolException("数据不存在或无权访问", 403);
    }

    private static int limit(int size) { return Math.min(Math.max(size, 1), 100); }
    private static void checkIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)) throw new CoolException("请选择1至100条有效记录");
    }
}
