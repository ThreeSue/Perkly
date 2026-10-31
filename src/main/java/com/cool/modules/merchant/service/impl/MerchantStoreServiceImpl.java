package com.cool.modules.merchant.service.impl;

import cn.hutool.core.util.StrUtil;
import com.cool.core.base.BaseServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.service.MerchantInfoService;
import com.cool.modules.merchant.service.MerchantStoreCategoryService;
import com.cool.modules.merchant.service.MerchantStoreService;
import com.cool.modules.merchant.service.StoreStatus;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MerchantStoreServiceImpl extends BaseServiceImpl<MerchantStoreMapper, MerchantStoreEntity>
        implements MerchantStoreService {
    private final MerchantInfoService merchantService;
    private final MerchantStoreCategoryService categoryService;

    public MerchantStoreServiceImpl(MerchantInfoService merchantService,
            MerchantStoreCategoryService categoryService) {
        this.merchantService = merchantService;
        this.categoryService = categoryService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private MerchantSummaryService summaries;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.approval.service.ApprovalHistoryService history;

    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.coupon.mapper.ConsumptionOrderMapper consumptionOrders;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.merchant.mapper.MerchantUserStoreMapper employeeBindings;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.merchant.mapper.MerchantInfoMapper defaultMerchants;

    @Override
    public Object page(cn.hutool.json.JSONObject params, com.mybatisflex.core.paginate.Page<MerchantStoreEntity> page, QueryWrapper query) {
        com.mybatisflex.core.paginate.Page<MerchantStoreEntity> result = (com.mybatisflex.core.paginate.Page<MerchantStoreEntity>) super.page(params, page, query);
        summaries.stores(result.getRecords());
        return result;
    }

    @Override
    public Object list(cn.hutool.json.JSONObject params, QueryWrapper query) {
        java.util.List<MerchantStoreEntity> rows = (java.util.List<MerchantStoreEntity>) super.list(params, query);
        summaries.stores(rows);
        return rows;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean delete(Long... ids) {
        if (ids == null || ids.length == 0 || java.util.Arrays.stream(ids).anyMatch(Objects::isNull)) throw new CoolException("请选择有效的门店");
        Long[] ordered = java.util.Arrays.stream(ids).distinct().sorted().toArray(Long[]::new);
        for (Long id : ordered) {
            if (mapper.lockForCouponCheck(id) == null) throw new CoolException("门店不存在");
            if(!employeeBindings.selectListByQuery(QueryWrapper.create().eq(com.cool.modules.merchant.entity.MerchantUserStoreEntity::getStoreId,id).forUpdate()).isEmpty())
                throw new CoolException("门店仍绑定员工账号，请先调整账号归属；结束经营请关闭门店并保留历史");
            if (mapper.couponReferenceCount(id) > 0) throw new CoolException("门店已关联优惠券或核销记录，不能删除，请关闭门店保留历史");
            if (consumptionOrders.selectCountByQuery(QueryWrapper.create()
                    .eq(com.cool.modules.coupon.entity.ConsumptionOrderEntity::getStoreId,id)) > 0)
                throw new CoolException("门店已关联消费账单，不能删除，请关闭门店保留历史");
        }
        if(defaultMerchants.selectCountByQuery(QueryWrapper.create().in(MerchantInfoEntity::getDefaultStoreId,java.util.Arrays.asList(ordered)))>0)throw new CoolException("门店正在用作默认门店，请先取消默认设置");
        return super.delete(ordered);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long add(MerchantStoreEntity entity) {
        if (entity.getStatus() == null) entity.setStatus(1);
        normalize(entity);
        validate(entity, null);
        return super.add(entity);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean update(MerchantStoreEntity entity) {
        if (entity.getId() == null) throw new CoolException("门店ID不能为空");
        MerchantStoreEntity existing = mapper.selectOneByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getId,entity.getId()).forUpdate());
        if (existing == null) throw new CoolException("门店不存在");
        normalize(entity);
        validate(entity, existing);
        boolean result=super.update(entity);
        if(result&&!Objects.equals(existing.getStatus(),entity.getStatus()))history.record("STORE_STATE",entity.getId(),entity.getMerchantId(),1,"STATUS",StoreStatus.text(existing.getStatus())+" → "+StoreStatus.text(entity.getStatus()),null);
        return result;
    }

    private void normalize(MerchantStoreEntity entity) {
        if (entity.getName() != null) entity.setName(entity.getName().trim());
        if (entity.getCover() != null) entity.setCover(entity.getCover().trim());
        if (entity.getAddress() != null) entity.setAddress(entity.getAddress().trim());
        if (entity.getPhone() != null) entity.setPhone(entity.getPhone().trim());
        if (entity.getBusinessHours() != null) entity.setBusinessHours(entity.getBusinessHours().trim());
    }

    private void validate(MerchantStoreEntity entity, MerchantStoreEntity existing) {
        boolean requireEnabled = existing == null || (Integer.valueOf(1).equals(entity.getStatus()) && !Integer.valueOf(1).equals(existing.getStatus()))
                || !Objects.equals(existing.getMerchantId(), entity.getMerchantId())
                || !Objects.equals(existing.getCategoryId(), entity.getCategoryId());
        if (entity.getMerchantId() == null) throw new CoolException("所属商家不能为空");
        MerchantInfoEntity merchant = merchantService.getById(entity.getMerchantId());
        if (merchant == null) throw new CoolException("所属商家不存在");
        if (requireEnabled && !Integer.valueOf(1).equals(merchant.getStatus())) throw new CoolException("请选择启用中的商家");
        if (entity.getCategoryId() == null) throw new CoolException("门店类型不能为空");
        MerchantStoreCategoryEntity category = categoryService.getOne(QueryWrapper.create()
                .eq(MerchantStoreCategoryEntity::getId, entity.getCategoryId()).forUpdate());
        if (category == null) throw new CoolException("门店类型不存在");
        if (requireEnabled && !Integer.valueOf(1).equals(category.getStatus())) throw new CoolException("请选择启用中的门店类型");
        if (StrUtil.isBlank(entity.getName()) || entity.getName().length() > 100) {
            throw new CoolException("门店名称不能为空且不能超过100个字符");
        }
        if (StrUtil.isBlank(entity.getAddress()) || entity.getAddress().length() > 500) {
            throw new CoolException("门店地址不能为空且不能超过500个字符");
        }
        if (StrUtil.isBlank(entity.getPhone()) || entity.getPhone().length() > 30) {
            throw new CoolException("门店电话不能为空且不能超过30个字符");
        }
        if (!com.cool.modules.merchant.service.StoreStatus.valid(entity.getStatus())) {
            throw new CoolException("请选择营业、停业、禁用或关闭");
        }
        MerchantValidation.phone(entity.getPhone(), "门店电话");
        if (entity.getCover() != null && entity.getCover().length() > 500) throw new CoolException("门店封面地址不能超过500个字符");
        if (entity.getBusinessHours() != null && entity.getBusinessHours().length() > 4000) throw new CoolException("营业时间不能超过4000个字符");
        if ((entity.getLongitude() == null) != (entity.getLatitude() == null)) throw new CoolException("经度和纬度需要同时填写或同时留空");
        validateCoordinate(entity.getLongitude(), "经度", new BigDecimal("-180"), new BigDecimal("180"));
        validateCoordinate(entity.getLatitude(), "纬度", new BigDecimal("-90"), new BigDecimal("90"));
    }

    private void validateCoordinate(BigDecimal value, String label, BigDecimal min, BigDecimal max) {
        if (value != null && (value.compareTo(min) < 0 || value.compareTo(max) > 0)) {
            throw new CoolException(label + "超出有效范围");
        }
    }
}
