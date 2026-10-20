package com.cool.modules.coupon.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.controller.admin.params.CouponContentParam;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper;
import com.cool.modules.coupon.service.CouponTemplateService;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理优惠券模板和适用门店并保护已有发券及发布记录
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:13:59
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CouponTemplateServiceImpl extends BaseServiceImpl<CouponTemplateMapper, CouponTemplateEntity>
        implements CouponTemplateService {
    private final MerchantInfoMapper merchants;
    private final CouponTemplateRelationsMapper relations;

    @Override
    public List<Map<String, Object>> productOptions(Long merchantId) {
        if (merchantId == null || merchants.selectOneById(merchantId) == null) throw new CoolException("请选择存在的商家");
        return relations.products(merchantId);
    }

    @Override
    public CouponTemplateEntity validateForPublication(Long id, Integer version) {
        CouponTemplateEntity e = lock(id);
        if (version == null || !version.equals(e.getRuleVersion())) throw new CoolException("券规则版本已变化，请刷新后重新操作");
        if (!Integer.valueOf(1).equals(e.getStatus())) throw new CoolException("请先启用优惠券模板");
        enrich(List.of(e));
        CouponTemplateRules.validate(e);
        validateMerchant(e);
        validateStores(e, e.getStoreIds());
        validateContents(e);
        if (relations.usableStoreCount(e.getMerchantId(), e.getScopeType(), e.getStoreIds()) == 0) {
            throw new CoolException("没有营业中的适用门店，不能提交审核或上架");
        }
        return e;
    }

    @Override
    public Long add(CouponTemplateEntity e) {
        e.setId(null);
        e.setIssuedCount(0L);
        e.setRuleVersion(1);
        CouponTemplateRules.validate(e);
        validateMerchant(e);
        validateStores(e, List.of());
        validateContents(e);
        Long id = super.add(e);
        saveStores(e);
        relations.replaceContents(e.getMerchantId(), id, e.getContentItems());
        return id;
    }

    @Override
    public boolean update(CouponTemplateEntity e) {
        CouponTemplateEntity old = lock(e.getId());
        if (!Integer.valueOf(1).equals(e.getStatus()) && relations.activePublications(e.getId()) > 0) {
            throw new CoolException("此券正在审核或已上架，请先撤回审核或下架后停用");
        }
        if (!Objects.equals(old.getMerchantId(), e.getMerchantId())) throw new CoolException("优惠券创建后不能更换所属商家");
        List<Long> oldStores = storeIds(e.getId());
        List<CouponContentParam> oldContents = contentItems(e.getId());
        if (e.getContentItems() == null) e.setContentItems(oldContents);
        if (e.getStoreIds() == null) e.setStoreIds(oldStores);
        e.setIssuedCount(old.getIssuedCount());
        e.setCreateTime(old.getCreateTime());
        CouponTemplateRules.validate(e);
        validateMerchant(e);
        validateStores(e, oldStores);
        validateContents(e);
        old.setStoreIds(oldStores);
        boolean changed = !ruleFields(old).equals(ruleFields(e)) || !new HashSet<>(oldStores).equals(new HashSet<>(e.getStoreIds()))
                || !oldContents.equals(e.getContentItems());
        if (changed && relations.activePublications(e.getId()) > 0) {
            throw new CoolException("此券正在审核或已上架，请先撤回审核或下架后修改规则");
        }
        e.setRuleVersion(old.getRuleVersion() + (changed ? 1 : 0));
        // false表示把已清空的旧类型金额、旧有效期字段也写入NULL。
        boolean updated = mapper.update(e, false) > 0;
        if (updated) {
            saveStores(e);
            relations.replaceContents(e.getMerchantId(), e.getId(), e.getContentItems());
        }
        return updated;
    }

    @Override
    public boolean delete(Long... ids) {
        if (ids == null || ids.length == 0 || Arrays.stream(ids).anyMatch(Objects::isNull)) throw new CoolException("请选择有效的优惠券");
        Long[] ordered = Arrays.stream(ids).distinct().sorted().toArray(Long[]::new);
        for (Long id : ordered) {
            CouponTemplateEntity e = lock(id);
            if (e.getStatus() == 1 || e.getIssuedCount() > 0) throw new CoolException("启用中或已经发放的优惠券不能删除");
            if (relations.references(id) > 0) throw new CoolException("优惠券已有发布、商品内容或发放关联记录，不能删除");
        }
        for (Long id : ordered) relations.deleteStores(id);
        return super.delete(ordered);
    }

    @Override
    public Object info(Long id) {
        CouponTemplateEntity e = getById(id);
        if (e == null) throw new CoolException("优惠券不存在");
        enrich(List.of(e));
        return e;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object page(JSONObject params, Page<CouponTemplateEntity> page, QueryWrapper query) {
        Page<CouponTemplateEntity> result = (Page<CouponTemplateEntity>) super.page(params, page, query);
        enrich(result.getRecords());
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object list(JSONObject params, QueryWrapper query) {
        List<CouponTemplateEntity> result = (List<CouponTemplateEntity>) super.list(params, query);
        enrich(result);
        return result;
    }

    private CouponTemplateEntity lock(Long id) {
        if (id == null || relations.lockTemplate(id) == null) throw new CoolException("优惠券不存在");
        // 锁查询会清除会话缓存，重新读取数据库值，避免调用方修改了之前查出的同一实体对象。
        return getById(id);
    }

    private void validateMerchant(CouponTemplateEntity e) {
        MerchantInfoEntity merchant = e.getMerchantId() == null ? null : merchants.selectOneById(e.getMerchantId());
        if (merchant == null) throw new CoolException("请选择存在的所属商家");
        if (e.getStatus() == 1 && !Integer.valueOf(1).equals(merchant.getStatus())) throw new CoolException("所属商家已停用，不能启用优惠券");
    }

    private void validateStores(CouponTemplateEntity e, List<Long> oldStores) {
        if (e.getScopeType() == 0) { e.setStoreIds(new ArrayList<>()); return; }
        if (e.getStoreIds() == null || e.getStoreIds().isEmpty()) throw new CoolException("指定门店时至少选择一家门店");
        if (e.getStoreIds().stream().anyMatch(Objects::isNull)) throw new CoolException("门店选项无效");
        e.setStoreIds(e.getStoreIds().stream().distinct().sorted().collect(Collectors.toList()));
        for (Long storeId : e.getStoreIds()) {
            Map<String, Object> store = relations.store(storeId);
            if (store == null || !Objects.equals(number(store.get("merchant_id")), e.getMerchantId())) throw new CoolException("适用门店必须属于所选商家");
            if ((e.getStatus() == 1 || !oldStores.contains(storeId)) && ((Number)store.get("status")).intValue() != 1) {
                throw new CoolException("请选择营业中的门店，原门店已停用时请调整范围");
            }
        }
    }

    private void validateContents(CouponTemplateEntity e) {
        List<CouponContentParam> items = e.getContentItems() == null ? new ArrayList<>() : e.getContentItems();
        if (items.size() > 100) throw new CoolException("每张券最多配置100个内容项目");
        boolean contentType = Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE").contains(e.getType());
        if (!contentType && !items.isEmpty()) throw new CoolException("满减、折扣和代金券不配置套餐内容，请先移除内容或更换券类型");
        if (contentType && e.getStatus() == 1 && items.isEmpty()) throw new CoolException("请先配置套餐或服务内容，再启用优惠券");
        Set<Long> ids = new HashSet<>();
        Set<String> newNames = new HashSet<>();
        for (CouponContentParam item : items) {
            if (item == null || item.getQuantity() == null || item.getQuantity() < 1) throw new CoolException("项目数量必须为正整数");
            String note = item.getItemDescription();
            item.setItemDescription(note == null || note.isBlank() ? null : note.trim());
            if (item.getItemDescription() != null && item.getItemDescription().length() > 500) throw new CoolException("项目说明不能超过500字");
            if (item.getProductId() != null) {
                if (!ids.add(item.getProductId())) throw new CoolException("相同项目请合并数量，不要重复选择");
                Map<String, Object> p = relations.product(item.getProductId());
                if (p == null || !Objects.equals(number(p.get("merchant_id")), e.getMerchantId())) throw new CoolException("内容项目必须属于所选商家");
                if (((Number)p.get("status")).intValue() != 1) throw new CoolException("内容项目已停用，请调整后保存");
                item.setName((String)p.get("name")); item.setType((String)p.get("type"));
            } else {
                if (item.getName() == null || item.getName().isBlank() || item.getName().trim().length() > 100) throw new CoolException("项目名称必填且不能超过100字");
                item.setName(item.getName().trim());
                if (!Set.of("GOODS", "SERVICE", "EXPERIENCE").contains(item.getType() == null ? "" : item.getType())) throw new CoolException("请选择菜品商品、服务或体验项目");
                if (!newNames.add(item.getType() + ":" + item.getName().toLowerCase(Locale.ROOT))) throw new CoolException("相同的新项目请合并数量");
            }
        }
        e.setContentItems(items);
    }

    private List<CouponContentParam> contentItems(Long id) {
        return relations.contentRows(id).stream().map(this::contentItem).collect(Collectors.toList());
    }

    private CouponContentParam contentItem(Map<String, Object> row) {
            var item = new CouponContentParam();
            item.setProductId(number(row.get("product_id")));
            item.setName((String) row.get("name")); item.setType((String) row.get("type"));
            item.setQuantity(((Number)row.get("quantity")).intValue());
            item.setItemDescription((String) row.get("item_description"));
            return item;
    }

    private void saveStores(CouponTemplateEntity e) {
        relations.deleteStores(e.getId());
        for (Long storeId : e.getStoreIds()) {
            relations.insertStore(e.getMerchantId(), e.getId(), storeId);
        }
    }

    private List<Long> storeIds(Long id) {
        return relations.storeIds(id);
    }

    private Map<String, Object> ruleFields(CouponTemplateEntity e) {
        Map<String, Object> result = BeanUtil.beanToMap(e);
        for (String key : List.of("id", "createTime", "updateTime", "issuedCount", "ruleVersion", "status", "merchantName", "storeIds", "storeNames", "contentItems", "publication", "queryWrapper")) result.remove(key);
        return result;
    }

    private void enrich(List<CouponTemplateEntity> rows) {
        if (rows.isEmpty()) return;
        Map<Long, CouponTemplateEntity> byId = rows.stream().collect(Collectors.toMap(CouponTemplateEntity::getId, e -> e));
        Set<Long> merchantIds = rows.stream().map(CouponTemplateEntity::getMerchantId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        relations.merchantNames(merchantIds)
                .forEach(row -> names.put(number(row.get("id")), (String)row.get("name")));
        rows.forEach(e -> {
            e.setMerchantName(names.getOrDefault(e.getMerchantId(), "商家资料已失效"));
            e.setStoreIds(new ArrayList<>());
            e.setStoreNames(new ArrayList<>());
            e.setContentItems(new ArrayList<>());
        });
        relations.contentRows(byId.keySet()).forEach(row -> byId.get(number(row.get("coupon_template_id")))
                .getContentItems().add(contentItem(row)));
        relations.storeNames(byId.keySet())
                .forEach(row -> {
                    CouponTemplateEntity e = byId.get(number(row.get("coupon_template_id")));
                    e.getStoreIds().add(number(row.get("store_id")));
                    e.getStoreNames().add(row.get("name") == null ? "门店资料已失效" : (String) row.get("name"));
                });
        Map<Long, Map<String, Object>> publications = new HashMap<>();
        relations.publications(byId.keySet()).forEach(row -> {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("auditStatus", row.get("audit_status"));
            state.put("publishStatus", row.get("publish_status"));
            state.put("submittedVersion", row.get("submitted_version"));
            state.put("publishedVersion", row.get("published_version"));
            state.put("submittedAt", row.get("submitted_at"));
            state.put("publishedAt", row.get("published_at"));
            publications.put(number(row.get("coupon_template_id")), state);
        });
        rows.forEach(e -> e.setPublication(publications.getOrDefault(e.getId(),
                Map.of("auditStatus", 0, "publishStatus", 0))));
    }

    private static Long number(Object value) { return value == null ? null : ((Number)value).longValue(); }
}

