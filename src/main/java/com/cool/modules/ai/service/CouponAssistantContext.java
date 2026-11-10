package com.cool.modules.ai.service;

import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;


import com.cool.modules.ai.service.CouponAssistantService.*;
import static com.cool.modules.ai.service.CouponAssistantService.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理优惠券会话的选择范围和查询条件继承
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 19:49:00
 */
@Service
@RequiredArgsConstructor
public class CouponAssistantContext {
    private final CoolCache cache;
    private final ObjectMapper json;
    private final CouponTemplateMapper templates;
    private final CouponAssistantQueries queries;
    private record QueryContext(Selection selection, Intent intent, long expires) {}
    public static final Set<String> QUERY_ACTIONS = Set.of("FIND", "COUNT", "RANK_SALES", "RANK_REDEEM", "REPORT");
    private static final Set<String> QUERY_FIELDS = Set.of("couponName", "type", "status", "auditStatus", "publishStatus",
        "merchantName", "storeName", "startDay", "endDay", "relativeDays", "price", "groupBy");

    private record SelectedCoupon(String name, String type) {}

    public Chat withSelectionContext(BaseSysUserEntity account, Long merchant, Chat original, Chat input) {
        if (input.history() != null && (input.history().size() > 16
            || input.history().stream().anyMatch(t -> t == null || t.text() == null || t.text().length() > 1500)))
            throw new CoolException("对话上下文过长");
        var ids = selected(original);
        Long target = original.couponId() != null ? original.couponId() : ids.size() == 1 ? ids.get(0) : null;
        if (target == null) return input;
        var row = templates.selectOneByQuery(queries.visible(account, merchant, original.storeId()).eq(CouponTemplateEntity::getId, target));
        if (row == null) return input;
        var history = new ArrayList<Turn>();
        if (input.history() != null) history.addAll(input.history().stream().skip(Math.max(0, input.history().size() - 15)).toList());
        history.add(new Turn("assistant", "后端已核验的当前唯一选中券：" + encode(new SelectedCoupon(row.getName(), row.getType()))
            + "。指代这张券时couponName留空，由后端按选中ID定位，不要把历史搜索关键词当完整券名。"));
        return new Chat(input.text(), history, input.merchantId(), input.storeId(), input.couponId(), input.selectedIds(),
            input.answer(), input.planToken(), input.queryToken());
    }

    public Chat withQueryContext(Long actor, boolean platform, Chat input) {
        if (input.queryToken() == null) return input;
        if (input.history() != null && (input.history().size() > 16
            || input.history().stream().anyMatch(t -> t == null || t.text() == null || t.text().length() > 1500)))
            throw new CoolException("对话上下文过长");
        Object raw = cache.get(key(actor, platform, "query:" + input.queryToken()));
        if (!(raw instanceof String encoded)) return input;
        try {
            var context = json.readValue(encoded, QueryContext.class);
            if (context.expires() < System.currentTimeMillis() || !context.selection().equals(selection(input))) return input;
            var history = new ArrayList<Turn>();
            if (input.history() != null) history.addAll(input.history().stream().skip(Math.max(0, input.history().size() - 15)).toList());
            ObjectNode saved = json.valueToTree(context.intent());
            saved.remove(List.of("action", "fields", "stock", "requestedFields", "revision"));
            if (saved.get("query") instanceof ObjectNode query) query.remove("contextMode");
            history.add(new Turn("assistant", "后端保存的上次查询条件（仅供理解范围）：" + encode(saved)));
            return new Chat(input.text(), history, input.merchantId(), input.storeId(), input.couponId(), input.selectedIds(),
                input.answer(), input.planToken(), input.queryToken());
        } catch (java.io.IOException invalid) { return input; }
    }

    public Intent resolveQuery(Long actor, boolean platform, Chat input, Intent current) {
        if (!QUERY_ACTIONS.contains(Objects.toString(current.action(), "")) || current.query() == null) return current;
        var spec = current.query();
        var clear = spec.clear() == null ? List.<String>of() : spec.clear();
        if (!QUERY_FIELDS.containsAll(clear)) throw new CoolException("包含暂不支持清除的筛选条件，请重新说明范围");
        if (spec.contextMode() != null && !Set.of("RESET", "INHERIT").contains(spec.contextMode()))
            throw new CoolException("查询范围不明确，请说明沿用上次范围还是重新查询");
        Intent previous = null;
        if ("INHERIT".equals(spec.contextMode())) {
            if (input.queryToken() == null) throw new CoolException("没有可沿用的查询范围，请补充要统计的商家、门店或券型");
            try {
                Object raw = cache.get(key(actor, platform, "query:" + input.queryToken()));
                if (!(raw instanceof String encoded)) throw new CoolException("上次查询范围已失效，请重新说明筛选条件");
                var saved = json.readValue(encoded, QueryContext.class);
                if (saved.expires() < System.currentTimeMillis() || !saved.selection().equals(selection(input)))
                    throw new CoolException("券选择或筛选范围已变化，请重新说明查询范围");
                previous = saved.intent();
            } catch (java.io.IOException invalid) { throw new CoolException("查询范围记录无效，请重新查询"); }
        }
        final Intent base = previous;
        // Structured models sometimes emit an empty optional object instead of null. It is not an instruction to clear a filter.
        PriceFilter price = spec.price();
        if (price != null && (price.mode() == null || "ANY".equals(price.mode())) && price.min() == null && price.max() == null) price = null;
        var merged = new QuerySpec("RESET",
            inherit("price", price, base == null || base.query() == null ? null : base.query().price(), clear),
            List.of(), inherit("groupBy", spec.groupBy(), base == null || base.query() == null ? null : base.query().groupBy(), clear),
            spec.unsupportedConditions(), spec.metrics(), spec.relativeDays() != null ? spec.relativeDays()
                : current.startDay() != null || current.endDay() != null ? null
                : inherit("relativeDays", null, base == null || base.query() == null ? null : base.query().relativeDays(), clear));
        return new Intent(current.action(), current.question(),
            inherit("couponName", current.couponName(), base == null ? null : base.couponName(), clear),
            inherit("type", current.type(), base == null ? null : base.type(), clear),
            spec.relativeDays() != null ? null : inherit("startDay", current.startDay(), base == null ? null : base.startDay(), clear),
            spec.relativeDays() != null ? null : inherit("endDay", current.endDay(), base == null ? null : base.endDay(), clear), current.fields(),
            inherit("status", current.status(), base == null ? null : base.status(), clear),
            inherit("auditStatus", current.auditStatus(), base == null ? null : base.auditStatus(), clear),
            inherit("publishStatus", current.publishStatus(), base == null ? null : base.publishStatus(), clear),
            current.reason(), current.batch(),
            inherit("merchantName", current.merchantName(), base == null ? null : base.merchantName(), clear),
            inherit("storeName", current.storeName(), base == null ? null : base.storeName(), clear),
            current.stock(), current.requestedFields(), merged);
    }

    private <T> T inherit(String field, T current, T previous, List<String> clear) {
        return current != null ? current : clear.contains(field) ? null : previous;
    }

    public String rememberQuery(Long actor, boolean platform, Chat input, Intent intent) {
        String token = UUID.randomUUID().toString();
        cache.set(key(actor, platform, "query:" + token),
            encode(new QueryContext(selection(input), intent, System.currentTimeMillis() + 30 * 60_000)), 1800);
        return token;
    }

    public String queryProblem(Intent intent) {
        if (intent.query() != null && intent.query().unsupportedConditions() != null && !intent.query().unsupportedConditions().isEmpty())
            return "这次要求中的「" + String.join("、", intent.query().unsupportedConditions())
                + "」暂不支持，尚未执行查询。可以按商家、门店、券名、券型、模板/审核/上架状态、售价筛选并统计券款数；"
                + "请确认是否去掉不支持的条件，或到优惠券管理页面查看对应规则。";
        if (!QUERY_ACTIONS.contains(Objects.toString(intent.action(), ""))) return null;
        if (Set.of("COUNT", "FIND").contains(intent.action()) && (intent.startDay() != null || intent.endDay() != null
            || intent.query() != null && intent.query().relativeDays() != null))
            return "优惠券款数和查券暂不支持日期筛选，不能把它当作销量日期处理。你要查询某段时间的销量，还是去掉日期统计当前券款数？";
        if (intent.query() != null) {
            String group = intent.query().groupBy();
            if (group != null && !Set.of("NONE", "TYPE", "PRICE", "STATUS").contains(group))
                return "暂不支持按「" + group + "」分组。当前可按券型、免费/付费、模板状态统计；请选择一种口径。";
            if (!Set.of("COUNT", "REPORT").contains(intent.action()) && group != null && !"NONE".equals(group))
                return "分组目前仅用于优惠券款数统计，暂不支持分组查券或分组销量排名。你要统计分组款数，还是取消分组继续？";
            // Validate even before COUNT removes the price predicate for its comparison denominator.
            queries.priceFilter(QueryWrapper.create(), intent.query().price());
        }
        return null;
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new CoolException("助手数据格式无效"); }
    }
}
