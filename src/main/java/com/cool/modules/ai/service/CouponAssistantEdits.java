package com.cool.modules.ai.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.controller.admin.params.CouponContentParam;
import com.cool.modules.coupon.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;


import com.cool.modules.ai.service.CouponAssistantService.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 维护优惠券编辑字段目录并生成复用业务验证的修改计划
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 19:43:00
 */
@Service
@RequiredArgsConstructor
public class CouponAssistantEdits {
    private final com.cool.modules.merchant.mapper.MerchantStoreMapper merchantStores;
    private static final String STOCK_REMAINING = "REMAINING";
    private static final String STOCK_TOTAL = "TOTAL";
    public record EditField(String key, String label, String question, String kind, String unit, Set<String> types) {}

    public static final List<EditField> EDIT_FIELDS = List.of(
        new EditField("name", "券名称", "券名称要改成什么？", "TEXT", "", Set.of()),
        new EditField("description", "使用说明", "使用说明要改成什么？", "TEXT", "", Set.of()),
        new EditField("salePrice", "售价", "售价要改成多少元？", "NUMBER", "元", Set.of()),
        new EditField("minSpend", "最低消费", "最低消费要改成多少元？", "NUMBER", "元", Set.of("CASH", "DISCOUNT", "FULL_REDUCTION")),
        new EditField("deductAmount", "抵扣金额", "抵扣金额要改成多少元？", "NUMBER", "元", Set.of("CASH")),
        new EditField("discountAmount", "减免金额", "减免金额要改成多少元？", "NUMBER", "元", Set.of("FULL_REDUCTION")),
        new EditField("discountRate", "折扣数", "你想改成几折？", "NUMBER", "折", Set.of("DISCOUNT")),
        new EditField("validDays", "发券后有效天数", "领券后要有效几天？", "INTEGER", "天", Set.of()),
        new EditField("perCustomerLimit", "每人限领张数", "每人最多可领几张？", "INTEGER", "张", Set.of()),
        new EditField("maxDiscount", "最高优惠金额", "最高优惠金额要设为多少元？", "NUMBER", "元", Set.of("DISCOUNT")),
        new EditField("allowCombine", "同享其他优惠", "是否允许同享其他优惠？", "STRUCTURED", "", Set.of()),
        new EditField("validityType", "有效期方式", "使用固定日期，还是领券后按天数有效？", "STRUCTURED", "", Set.of()),
        new EditField("validFrom", "固定有效期开始", "固定有效期从哪天几点开始？", "STRUCTURED", "", Set.of()),
        new EditField("validTo", "固定有效期结束", "固定有效期到哪天几点结束？", "STRUCTURED", "", Set.of()),
        new EditField("saleStartTime", "领取购买开始", "从哪天几点开始领取购买？", "STRUCTURED", "", Set.of()),
        new EditField("saleEndTime", "领取购买结束", "到哪天几点停止领取购买？", "STRUCTURED", "", Set.of()),
        new EditField("scopeType", "门店范围", "适用于全部营业门店，还是指定门店？", "STRUCTURED", "", Set.of()),
        new EditField("storeNames", "指定门店", "请提供适用门店的完整名称；同名门店可在表单选择。", "STRUCTURED", "", Set.of()),
        new EditField("contentItems", "套餐或服务内容", "请提供完整内容清单及每项数量；新清单将替换原清单。", "STRUCTURED", "", Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE"))
    );

    public void apply(CouponTemplateEntity row, Fields fields, List<Change> changes) {
        if (fields == null) return;
        if (fields.deductAmount() != null && !"CASH".equals(row.getType())
            || fields.discountAmount() != null && !"FULL_REDUCTION".equals(row.getType())
            || fields.discountRate() != null && !"DISCOUNT".equals(row.getType())
            || fields.minSpend() != null && !Set.of("CASH", "DISCOUNT", "FULL_REDUCTION").contains(row.getType()))
            throw new CoolException("所填优惠规则与券类型不符");
        change(changes, "券名称", row.getName(), fields.name() == null ? null : fields.name().strip(), row::setName);
        change(changes, "使用说明", row.getDescription(), fields.description(), row::setDescription);
        change(changes, "售价", row.getSalePrice(), fields.salePrice(), row::setSalePrice);
        change(changes, "最低消费", row.getMinSpend(), fields.minSpend(), row::setMinSpend);
        change(changes, "抵扣金额", row.getDeductAmount(), fields.deductAmount(), row::setDeductAmount);
        change(changes, "减免金额", row.getDiscountAmount(), fields.discountAmount(), row::setDiscountAmount);
        change(changes, "折扣数", row.getDiscountRate(), fields.discountRate(), row::setDiscountRate);
        if (fields.maxDiscount() != null && !"DISCOUNT".equals(row.getType())) throw new CoolException("仅折扣券支持最高优惠金额");
        change(changes, "最高优惠金额", row.getMaxDiscount(), fields.maxDiscount(), row::setMaxDiscount);
        if (fields.allowCombine() != null) {
            if (!Set.of(0, 1).contains(fields.allowCombine())) throw new CoolException("请选择是否同享其他优惠");
            if (!Objects.equals(row.getAllowCombine(), fields.allowCombine())) {
                changes.add(new Change("同享其他优惠", Integer.valueOf(1).equals(row.getAllowCombine()) ? "是" : "否", fields.allowCombine() == 1 ? "是" : "否"));
                row.setAllowCombine(fields.allowCombine());
            }
        }
        String validity = fields.validityType();
        if (("FIXED".equals(validity) || fields.validFrom() != null || fields.validTo() != null) && fields.validDays() != null
            || "AFTER_ISSUE".equals(validity) && (fields.validFrom() != null || fields.validTo() != null))
            throw new CoolException("固定日期与发券后天数不能同时设置，请选择一种有效期方式");
        if (validity == null && fields.validDays() != null) validity = "AFTER_ISSUE";
        if (validity == null && (fields.validFrom() != null || fields.validTo() != null)) validity = "FIXED";
        if (validity != null && !Set.of("FIXED", "AFTER_ISSUE").contains(validity)) throw new CoolException("请选择有效期方式");
        if (validity != null && !Objects.equals(validity, row.getValidityType())) {
            changes.add(new Change("有效期方式", "FIXED".equals(row.getValidityType()) ? "固定日期" : "领券后按天", "FIXED".equals(validity) ? "固定日期" : "领券后按天"));
            row.setValidityType(validity);
            if ("FIXED".equals(validity) && row.getValidDays() != null) changes.add(new Change("发券后有效天数", row.getValidDays(), "清除，改用固定日期"));
            if ("AFTER_ISSUE".equals(validity) && row.getValidFrom() != null) changes.add(new Change("固定有效期", "已设置", "清除，改用领券后天数"));
        }
        dateChange(changes, "固定有效期开始", row.getValidFrom(), fields.validFrom(), row::setValidFrom);
        dateChange(changes, "固定有效期结束", row.getValidTo(), fields.validTo(), row::setValidTo);
        dateChange(changes, "领取购买开始", row.getSaleStartTime(), fields.saleStartTime(), row::setSaleStartTime);
        dateChange(changes, "领取购买结束", row.getSaleEndTime(), fields.saleEndTime(), row::setSaleEndTime);
        change(changes, "发券后有效天数", row.getValidDays(), fields.validDays(), row::setValidDays);
        change(changes, "每人限领张数", row.getPerCustomerLimit(), fields.perCustomerLimit(), row::setPerCustomerLimit);
        applyStores(row, fields, changes);
        if (fields.contentItems() != null && (!fields.contentItems().isEmpty()
            || Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE").contains(row.getType()))) {
            if (!Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE").contains(row.getType())) throw new CoolException("此券型不支持内容清单");
            if (fields.contentItems().size() > 100) throw new CoolException("每张券最多配置100个内容项目");
            var content = fields.contentItems().stream().map(item -> {
                if (item == null || item.name() == null || item.name().isBlank() || item.quantity() == null || item.quantity() < 1)
                    throw new CoolException("请补全每个内容项目名称和正整数数量");
                var entry = new CouponContentParam();
                entry.setName(item.name().strip()); entry.setType(Objects.toString(item.type(), "GOODS"));
                entry.setQuantity(item.quantity()); entry.setItemDescription(item.itemDescription());
                if (!Set.of("GOODS", "SERVICE", "EXPERIENCE").contains(entry.getType())) throw new CoolException("内容项目类型无效");
                return entry;
            }).toList();
            if (!content.equals(row.getContentItems())) {
                changes.add(new Change("完整内容清单（替换）", contents(row.getContentItems()), contents(content)));
                row.setContentItems(content);
            }
        }
    }

    private String contents(List<CouponContentParam> items) {
        return items == null || items.isEmpty() ? "无" : items.stream()
            .map(item -> item.getName() + " × " + item.getQuantity() + (item.getItemDescription() == null ? "" : "（" + item.getItemDescription() + "）"))
            .collect(java.util.stream.Collectors.joining("、"));
    }

    private void applyStores(CouponTemplateEntity row, Fields fields, List<Change> changes) {
        if (fields.scopeType() == null && fields.storeNames() == null) return;
        int scopeType = fields.scopeType() == null ? 1 : fields.scopeType();
        if (scopeType != 0 && scopeType != 1) throw new CoolException("适用门店范围无效");
        if (scopeType == 0 && fields.storeNames() != null && !fields.storeNames().isEmpty()) throw new CoolException("全部营业门店与指定门店不能同时设置");
        var ids = new ArrayList<Long>();
        var names = new ArrayList<String>();
        if (scopeType == 1) {
            if (fields.storeNames() == null || fields.storeNames().isEmpty()) throw new CoolException("请指定至少一家门店");
            for (String name : fields.storeNames()) {
                if (name == null || name.isBlank()) throw new CoolException("请提供完整门店名称");
                var found = merchantStores.selectListByQuery(QueryWrapper.create()
                    .eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getMerchantId, row.getMerchantId())
                    .eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getName, name.strip())
                    .eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getStatus, 1).limit(2));
                if (found.size() != 1) throw new CoolException("无法唯一匹配本商家的营业门店，请核对全名或在表单选择");
                if (!ids.contains(found.get(0).getId())) { ids.add(found.get(0).getId()); names.add(found.get(0).getName()); }
            }
        }
        if (!Objects.equals(row.getScopeType(), scopeType) || !new HashSet<>(Objects.requireNonNullElse(row.getStoreIds(), List.of())).equals(new HashSet<>(ids))) {
            changes.add(new Change("适用门店", Integer.valueOf(0).equals(row.getScopeType()) ? "全部营业门店" : String.join("、", Objects.requireNonNullElse(row.getStoreNames(), List.of())), scopeType == 0 ? "全部营业门店" : String.join("、", names)));
            row.setScopeType(scopeType); row.setStoreIds(ids); row.setStoreNames(names);
        }
    }

    private void dateChange(List<Change> changes, String label, Date before, String input, java.util.function.Consumer<Date> setter) {
        if (input == null) return;
        Date after;
        try { after = Date.from(LocalDateTime.parse(input, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.of("Asia/Shanghai")).toInstant()); }
        catch (DateTimeException invalid) { throw new CoolException(label + "需要明确的北京时间，格式为 yyyy-MM-ddTHH:mm:ss"); }
        if (!Objects.equals(before, after)) {
            changes.add(new Change(label, before == null ? "未设置" : before.toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime().toString(), input));
            setter.accept(after);
        }
    }

    public void applyStock(CouponTemplateEntity row, StockEdit stock, List<Change> changes) {
        if (stock == null) return;
        long issued = Objects.requireNonNullElse(row.getIssuedCount(), 0L);
        if (stock.quantity() == null || stock.quantity().signum() < 0
            || !Set.of(STOCK_TOTAL, STOCK_REMAINING).contains(Objects.toString(stock.basis(), "")))
            throw new CoolException("请填写有效的库存口径及张数");
        long total;
        try {
            long quantity = stock.quantity().longValueExact();
            total = STOCK_REMAINING.equals(stock.basis()) ? Math.addExact(issued, quantity) : quantity;
        } catch (ArithmeticException overflow) {
            throw new CoolException("张数必须是有效整数，且发行总量不得超出可设置范围");
        }
        if (total < 1 || total < issued) throw new CoolException("发行总量必须大于0，且不能小于已发放数量");
        Long previous = row.getIssueLimit();
        if (Objects.equals(previous, total)) return;
        changes.add(new Change("发行总量", previous == null ? "不限量" : previous, total));
        changes.add(new Change("剩余可领数量", previous == null ? "不限量" : Math.max(0, previous - issued), total - issued));
        row.setIssueLimit(total);
    }

    private <T> void change(List<Change> changes, String label, T before, T after, java.util.function.Consumer<T> setter) {
        if (after == null || Objects.equals(before, after)) return;
        changes.add(new Change(label, before, after));
        setter.accept(after);
    }

}
