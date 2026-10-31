package com.cool.modules.merchant.service.impl;

import cn.hutool.core.util.StrUtil;
import com.cool.core.base.BaseServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.service.MerchantCategoryService;
import com.cool.modules.merchant.service.MerchantInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantInfoServiceImpl
        extends BaseServiceImpl<MerchantInfoMapper, MerchantInfoEntity>
        implements MerchantInfoService {
    private final MerchantCategoryService categoryService;

    @org.springframework.beans.factory.annotation.Autowired
    private MerchantSummaryService summaries;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.merchant.mapper.MerchantStoreMapper defaultStores;

    @Override
    public Object page(cn.hutool.json.JSONObject params, com.mybatisflex.core.paginate.Page<MerchantInfoEntity> page, QueryWrapper query) {
        com.mybatisflex.core.paginate.Page<MerchantInfoEntity> result = (com.mybatisflex.core.paginate.Page<MerchantInfoEntity>) super.page(params, page, query);
        summaries.merchants(result.getRecords());
        return result;
    }

    @Override
    public Object list(cn.hutool.json.JSONObject params, QueryWrapper query) {
        java.util.List<MerchantInfoEntity> rows = (java.util.List<MerchantInfoEntity>) super.list(params, query);
        summaries.merchants(rows);
        return rows;
    }

    @Override
    public Long add(MerchantInfoEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        validate(entity, true);
        return super.add(entity);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public boolean update(MerchantInfoEntity entity) {
        if (entity.getId() == null) {
            throw new CoolException("商家ID不能为空");
        }
        MerchantInfoEntity existing = mapper.selectOneByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getId,entity.getId()).forUpdate());
        if (existing == null) {
            throw new CoolException("商家不存在");
        }
        if(StrUtil.isNotBlank(existing.getLicenseNumber()) && !java.util.Objects.equals(existing.getLicenseNumber(),entity.getLicenseNumber())) throw new CoolException("营业执照号已锁定，不能修改");
        // 管理接口由框架合并原记录和提交字段后调用此方法。
        validate(entity, !java.util.Objects.equals(existing.getCategoryId(), entity.getCategoryId()));
        return super.update(entity);
    }

    private void validate(MerchantInfoEntity entity, boolean requireEnabledCategory) {
        entity.setName(required(entity.getName(), "商家名称", 100));
        entity.setContactName(required(entity.getContactName(), "联系人姓名", 50));
        entity.setContactPhone(required(entity.getContactPhone(), "联系电话", 30));
        MerchantValidation.phone(entity.getContactPhone(), "联系电话");
        if (entity.getCategoryId() == null) {
            throw new CoolException("所属分类不能为空");
        }
        MerchantCategoryEntity category = categoryService.getById(entity.getCategoryId());
        if (category == null) {
            throw new CoolException("所属分类不存在");
        }
        if (requireEnabledCategory && !Integer.valueOf(1).equals(category.getStatus())) {
            throw new CoolException("请选择启用的商家分类");
        }
        if (!Integer.valueOf(0).equals(entity.getStatus()) && !Integer.valueOf(1).equals(entity.getStatus())) {
            throw new CoolException("商家状态只能为0或1");
        }
        if(StrUtil.isNotBlank(entity.getLicenseNumber())) {entity.setLicenseNumber(entity.getLicenseNumber().trim().toUpperCase(java.util.Locale.ROOT)); if(!entity.getLicenseNumber().matches("[A-Z0-9]{15}|[A-Z0-9]{18}"))throw new CoolException("营业执照号需15或18位字母数字");}
        if(entity.getDefaultStoreId()!=null){var store=defaultStores.selectOneById(entity.getDefaultStoreId());if(store==null||!java.util.Objects.equals(entity.getId(),store.getMerchantId())||!Integer.valueOf(1).equals(store.getStatus()))throw new CoolException("默认门店必须属于本商家且正在营业");}
        checkLength(entity.getLogo(), "Logo地址", 500);
        checkLength(entity.getProvince(), "省", 50);
        checkLength(entity.getCity(), "市", 50);
        checkLength(entity.getDistrict(), "区县", 50);
        checkLength(entity.getAddress(), "详细地址", 500);
        checkLength(entity.getIntroduction(), "商家简介", 2000);
        checkLength(entity.getRemark(), "平台内部备注", 1000);
    }

    private String required(String value, String label, int maxLength) {
        if (StrUtil.isBlank(value)) {
            throw new CoolException(label + "不能为空");
        }
        String trimmed = value.trim();
        checkLength(trimmed, label, maxLength);
        return trimmed;
    }

    private void checkLength(String value, String label, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new CoolException(label + "不能超过" + maxLength + "个字符");
        }
    }
}
