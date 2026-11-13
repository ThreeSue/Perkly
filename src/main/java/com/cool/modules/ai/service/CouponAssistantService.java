package com.cool.modules.ai.service;

import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.coupon.service.CouponTemplateService;
import com.cool.modules.coupon.service.impl.CouponTemplateRules;
import com.cool.modules.merchant.service.MerchantWorkspaceService;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.workspace.mapper.WorkspaceCouponStoreMapper;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.workspace.service.WorkspaceDataScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import com.cool.modules.ai.service.CouponAssistantEdits.EditField;
import static com.cool.modules.ai.service.CouponAssistantEdits.EDIT_FIELDS;
import static com.cool.modules.ai.service.CouponAssistantContext.QUERY_ACTIONS;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 将优惠券助手意图映射为受权限约束的查询、草稿及确认操作
 * @Version: v1.1
 * @Date: 2026-11-08
 * @Time: 23:18:14
 */
@Service
@RequiredArgsConstructor
public class CouponAssistantService {
 @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.ai.service.AiFeatureControl featureControl;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> TYPES = Set.of("CASH", "DISCOUNT", "FULL_REDUCTION", "PACKAGE", "EXPERIENCE", "EXCHANGE");
    private static final String UPDATE_AND_SUBMIT = "UPDATE_AND_SUBMIT";
    private static final String STOCK_REMAINING = "REMAINING";
    private static final String STOCK_TOTAL = "TOTAL";
    private static final java.util.regex.Pattern STOCK_WORDS = java.util.regex.Pattern.compile(
        "库存|存量|余量|剩余|剩下|剩[0-9]|发行|发放|发售|总量|总共|总数|可领|能领|限量|限发|限售|\\bstock\\b|\\binventory\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);
    private final CouponAssistantContext context;
    private final CouponAssistantQueries queries;
    private final CouponAssistantEdits edits;
    private final CouponAssistantNarrator narrator;
    private final CouponAssistantInterpreter interpreter;
    private final CouponAssistantRecovery recovery;
    private final ObjectMapper json;
    private final SupportAccess access;
    private final WorkspaceAuthorization auth;
    private final WorkspaceDataScope scope;
    private final CouponTemplateService coupons;
    private final com.cool.modules.coupon.service.CouponPublicationService publications;
    private final CouponTemplateMapper templates;
    private final CouponTemplateRelationsMapper couponRelations;
    private final WorkspaceCouponStoreMapper stores;
    private final com.cool.modules.merchant.mapper.MerchantInfoMapper merchants;
    private final com.cool.modules.merchant.mapper.MerchantStoreMapper merchantStores;
    private final MerchantWorkspaceService workspace;
    private final CoolCache cache;

    public record Turn(String role, String text) {}
    public record Chat(String text, List<Turn> history, Long merchantId, Long storeId, Long couponId, List<Long> selectedIds,
                       Answer answer, String planToken, String queryToken) {
        public Chat(String text, List<Turn> history, Long merchantId, Long storeId, Long couponId, List<Long> selectedIds,
                    Answer answer, String planToken) {
            this(text, history, merchantId, storeId, couponId, selectedIds, answer, planToken, null);
        }
        public Chat(String text, List<Turn> history, Long merchantId, Long storeId, Long couponId, List<Long> selectedIds, Answer answer) {
            this(text, history, merchantId, storeId, couponId, selectedIds, answer, null);
        }
        public Chat(String text, List<Turn> history, Long merchantId, Long storeId, Long couponId, List<Long> selectedIds) {
            this(text, history, merchantId, storeId, couponId, selectedIds, null);
        }
        public Chat(String text, List<Turn> history, Long merchantId, Long storeId, Long couponId) {
            this(text, history, merchantId, storeId, couponId, null);
        }
    }
    public record Fields(String name, String type, String description, BigDecimal salePrice, BigDecimal minSpend,
                         BigDecimal deductAmount, BigDecimal discountAmount, BigDecimal discountRate,
                         List<DraftItem> contentItems, Integer validDays, Integer perCustomerLimit,
                         BigDecimal maxDiscount, Integer allowCombine, String validityType, String validFrom,
                         String validTo, String saleStartTime, String saleEndTime, Integer scopeType, List<String> storeNames) {
        public Fields(String name, String type, String description, BigDecimal salePrice, BigDecimal minSpend,
                      BigDecimal deductAmount, BigDecimal discountAmount, BigDecimal discountRate,
                      List<DraftItem> contentItems, Integer validDays, Integer perCustomerLimit) {
            this(name, type, description, salePrice, minSpend, deductAmount, discountAmount, discountRate,
                contentItems, validDays, perCustomerLimit, null, null, null, null, null, null, null, null, null);
        }
        public Fields(String name, String type, String description, BigDecimal salePrice, BigDecimal minSpend,
                      BigDecimal deductAmount, BigDecimal discountAmount, BigDecimal discountRate) {
            this(name, type, description, salePrice, minSpend, deductAmount, discountAmount, discountRate, null, null, null);
        }
    }
    public record DraftItem(String name, String type, Integer quantity, String itemDescription) {}
    public record StockEdit(String basis, BigDecimal quantity) {}
    public record PriceFilter(String mode, BigDecimal min, BigDecimal max, Boolean minInclusive, Boolean maxInclusive) {}
    public record FilterRemoval(String field, String quote) {}
    public record QuerySpec(String contextMode, PriceFilter price, List<String> clear, String groupBy,
                            List<String> unsupportedConditions, List<String> metrics, Integer relativeDays, List<FilterRemoval> clearEvidence) {
        public QuerySpec(String contextMode, PriceFilter price, List<String> clear, String groupBy,
                         List<String> unsupportedConditions, List<String> metrics, Integer relativeDays) {
            this(contextMode, price, clear, groupBy, unsupportedConditions, metrics, relativeDays, null);
        }
        public QuerySpec(String contextMode, PriceFilter price, List<String> clear, String groupBy,
                         List<String> unsupportedConditions, List<String> metrics) {
            this(contextMode, price, clear, groupBy, unsupportedConditions, metrics, null);
        }
        public QuerySpec(String contextMode, PriceFilter price, List<String> clear, String groupBy, List<String> unsupportedConditions) {
            this(contextMode, price, clear, groupBy, unsupportedConditions, null);
        }
    }
    public record Revision(List<String> removeFields, String submitAction) {}
    public record GroupCount(String label, long count) {}
    public record CountResult(long matched, long baseTotal, BigDecimal percentage, String priceCondition,
                              String scope, List<GroupCount> groups) {}
    public record Intent(String action, String question, String couponName, String type, String startDay, String endDay,
                         Fields fields, Integer status, Integer auditStatus, Integer publishStatus, String reason, Boolean batch,
                         String merchantName, String storeName, StockEdit stock, List<String> requestedFields, QuerySpec query, Revision revision) {
        public Intent(String action, String question, String couponName, String type, String startDay, String endDay,
                      Fields fields, Integer status, Integer auditStatus, Integer publishStatus, String reason, Boolean batch,
                      String merchantName, String storeName, StockEdit stock, List<String> requestedFields, QuerySpec query) {
            this(action, question, couponName, type, startDay, endDay, fields, status, auditStatus, publishStatus, reason,
                batch, merchantName, storeName, stock, requestedFields, query, null);
        }
        public Intent(String action, String question, String couponName, String type, String startDay, String endDay,
                      Fields fields, Integer status, Integer auditStatus, Integer publishStatus, String reason, Boolean batch,
                      String merchantName, String storeName, StockEdit stock, List<String> requestedFields) {
            this(action, question, couponName, type, startDay, endDay, fields, status, auditStatus, publishStatus, reason,
                batch, merchantName, storeName, stock, requestedFields, null);
        }
        public Intent(String action, String question, String couponName, String type, String startDay, String endDay,
                      Fields fields, Integer status, Integer auditStatus, Integer publishStatus, String reason, Boolean batch,
                      String merchantName, String storeName, StockEdit stock) {
            this(action, question, couponName, type, startDay, endDay, fields, status, auditStatus, publishStatus, reason, batch,
                merchantName, storeName, stock, null);
        }
        public Intent(String action, String question, String couponName, String type, String startDay, String endDay,
                      Fields fields, Integer status, Integer auditStatus, Integer publishStatus, String reason, Boolean batch,
                      String merchantName, String storeName) {
            this(action, question, couponName, type, startDay, endDay, fields, status, auditStatus, publishStatus, reason, batch,
                merchantName, storeName, null);
        }
        public Intent(String action, String question, String couponName, String type, String startDay, String endDay, Fields fields) {
            this(action, question, couponName, type, startDay, endDay, fields, null, null, null, null, null, null, null);
        }
        Intent afterClarification(String name, StockEdit edit) {
            return afterClarification(name, edit, fields);
        }
        Intent afterClarification(String name, StockEdit edit, Fields values) {
            return new Intent(action, null, name, type, startDay, endDay, values, status, auditStatus, publishStatus, reason,
                batch, merchantName, storeName, edit, requestedFields, query);
        }
    }
    public record Candidate(Long id, String name, String merchantName, String type, String status, Date createdAt) {}
    public record Change(String field, Object before, Object after) {}
    public record BatchItem(String label, String token) {}
    public record Option(String value, String label) {}
    public record Answer(String token, String value) {}
    public record Clarification(String token, String type, String question, List<Option> options) {}
    public record Reply(String text, List<Candidate> choices, CouponTemplateEntity draft, List<Change> changes, String token,
                        CouponTemplateEntity detail, boolean searchResult, List<BatchItem> batch, String operation,
                        Clarification clarification, String queryToken, CountResult countResult) {
        public Reply(String text, List<Candidate> choices, CouponTemplateEntity draft, List<Change> changes, String token,
                     CouponTemplateEntity detail, boolean searchResult, List<BatchItem> batch, String operation,
                     Clarification clarification) {
            this(text, choices, draft, changes, token, detail, searchResult, batch, operation, clarification, null, null);
        }
        public Reply(String text, List<Candidate> choices, CouponTemplateEntity draft, List<Change> changes, String token,
                     CouponTemplateEntity detail, boolean searchResult, List<BatchItem> batch, String operation) {
            this(text, choices, draft, changes, token, detail, searchResult, batch, operation, null);
        }
        public Reply(String text, List<Candidate> choices, CouponTemplateEntity draft, List<Change> changes, String token) {
            this(text, choices, draft, changes, token, null, false, List.of(), null);
        }
    }
    public record Pending(Long id, Fields fields, String action, String reason, String fingerprint, long expires,
                          StockEdit stock, Long merchantId, Long storeId, Intent intent, Selection selection,
                          List<String> prerequisites) {}
    public record Selection(Long merchantId, Long storeId, Long couponId, List<Long> ids) {}
    private record FollowUp(String token, Selection selection, Long target, Intent intent, String type, String question,
                            List<Candidate> candidates, long expires, String fieldKey) {}
    public Reply chat(Long actor, boolean platform, Chat input) {
        require(actor, platform, "view");
        if(input==null || input.text()==null || input.text().isBlank() || input.text().length()>1500)
            throw new CoolException("请输入1至1500字的需求");
        if(featureControl!=null)featureControl.consume("admin",actor);
        return chatWithinAdmittedRequest(actor, platform, input);
    }

    /** 仅供已通过统一工作台计额的内部桥接复用，避免一次对话重复扣额。 */
    public Reply chatWithinAdmittedRequest(Long actor, boolean platform, Chat input) {
        require(actor, platform, "view");
        if(featureControl!=null)featureControl.check("admin");
        Reply result = processChat(actor, platform, input);
        if (result.text() == null || result.text().isBlank()) return result;
        String text;
        try { text = narrate(input, result); }
        catch (CoolException unavailable) {
            boolean draftReady = result.draft() != null && unavailable.getCode() == 503;
            boolean navigationReady = result.detail() != null && Set.of("编辑封面", "编辑表单").contains(Objects.toString(result.operation(), ""));
            if (!draftReady && !navigationReady) throw unavailable;
            text = ""; // Preserve authorized structured output; the UI labels unavailable model prose explicitly.
        }
        return new Reply(text, result.choices(), result.draft(), result.changes(), result.token(), result.detail(),
            result.searchResult(), result.batch(), result.operation(), result.clarification(), result.queryToken(), result.countResult());
    }

    private Reply processChat(Long actor, boolean platform, Chat input) {
        require(actor, platform, "view");
        if (input == null) throw new CoolException("请输入优惠券需求");
        if (input.text() == null || input.text().isBlank() || input.text().length() > 1500) throw new CoolException("请输入1至1500字的需求");
        if (Set.of("先别改", "先不改了", "取消", "取消操作", "算了", "先别提交").contains(input.text().strip())) {
            invalidatePreviews(actor, platform);
            cache.del(clarificationKey(actor, platform));
            return say("好的，这次待办已取消。");
        }
        if (input.planToken() != null) return revisePreview(actor, platform, input);
        if (input.answer() != null) return resume(actor, platform, input);
        if (cache.get(clarificationKey(actor, platform)) != null) {
            var pending = readFollowUp(clarificationKey(actor, platform));
            if ("MERCHANT".equals(pending.type()) && "CREATE".equals(pending.intent().action()) && input.merchantId() != null)
                return resume(actor, platform, new Chat(input.text(), input.history(), input.merchantId(), input.storeId(),
                    input.couponId(), input.selectedIds(), new Answer(pending.token(), null)));
        }
        if ("就这张".equals(input.text().strip()) && cache.get(clarificationKey(actor, platform)) != null) {
            var pending = readFollowUp(clarificationKey(actor, platform));
            return resume(actor, platform, new Chat(input.text(), input.history(), input.merchantId(), input.storeId(),
                input.couponId(), input.selectedIds(), new Answer(pending.token(), null)));
        }
        invalidatePreviews(actor, platform);
        cache.del(clarificationKey(actor, platform));
        return handle(actor, platform, input, null);
    }

    private void invalidatePreviews(Long actor, boolean platform) {
        String index = key(actor, platform, "previews");
        Object raw = cache.get(index);
        if (raw instanceof String encoded) {
            try {
                for (String token : json.readValue(encoded, String[].class)) cache.del(key(actor, platform, token));
            } catch (java.io.IOException invalid) { throw new CoolException("待办记录无效，请重试"); }
        }
        cache.del(index);
    }

    private Reply revisePreview(Long actor, boolean platform, Chat input) {
        Pending pending;
        try {
            Object raw = cache.get(key(actor, platform, input.planToken()));
            if (!(raw instanceof String encoded)) throw new CoolException("原预览已失效，请重新描述操作");
            pending = json.readValue(encoded, Pending.class);
        } catch (java.io.IOException invalid) { throw new CoolException("原预览无效，请重新描述操作"); }
        if (pending.expires() < System.currentTimeMillis() || pending.intent() == null
            || !selection(input).equals(pending.selection())) throw new CoolException("目标或范围已变化，请重新描述操作");
        invalidatePreviews(actor, platform);
        if (!Set.of("UPDATE", UPDATE_AND_SUBMIT).contains(pending.action())) return say("原预览已撤销，请说明接下来要做什么。");
        var intent = pending.intent();
        String text = input.text().strip().replaceFirst("^(改成|改为|调整到|设为)", "").strip();
        var values = fieldValues(pending.fields());
        var filled = EDIT_FIELDS.stream().filter(field -> values.hasNonNull(field.key())).toList();
        boolean numeric = text.matches("[+-]?[0-9]{1,19}(?:[.][0-9]{1,12})?[张元天]?$");
        if (pending.stock() != null && filled.isEmpty() && numeric) {
            BigDecimal quantity;
            try { quantity = new BigDecimal(text.replaceFirst("张$", "").strip()); }
            catch (NumberFormatException invalid) { return clarify(actor, platform, input, pending.id(), intent,
                "NUMBER", "原预览已撤销。库存要改成多少张？", List.of()); }
            intent = intent.afterClarification(intent.couponName(), new StockEdit(pending.stock().basis(), quantity));
        } else if (pending.stock() == null && filled.size() == 1 && numeric) {
            var field = filled.get(0);
            var follow = new FollowUp("", selection(input), pending.id(), intent, field.kind(), field.question(), List.of(), 0, field.key());
            intent = intent.afterClarification(intent.couponName(), null, answerEditField(input, follow, text));
        } else {
            var revisionHistory = new ArrayList<Turn>();
            if (input.history() != null) revisionHistory.addAll(input.history().stream().skip(Math.max(0, input.history().size() - 14)).toList());
            revisionHistory.add(new Turn("assistant", "待确认计划（尚未执行），用户可以新增、纠正、撤销部分字段：" + encode(pending.intent())));
            var delta = interpret(new Chat(input.text(), revisionHistory, input.merchantId(), input.storeId(), pending.id(), input.selectedIds()));
            String problem = context.queryProblem(delta);
            if (problem != null) return say("原预览已撤销。" + problem);
            if ("CANCEL".equals(delta.action())) return say("好的，这次待办已取消。");
            if (!Set.of("UPDATE", UPDATE_AND_SUBMIT).contains(Objects.toString(delta.action(), "")))
                return handle(actor, platform, input, delta);
            var target = templates.selectOneById(pending.id());
            if (target == null) return say("原预览已撤销，目标优惠券已不存在，请重新选择。");
            if (delta.couponName() != null && !delta.couponName().equals(target.getName()))
                return say("原预览已撤销。你提到了另一张券，请先明确选择要操作的券，再说明修改内容。");
            var removed = delta.revision() == null || delta.revision().removeFields() == null ? List.<String>of() : delta.revision().removeFields();
            for (String field : removed) {
                if (!"stock".equals(field)) editField(field);
                values.remove(field);
            }
            var updateValues = fieldValues(delta.fields());
            for (var field : EDIT_FIELDS) if (updateValues.hasNonNull(field.key())) values.set(field.key(), updateValues.get(field.key()));
            Fields merged;
            try { merged = json.treeToValue(values, Fields.class); }
            catch (java.io.IOException invalid) { throw new CoolException("修改内容无效，请重新说明"); }
            var requested = new LinkedHashSet<String>();
            if (intent.requestedFields() != null) requested.addAll(intent.requestedFields());
            requested.removeAll(removed);
            if (delta.requestedFields() != null) requested.addAll(delta.requestedFields());
            requested.removeAll(removed);
            StockEdit stock = delta.stock() != null ? delta.stock() : removed.contains("stock") ? null : pending.stock();
            boolean submit = UPDATE_AND_SUBMIT.equals(intent.action()) || UPDATE_AND_SUBMIT.equals(delta.action());
            if (delta.revision() != null && delta.revision().submitAction() != null) {
                submit = switch (delta.revision().submitAction()) {
                    case "KEEP" -> submit; case "ADD" -> true; case "REMOVE" -> false;
                    default -> throw new CoolException("送审步骤不明确，请说明保留、增加或取消送审");
                };
            }
            intent = new Intent(submit ? UPDATE_AND_SUBMIT : "UPDATE", null, target.getName(), null, null, null, merged,
                null, null, null, null, false, null, null, stock, new ArrayList<>(requested));
        }
        return handle(actor, platform, input, intent, pending.id(), pending.prerequisites() == null ? List.of() : pending.prerequisites(), 0);
    }

    public Reply detail(Long actor, boolean platform, Chat input) {
        if (input == null || input.couponId() == null) throw new CoolException("请选择优惠券");
        return handle(actor, platform, new Chat("查看优惠券详情", List.of(), input.merchantId(), input.storeId(), input.couponId()),
            new Intent("DETAIL", null, null, null, null, null, null));
    }

    // Called by the controller only after confirm's transaction has rolled back.
    public Reply recoverConfirmation(Long actor, boolean platform, String token, CoolException failure) {
        if (failure.getCode() == 401 || failure.getCode() == 403) throw failure;
        Object raw = cache.get(key(actor, platform, token));
        if (!(raw instanceof String encoded)) throw failure;
        Pending pending;
        try { pending = json.readValue(encoded, Pending.class); }
        catch (java.io.IOException invalid) { throw failure; }
        if (pending.expires() < System.currentTimeMillis() || pending.intent() == null
            || !Set.of("UPDATE", UPDATE_AND_SUBMIT, "ENABLE", "DISABLE").contains(pending.action())) throw failure;
        require(actor, platform, "view");
        var selection = pending.selection();
        var input = new Chat("继续原任务，但刚才的确认未执行：" + failure.getMessage(), List.of(), selection.merchantId(),
            selection.storeId(), selection.couponId(), selection.ids());
        invalidatePreviews(actor, platform);
        var result = handle(actor, platform, input, pending.intent(), pending.id());
        return new Reply(narrate(input, result), result.choices(), result.draft(), result.changes(), result.token(), result.detail(),
            result.searchResult(), result.batch(), result.operation(), result.clarification(), result.queryToken(), result.countResult());
    }

    private Reply handle(Long actor, boolean platform, Chat input, Intent fixed) {
        return handle(actor, platform, input, fixed, null);
    }

    private Reply handle(Long actor, boolean platform, Chat input, Intent fixed, Long resolvedTarget) {
        return handle(actor, platform, input, fixed, resolvedTarget, List.of(), 0);
    }

    private Reply handle(Long actor, boolean platform, Chat input, Intent fixed, Long resolvedTarget,
                         List<String> prerequisites, int repairDepth) {
        require(actor, platform, "view");
        if (input == null || input.text() == null || input.text().isBlank() || input.text().length() > 1500)
            throw new CoolException("请输入1至1500字的需求");
        var account = platform ? null : auth.user(actor);
        Long merchantId = account == null ? input.merchantId() : account.getMerchantId();
        if (account != null && input.merchantId() != null && !Objects.equals(input.merchantId(), merchantId))
            throw new CoolException("无法查询其他商家", 403);
        if (account != null && input.storeId() != null && !auth.storeIds(account).contains(input.storeId()))
            throw new CoolException("门店不在授权范围", 403);
        if (input.storeId() != null) {
            var branch = merchantStores.selectOneById(input.storeId());
            if (branch == null || merchantId != null && !Objects.equals(branch.getMerchantId(), merchantId))
                throw new CoolException("门店不在当前商家范围", 403);
            merchantId = branch.getMerchantId();
        }
        Intent parsed;
        try { parsed = fixed == null ? interpret(context.withSelectionContext(account, merchantId, input, context.withQueryContext(actor, platform, input))) : fixed; }
        catch (CoolException unavailable) {
            if (Set.of("你好", "您好", "嗨", "你是谁", "你能做什么", "怎么用").contains(input.text().strip()))
                return say("你好，我是券管家的优惠券助手。可以帮你找券、看销量和核销情况，也可以起草或修改优惠券。你可以先选中一张券，再告诉我想改什么；修改会先给你核对。");
            throw unavailable;
        }
        // A structured-output model may emit an optional stock object even for unrelated edits.
        var cleanIntent = fixed == null && parsed.stock() != null && !STOCK_WORDS.matcher(input.text()).find()
            ? parsed.afterClarification(parsed.couponName(), null) : parsed;
        var intent = context.resolveQuery(actor, platform, input, cleanIntent);
        String queryProblem = context.queryProblem(intent);
        if (queryProblem != null) return say(queryProblem);
        Long storeId = input.storeId();
        if (intent.merchantName() != null && !intent.merchantName().isBlank()) {
            var names = QueryWrapper.create().eq(com.cool.modules.merchant.entity.MerchantInfoEntity::getName, intent.merchantName().strip());
            if (merchantId != null) names.eq(com.cool.modules.merchant.entity.MerchantInfoEntity::getId, merchantId);
            var matches = merchants.selectListByQuery(names.limit(2));
            if (matches.size() != 1) {
                if ("CREATE".equals(intent.action())) {
                    require(actor, platform, "create");
                    var search = QueryWrapper.create().like(com.cool.modules.merchant.entity.MerchantInfoEntity::getName, intent.merchantName().strip());
                    if (merchantId != null) search.eq(com.cool.modules.merchant.entity.MerchantInfoEntity::getId, merchantId);
                    var choices = merchants.selectListByQuery(search.limit(20)).stream()
                        .map(m -> new Candidate(m.getId(), m.getName(), m.getName(), "MERCHANT", "商家", null)).toList();
                    return clarify(actor, platform, input, null, intent, "MERCHANT",
                        choices.isEmpty() ? "没有找到这个名称的商家。请在上方选择所属商家后告诉我，原草稿需求会保留。" : "请确认以下哪一家是你要创建优惠券的商家，原草稿需求已保留。", choices);
                }
                return say("当前范围无法唯一确定该商家，请使用商家筛选。");
            }
            merchantId = matches.get(0).getId();
        }
        if (intent.storeName() != null && !intent.storeName().isBlank()) {
            var names = QueryWrapper.create().eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getName, intent.storeName().strip());
            if (merchantId != null) names.eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getMerchantId, merchantId);
            if (storeId != null) names.eq(com.cool.modules.merchant.entity.MerchantStoreEntity::getId, storeId);
            if (account != null) {
                var authorized = auth.storeIds(account);
                names.in(com.cool.modules.merchant.entity.MerchantStoreEntity::getId, authorized.isEmpty() ? List.of(-1L) : authorized);
            }
            var matches = merchantStores.selectListByQuery(names.limit(2));
            if (matches.size() != 1) return say("当前范围无法唯一确定该门店，请使用门店筛选。");
            storeId = matches.get(0).getId();
            merchantId = matches.get(0).getMerchantId();
        }
        String action = Objects.toString(intent.action(), "ASK");
        if ("CHAT".equals(action)) return say(intent.question() == null || intent.question().isBlank()
            ? "我是券管家的优惠券助手。你可以让我找券、查看销量，或选中一张券后告诉我要修改什么。修改会先给你核对。"
            : intent.question());
        if ("CANCEL".equals(action)) {
            invalidatePreviews(actor, platform);
            cache.del(clarificationKey(actor, platform));
            return say("好的，这次待办已取消。");
        }
        if ("ASK".equals(action)) return say(Objects.toString(intent.question(), "请补充优惠券名称及操作。"));
        if ("CREATE".equals(action)) {
            require(actor, platform, "create");
            if (merchantId == null) return clarify(actor, platform, input, null, intent, "MERCHANT", "请在上方选择所属商家，再告诉我继续，原草稿需求已保留。", List.of());
            var draftIntent = intent;
            Chat planningInput = input;
            String blocker = "请补充创建优惠券的具体要求。";
            for (int attempt = 0; attempt < 2; attempt++) {
                boolean suggested = false;
                try {
                    // Delegation can concern any draft field, even when name/type/price already exist.
                    // The planner determines whether the user authorized suggestions; absent authorization it returns none.
                    var suggestion = proposeDraft(planningInput, draftIntent);
                    if (suggestion != null && suggestion.authorized() && suggestion.fields() != null) {
                        draftIntent = mergeIntent(mergeIntent(intent, suggestion.fields(), null), intent.fields(), null);
                        suggested = true;
                    }
                    var result = draft(account, merchantId, storeId, draftIntent.fields());
                    if (result.draft() != null) {
                        if (suggested) coupons.validatePreview(result.draft());
                        return result;
                    }
                    blocker = result.text();
                } catch (CoolException blocked) {
                    if (blocked.getCode() == 401 || blocked.getCode() == 403) throw blocked;
                    blocker = blocked.getMessage();
                }
                if (!suggested) break;
                var history = new ArrayList<Turn>(input.history() == null ? List.of() : input.history());
                history.add(new Turn("assistant", "上一份建议草稿未保存，业务校验结果：" + blocker));
                planningInput = new Chat(input.text(), history, input.merchantId(), input.storeId(), input.couponId(), input.selectedIds());
            }
            return clarify(actor, platform, input, null, intent, "CREATE", "草稿尚未保存：" + blocker + "。请补充或调整要求。", List.of());
        }
        if ("REPORT".equals(action)) return report(actor, platform, input, intent);
        var query = queries.visible(account, merchantId, storeId);
        if (Set.of("FIND", "COUNT", "RANK_SALES", "RANK_REDEEM", "SALES", "REDEEM", "DETAIL", "COMPARE_SALES", "COMPARE_REDEEM").contains(action)) {
            queries.filters(query, merchantId, intent);
            if (!"COUNT".equals(action)) queries.priceFilter(query, intent.query() == null ? null : intent.query().price());
        }
        if ("FIND".equals(action)) {
            var found = templates.selectListByQuery(query.orderBy(CouponTemplateEntity::getId, false).limit(31));
            String summary = found.size() > 30 ? "显示最近创建的30款，请缩小筛选范围查看其余结果。" : "找到 " + found.size() + " 款优惠券。";
            var conditions = new ArrayList<String>();
            conditions.add(queries.scopeLabel(merchantId, storeId));
            if (intent.couponName() != null && !intent.couponName().isBlank()) conditions.add("券名含「" + intent.couponName().strip() + "」");
            if (intent.type() != null) conditions.add(queries.typeName(intent.type()));
            if (intent.status() != null) conditions.add(queries.statusName(intent.status()));
            if (intent.auditStatus() != null) conditions.add(List.of("未提交", "待审核", "审核通过", "已驳回").get(intent.auditStatus()));
            if (intent.publishStatus() != null) conditions.add(List.of("未上架", "已上架", "已下架").get(intent.publishStatus()));
            if (intent.query() != null && intent.query().price() != null) conditions.add(queries.priceLabel(intent.query().price()));
            summary += "筛选条件为" + String.join("、", conditions) + "。";
            return new Reply(summary, found.stream().limit(30).map(queries::candidate).toList(), null, List.of(), null, null, true,
                List.of(), null, null, context.rememberQuery(actor, platform, input, intent), null);
        }
        if ("COUNT".equals(action)) {
            return queries.countCoupons(intent, query, merchantId, storeId, context.rememberQuery(actor, platform, input, intent));
        }
        if (Set.of("RANK_SALES", "RANK_REDEEM", "COMPARE_SALES", "COMPARE_REDEEM").contains(action)) {
            var result = queries.ranking(actor, platform, account,
                new Chat(input.text(), input.history(), merchantId, storeId, input.couponId(), input.selectedIds()), action, intent, query);
            return new Reply(result.text(), result.choices(), result.draft(), result.changes(), result.token(), result.detail(),
                result.searchResult(), result.batch(), result.operation(), result.clarification(),
                QUERY_ACTIONS.contains(action) ? context.rememberQuery(actor, platform, input, intent) : null, result.countResult());
        }
        if ("DELETE".equals(action) && Boolean.TRUE.equals(intent.batch())) {
            require(actor, platform, "delete");
            var ids = selected(input);
            if (ids.isEmpty()) return say("请在券列表勾选要删除的优惠券。");
            var rows = templates.selectListByQuery(query.in(CouponTemplateEntity::getId, ids));
            if (rows.size() != ids.size()) throw new CoolException("部分券不存在或超出当前授权范围", 403);
            var operations = rows.stream().map(row -> {
                var target = (CouponTemplateEntity) coupons.info(row.getId());
                return new BatchItem(queries.label(target), token(actor, platform, target, intent, null, null, input));
            }).toList();
            return new Reply("确认后逐条删除 " + operations.size() + " 款已选优惠券。已有发放、发布或关联记录的券可能被拦截。",
                List.of(), null, List.of(), null, null, false, operations, "批量删除");
        }
        if (!Set.of("SALES", "REDEEM", "DETAIL", "EDIT_FORM", "EDIT_COVER", "UPDATE", UPDATE_AND_SUBMIT, "DELETE", "ENABLE", "DISABLE", "SUBMIT", "WITHDRAW", "PUBLISH", "UNPUBLISH", "APPROVE", "REJECT").contains(action))
            return say("请说明要查询或操作的优惠券。");
        boolean updating = "UPDATE".equals(action) || UPDATE_AND_SUBMIT.equals(action);
        if (updating) requireOperation(actor, platform, action);
        var ids = selected(input);
        boolean named = intent.couponName() != null && !intent.couponName().isBlank();
        if (updating && input.queryToken() != null && input.couponId() == null && ids.isEmpty() && !named && resolvedTarget == null)
            return say("你是调整刚才的筛选条件，还是修改具体优惠券？如果要改券，请先选择目标。");
        if (resolvedTarget != null) query.eq(CouponTemplateEntity::getId, resolvedTarget);
        else if (named) {
            query.eq(CouponTemplateEntity::getName, intent.couponName().strip());
            if (!updating && input.couponId() != null) query.eq(CouponTemplateEntity::getId, input.couponId());
        } else if (input.couponId() != null) query.eq(CouponTemplateEntity::getId, input.couponId());
        else if (!ids.isEmpty()) query.in(CouponTemplateEntity::getId, ids);
        else if (updating) return clarify(actor, platform, input, null, intent, "TEXT", "请提供要修改的完整券名。", List.of());
        else return say("请提供完整券名，或选择列表中的优惠券。");
        var candidates = templates.selectListByQuery(query.orderBy(CouponTemplateEntity::getId, true).limit(30));
        if (updating && resolvedTarget == null && !named && input.couponId() == null && !ids.isEmpty()
            && candidates.size() != ids.size())
            throw new CoolException("部分选中券不存在或超出授权范围，请重新选择", 403);
        if (candidates.isEmpty()) return say("当前范围没有匹配的优惠券。请核对完整券名及筛选范围。");
        if (updating && candidates.size() > 1) return clarify(actor, platform, input, null, intent, "CANDIDATE",
            "请选择要修改的优惠券。", candidates.stream().map(queries::candidate).toList());
        if (candidates.size() > 1) return new Reply("找到同名券，请选择具体目标。",
            candidates.stream().map(queries::candidate).toList(),
            null, List.of(), null);
        var target = candidates.get(0);
        if (Set.of("EDIT_FORM", "EDIT_COVER").contains(action)) {
            require(actor, platform, "update");
            var detail = (CouponTemplateEntity) (platform ? coupons.info(target.getId()) : workspace.couponInfo(actor, target.getId()));
            return new Reply("可在原编辑表单处理" + queries.label(target) + ("EDIT_COVER".equals(action) ? "的封面，使用已有AI生图或素材库。" : "的完整规则。") + "核对后保存才会生效。",
                List.of(), null, List.of(), null, detail, false, List.of(), "EDIT_COVER".equals(action) ? "编辑封面" : "编辑表单");
        }
        if ("SALES".equals(action) || "REDEEM".equals(action)) return queries.statistics(actor, platform, account, target, storeId, action, intent);
        if ("DETAIL".equals(action)) {
            if (platform) access.platform("coupon:template:info");
            var detail = (CouponTemplateEntity) (platform ? coupons.info(target.getId()) : workspace.couponInfo(actor, target.getId()));
            return new Reply("已打开" + queries.label(target) + "的详情。", List.of(), null, List.of(), null, detail, false, List.of(), null);
        }
        requireOperation(actor, platform, action);
        var before = (CouponTemplateEntity) coupons.info(target.getId());
        try {
        var allowed = allowedPrerequisites(actor, platform, before, action);
        if (prerequisites.size() != new HashSet<>(prerequisites).size() || !allowed.containsAll(prerequisites))
            throw new CoolException("前置步骤已不适用或没有权限，请重新规划", 403);
        var changes = new ArrayList<Change>();
        for (String step : prerequisites) changes.add(new Change("前置步骤", step.equals("UNPUBLISH") ? "已上架" : "待审核",
            step.equals("UNPUBLISH") ? "先下架，修改后不会自动重新上架" : "先撤回审核，修改后需重新送审"));
        if ("DELETE".equals(action)) {
            checkDeletion(before);
            if (Integer.valueOf(1).equals(before.getStatus())) {
                requireOperation(actor, platform, "DISABLE_AND_DELETE");
                var disabled = copy(before);
                disabled.setStatus(2);
                CouponTemplateRules.validate(disabled);
                var plan = new Intent("DISABLE_AND_DELETE", null, null, null, null, null, null);
                return new Reply("这张券当前为启用状态。确认后我会先禁用，再删除" + queries.label(before)
                    + "。成功后它会从列表移除；任一步失败，两步均不生效。",
                    List.of(), null, List.of(new Change("模板状态", "启用", "停用"), new Change("优惠券", before.getName(), "删除")),
                    token(actor, platform, before, plan, merchantId, storeId, input), null, false, List.of(), "禁用并删除");
            }
        }
        if (updating) {
            var missing = missingEditField(intent, before);
            if (missing != null)
                return clarify(actor, platform, input, target.getId(), intent,
                    Set.of("NUMBER", "INTEGER").contains(missing.kind()) ? "NUMBER" : "TEXT", missing.question(), List.of(), missing.key());
            StockEdit stock = intent.stock();
            if (stock != null && !Set.of(STOCK_REMAINING, STOCK_TOTAL).contains(Objects.toString(stock.basis(), "")))
                return clarify(actor, platform, input, target.getId(), intent, "SINGLE",
                    clarificationQuestion(intent, "这里的库存是指剩余可领数量，还是发行总量？"), List.of());
            if (stock != null && stock.quantity() == null)
                return clarify(actor, platform, input, target.getId(), intent, "NUMBER",
                    clarificationQuestion(intent, "请填写要设置的张数。"), List.of());
            var updated = copy(before);
            edits.apply(updated, intent.fields(), changes);
            edits.applyStock(updated, stock, changes);
            if (account != null) scope.couponInput(account, updated);
            if (!changes.isEmpty()) coupons.validatePreview(updated);
            if (coupons.requiresPublicationRelease(updated) && !prerequisites.containsAll(requiredRelease(before)))
                throw new CoolException("此券正在审核或已上架，修改规则前需要先撤回审核或下架；修改后不会自动恢复审核或上架");
            if (changes.isEmpty() && stock == null && (intent.requestedFields() == null || intent.requestedFields().isEmpty()))
                return say("请说明要修改的字段和新值。");
            if (changes.isEmpty() && !UPDATE_AND_SUBMIT.equals(action)) return say("所选字段已经是指定值。");
            if (UPDATE_AND_SUBMIT.equals(action)) {
                if (!Integer.valueOf(1).equals(before.getStatus())) throw new CoolException("请先启用优惠券模板，再修改并提交审核");
                if (prerequisites.isEmpty()) checkSubmitState(before);
                changes.add(new Change("审核状态", publicationLabel(before, "auditStatus", List.of("未提交", "待审核", "审核通过", "已驳回")), "待审核"));
            }
        }
        if ("ENABLE".equals(action) || "DISABLE".equals(action))
            changes.add(new Change("模板状态", queries.statusName(before.getStatus()), "ENABLE".equals(action) ? "启用" : "停用"));
        if (Set.of("ENABLE", "DISABLE").contains(action)) {
            var updated = copy(before);
            edits.apply(updated, intent.fields(), changes);
            edits.applyStock(updated, intent.stock(), changes);
            updated.setStatus("ENABLE".equals(action) ? 1 : 2);
            coupons.validatePreview(updated);
            if (coupons.requiresPublicationRelease(updated) && !prerequisites.containsAll(requiredRelease(before)))
                throw new CoolException("当前发布状态不允许停用，需要先撤回审核或下架");
        }
        if (Set.of("SUBMIT", "WITHDRAW", "APPROVE", "REJECT").contains(action)) {
            String next = switch (action) {
                case "SUBMIT" -> "待审核"; case "WITHDRAW" -> "未提交"; case "APPROVE" -> "审核通过"; default -> "已驳回";
            };
            changes.add(new Change("审核状态", publicationLabel(before, "auditStatus", List.of("未提交", "待审核", "审核通过", "已驳回")), next));
        }
        if (Set.of("PUBLISH", "UNPUBLISH").contains(action))
            changes.add(new Change("上架状态", publicationLabel(before, "publishStatus", List.of("未上架", "已上架", "已下架")),
                "PUBLISH".equals(action) ? "已上架" : "已下架"));
        if ("REJECT".equals(action) && (intent.reason() == null || intent.reason().isBlank())) return say("请填写驳回原因。");
        if (intent.reason() != null && intent.reason().length() > 500) return say("审核意见不能超过500字。");
        if (intent.reason() != null && Set.of("APPROVE", "REJECT").contains(action))
            changes.add(new Change("审核意见", null, intent.reason()));
        String operation = operationName(action);
        String explanation = UPDATE_AND_SUBMIT.equals(action) && changes.size() == 1
            ? (intent.stock() != null ? "库存已经是指定值" : "所选字段已经是指定值") + "，确认后将直接提交当前版本。"
            : impact(action, before);
        String previewText = UPDATE_AND_SUBMIT.equals(action)
            ? "我会为" + queries.label(target) + "调整" + changes.stream().filter(change -> !"审核状态".equals(change.field()))
                .map(change -> change.field() + "为 " + change.after()).collect(java.util.stream.Collectors.joining("，"))
                + "，然后提交审核。请核对下面的内容，确认后再执行。"
            : "确认" + operation + queries.label(target) + "？" + explanation;
        if (UPDATE_AND_SUBMIT.equals(action) && changes.size() == 1) previewText = explanation;
        return new Reply(previewText,
            List.of(), null, changes, token(actor, platform, before, intent, merchantId, storeId, input, prerequisites), null, false, List.of(), operation);
        } catch (CoolException blocked) {
            if (blocked.getCode() == 401 || blocked.getCode() == 403 || !Set.of("UPDATE", UPDATE_AND_SUBMIT, "ENABLE", "DISABLE").contains(action)) throw blocked;
            return repair(actor, platform, input, intent, before, blocked.getMessage(), repairDepth);
        }
    }

    private List<String> requiredRelease(CouponTemplateEntity row) {
        var steps = new ArrayList<String>();
        if ("已上架".equals(publicationLabel(row, "publishStatus", List.of("未上架", "已上架", "已下架")))) steps.add("UNPUBLISH");
        if ("待审核".equals(publicationLabel(row, "auditStatus", List.of("未提交", "待审核", "审核通过", "已驳回")))) steps.add("WITHDRAW");
        return steps;
    }

    private List<String> allowedPrerequisites(Long actor, boolean platform, CouponTemplateEntity row, String action) {
        if (!Set.of("UPDATE", UPDATE_AND_SUBMIT, "DISABLE").contains(action)) return List.of();
        return requiredRelease(row).stream().filter(step -> {
            try { requireOperation(actor, platform, step); return true; }
            catch (CoolException denied) { return false; }
        }).toList();
    }

    public CouponAssistantRecovery.Proposal proposeRepair(Chat input, Intent original, CouponTemplateEntity current,
                                                          String blocker, List<String> allowed) {
        return recovery.propose(input, original, current, blocker, allowed);
    }

    public CouponAssistantRecovery.DraftSuggestion proposeDraft(Chat input, Intent known) { return recovery.draft(input, known); }

    private Reply repair(Long actor, boolean platform, Chat input, Intent original, CouponTemplateEntity current,
                         String blocker, int depth) {
        String question = "当前操作未执行：" + blocker + "。你希望如何调整原要求？也可以取消或改做其他任务。";
        if (depth == 0) {
            CouponAssistantRecovery.Proposal proposal = null;
            try { proposal = proposeRepair(input, original, current, blocker, allowedPrerequisites(actor, platform, current, original.action())); }
            catch (CoolException unavailable) { /* Keep the original task available for the user's next turn. */ }
            if (proposal != null) {
                var steps = proposal.prerequisites() == null ? List.<String>of() : proposal.prerequisites();
                if (proposal.fields() != null || proposal.stock() != null || !steps.isEmpty()) {
                    var amended = mergeIntent(original, proposal.fields(), original.stock() == null ? null : proposal.stock());
                    var result = handle(actor, platform, input, amended, current.getId(), steps, 1);
                    if (result.clarification() != null)
                        return clarify(actor, platform, input, current.getId(), original, "REPAIR", result.clarification().question(), List.of());
                    return new Reply("原要求尚未执行，遇到的限制是：" + blocker + "。以下为需要你重新确认的调整方案。原要求："
                        + input.text() + "。" + result.text(), result.choices(), result.draft(), result.changes(), result.token(), result.detail(),
                        result.searchResult(), result.batch(), result.operation(), result.clarification(), result.queryToken(), result.countResult());
                }
                if (proposal.question() != null && !proposal.question().isBlank()) question = "当前操作未执行：" + blocker + "。" + proposal.question();
            }
        }
        return clarify(actor, platform, input, current.getId(), original, "REPAIR", question, List.of());
    }

    private Intent mergeIntent(Intent original, Fields delta, StockEdit stock) {
        var values = fieldValues(original.fields());
        var updates = fieldValues(delta);
        for (var field : EDIT_FIELDS) if (updates.hasNonNull(field.key())) values.set(field.key(), updates.get(field.key()));
        if ("CREATE".equals(original.action()) && updates.hasNonNull("type")) values.set("type", updates.get("type"));
        // A mode change replaces the other mode's pending values, rather than asking its old questions again.
        if (updates.hasNonNull("validDays") || "AFTER_ISSUE".equals(updates.path("validityType").asText())) {
            values.put("validityType", "AFTER_ISSUE"); values.remove(List.of("validFrom", "validTo"));
        } else if (updates.hasNonNull("validFrom") || updates.hasNonNull("validTo") || "FIXED".equals(updates.path("validityType").asText())) {
            values.put("validityType", "FIXED"); values.remove("validDays");
        }
        try {
            var requested = new ArrayList<>(original.requestedFields() == null ? List.<String>of() : original.requestedFields());
            if ("AFTER_ISSUE".equals(values.path("validityType").asText())) requested.removeAll(List.of("validFrom", "validTo"));
            if ("FIXED".equals(values.path("validityType").asText())) requested.remove("validDays");
            var mergedStock = stock;
            if (stock != null && original.stock() != null) mergedStock = new StockEdit(
                stock.basis() == null || "UNSPECIFIED".equals(stock.basis()) ? original.stock().basis() : stock.basis(),
                stock.quantity() == null ? original.stock().quantity() : stock.quantity());
            return new Intent(original.action(), null, original.couponName(), original.type(), original.startDay(), original.endDay(),
                json.treeToValue(values, Fields.class), original.status(), original.auditStatus(), original.publishStatus(), original.reason(),
                original.batch(), original.merchantName(), original.storeName(), mergedStock == null ? original.stock() : mergedStock, requested, original.query(), original.revision());
        } catch (java.io.IOException invalid) { throw new CoolException("调整建议格式无效，请重新说明"); }
    }

    public static Selection selection(Chat input) {
        return new Selection(input.merchantId(), input.storeId(), input.couponId(), selected(input).stream().sorted().toList());
    }

    private String clarificationKey(Long actor, boolean platform) {
        return "ai:coupon:clarification:" + platform + ":" + actor;
    }

    private String clarificationQuestion(Intent intent, String fallback) {
        return intent.question() == null || intent.question().isBlank() ? fallback : intent.question().strip();
    }

    private Reply clarify(Long actor, boolean platform, Chat input, Long target, Intent intent, String type, String question,
                          List<Candidate> candidates) {
        return clarify(actor, platform, input, target, intent, type, question, candidates, null);
    }

    private Reply clarify(Long actor, boolean platform, Chat input, Long target, Intent intent, String type, String question,
                          List<Candidate> candidates, String fieldKey) {
        String context = target == null ? "" : "已选定" + queries.label((CouponTemplateEntity) coupons.info(target)) + "。";
        if (intent.stock() != null && intent.stock().quantity() != null)
            context += "你希望把库存调整到 " + intent.stock().quantity().stripTrailingZeros().toPlainString() + " 张。";
        if (UPDATE_AND_SUBMIT.equals(intent.action())) context += "修改后提交审核的要求也已记下。";
        String token = UUID.randomUUID().toString();
        cache.set(clarificationKey(actor, platform), encode(new FollowUp(token, selection(input), target, intent, type,
            question, candidates, System.currentTimeMillis() + 600_000, fieldKey)), 600);
        List<Option> options = "MERCHANT".equals(type) ? candidates.stream().map(c -> new Option(c.id().toString(), c.name())).toList() : "SINGLE".equals(type)
            ? List.of(new Option(STOCK_REMAINING, "剩余可领数量"), new Option(STOCK_TOTAL, "发行总量")) : List.of();
        return new Reply(context + question, "MERCHANT".equals(type) ? List.of() : candidates, null, List.of(), null, null, false, List.of(), null,
            new Clarification(token, type, question, options));
    }

    private Reply resume(Long actor, boolean platform, Chat input) {
        String key = clarificationKey(actor, platform);
        FollowUp plan = readFollowUp(key);
        if (!plan.token().equals(input.answer().token()) || plan.expires() < System.currentTimeMillis())
            throw new CoolException("此澄清已失效，请重新描述操作");
        boolean selectedCandidate = "CANDIDATE".equals(plan.type()) && "就这张".equals(input.text().strip())
            && Objects.equals(plan.selection().merchantId(), input.merchantId())
            && Objects.equals(plan.selection().storeId(), input.storeId());
        boolean selectedMerchant = "MERCHANT".equals(plan.type()) && "CREATE".equals(plan.intent().action()) && input.merchantId() != null;
        if (!plan.selection().equals(selection(input)) && !selectedCandidate && !selectedMerchant)
            throw new CoolException("优惠券选择或筛选范围已变化，请重新描述操作");
        String value = input.answer().value();
        String text = input.text() == null ? "" : input.text().strip();
        if ((value == null && text.isBlank()) || text.length() > 1500 || value != null && value.length() > 32)
            throw new CoolException("请填写有效的澄清答案");
        Intent intent = plan.intent();
        Long target = plan.target();
        String name = intent.couponName();
        StockEdit stock = intent.stock();
        Fields fields = intent.fields();
        if (selectedMerchant && value == null) {
            var next = interpret(new Chat(text, List.of(new Turn("assistant", "待继续的创建任务：" + encode(intent)
                + "。用户刚刚选择了商家，但仍可取消或切换任务。")), input.merchantId(), input.storeId(), input.couponId()));
            if (next.action() != null && !Set.of("ASK", "CREATE").contains(next.action())) {
                cache.del(key); invalidatePreviews(actor, platform); return handle(actor, platform, input, next);
            }
            if (next.fields() != null) intent = mergeIntent(intent, next.fields(), next.stock());
        }
        if ("MERCHANT".equals(plan.type()) && (selectedMerchant || value != null)) {
            Long chosen = selectedMerchant ? input.merchantId() : plan.candidates().stream()
                .filter(candidate -> candidate.id().toString().equals(value)).map(Candidate::id).findFirst()
                .orElseThrow(() -> new CoolException("请选择提供的商家"));
            var merchant = merchants.selectOneById(chosen);
            if (merchant == null) throw new CoolException("该商家已不存在，请重新选择");
            var resumed = new Intent(intent.action(), null, intent.couponName(), intent.type(), intent.startDay(), intent.endDay(), intent.fields(),
                intent.status(), intent.auditStatus(), intent.publishStatus(), intent.reason(), intent.batch(), merchant.getName(), intent.storeName(),
                intent.stock(), intent.requestedFields(), intent.query(), intent.revision());
            cache.del(key);
            return handle(actor, platform, new Chat(input.text(), input.history(), chosen, input.storeId(), input.couponId(), input.selectedIds()), resumed);
        }
        // Every free-text continuation is interpreted in the task context before treating it as a field value.
        if (value == null && !"CANDIDATE".equals(plan.type())) {
            var history = new ArrayList<Turn>();
            if (input.history() != null) history.addAll(input.history().stream().skip(Math.max(0, input.history().size() - 12)).toList());
            history.add(new Turn("assistant", "当前尚未执行的任务：" + encode(intent) + "。正在询问：" + plan.question()
                + "。用户可以补充、改口、取消或切换任务；优先判断本条最新意图，不得强行只提取原字段。"));
            Intent next = interpret(new Chat(text, history, input.merchantId(), input.storeId(), target));
            if ("CANCEL".equals(next.action())) {
                cache.del(key); invalidatePreviews(actor, platform); return say("已取消这次待办。");
            }
            if (next.action() != null && !next.action().equals(intent.action()) && !Set.of("ASK", "UPDATE", UPDATE_AND_SUBMIT).contains(next.action())) {
                cache.del(key); invalidatePreviews(actor, platform);
                return handle(actor, platform, input, next, next.couponName() == null ? target : null);
            }
            if (target != null && next.couponName() != null && !next.couponName().equals(templates.selectOneById(target).getName())) {
                cache.del(key); return handle(actor, platform, input, next);
            }
            var supplied = fieldValues(next.fields());
            var known = fieldValues(intent.fields());
            boolean changedFields = java.util.stream.StreamSupport.stream(((Iterable<java.util.Map.Entry<String, JsonNode>>) supplied::fields).spliterator(), false)
                .anyMatch(entry -> !entry.getValue().isNull() && !entry.getValue().equals(known.get(entry.getKey())));
            boolean literalStockAnswer = "SINGLE".equals(plan.type()) && (Set.of("剩余可领", "剩余可领数量", "发行总量", "总发行量").contains(text)
                || text.matches("(改成|改为)[0-9]+张?"));
            if (changedFields || !literalStockAnswer && next.stock() != null && !Objects.equals(next.stock(), intent.stock())) {
                var merged = mergeIntent(intent, next.fields(), next.stock());
                cache.del(key);
                return handle(actor, platform, input, merged, target);
            }
            if (Set.of("REPAIR", "CREATE", "MERCHANT").contains(plan.type()))
                return clarify(actor, platform, input, target, intent, plan.type(), plan.question(), plan.candidates());
        }
        if (plan.fieldKey() != null) {
            if (value != null) throw new CoolException("请在对话框填写答案");
            fields = answerEditField(input, plan, text);
        } else if ("CANDIDATE".equals(plan.type())) {
            Long selectedTarget = input.couponId() != null ? input.couponId()
                : selected(input).size() == 1 ? selected(input).get(0) : null;
            var matches = plan.candidates().stream().filter(candidate -> value != null
                ? candidate.id().toString().equals(value) : candidate.name().equals(text)
                || "就这张".equals(text) && Objects.equals(candidate.id(), selectedTarget)).toList();
            if (matches.size() != 1) throw new CoolException("请选择列表中的一款优惠券");
            target = matches.get(0).id();
        } else if ("TEXT".equals(plan.type())) {
            if (value != null || text.length() > 100) throw new CoolException("请输入完整券名");
            name = text;
        } else {
            String basis = stock.basis();
            BigDecimal quantity = stock.quantity();
            boolean quantityOnly = "SINGLE".equals(plan.type()) && text.matches("(改成|改为)[0-9]+张?");
            String explicitBasis = switch (text) {
                case "剩余可领", "剩余可领数量", "剩下能领的数量", "我是说剩下能领的数量" -> STOCK_REMAINING;
                case "发行总量", "总发行量", "总发行上限" -> STOCK_TOTAL;
                default -> null;
            };
            if (quantityOnly) {
                quantity = parseQuantity(text.replaceFirst("^(改成|改为)", "").replaceFirst("张$", ""));
            } else if (value != null) {
                if ("SINGLE".equals(plan.type())) basis = value;
                else quantity = parseQuantity(value);
            } else if ("SINGLE".equals(plan.type()) && explicitBasis != null) {
                basis = explicitBasis;
            } else if ("NUMBER".equals(plan.type()) && text.matches("[0-9]+")) {
                quantity = parseQuantity(text);
            } else {
                var answer = interpret(new Chat(text, List.of(new Turn("assistant", plan.question())), input.merchantId(),
                    input.storeId(), target));
                if (answer.stock() != null) {
                    if ("SINGLE".equals(plan.type())) basis = answer.stock().basis();
                    if (quantity == null) quantity = answer.stock().quantity();
                }
            }
            if (!quantityOnly && !Set.of(STOCK_TOTAL, STOCK_REMAINING).contains(Objects.toString(basis, "")))
                throw new CoolException("请选择剩余可领数量或发行总量");
            if ("NUMBER".equals(plan.type()) && quantity == null) throw new CoolException("请输入有效张数");
            stock = new StockEdit(basis, quantity);
        }
        synchronized (cache) {
            if (!readFollowUp(key).token().equals(plan.token())) throw new CoolException("此澄清已失效，请重新描述操作");
            var result = handle(actor, platform, input, intent.afterClarification(name, stock, fields), target);
            if (result.clarification() == null) cache.del(key);
            return result;
        }
    }

    private EditField editField(String key) {
        return EDIT_FIELDS.stream().filter(field -> field.key().equals(key)).findFirst()
            .orElseThrow(() -> new CoolException("包含助手暂不支持的修改字段，请核对请求"));
    }

    private EditField missingEditField(Intent intent, CouponTemplateEntity target) {
        var keys = new LinkedHashSet<String>();
        if (intent.requestedFields() != null) keys.addAll(intent.requestedFields());
        keys.remove("stock"); // Stock has its own basis/quantity clarification, not a template field.
        var values = fieldValues(intent.fields());
        values.fields().forEachRemaining(entry -> { if (!entry.getValue().isNull() && !"type".equals(entry.getKey())) keys.add(entry.getKey()); });
        var fields = intent.fields();
        if (fields != null) {
            boolean fixed = "FIXED".equals(fields.validityType()) || fields.validFrom() != null || fields.validTo() != null;
            if (fixed) {
                if (target.getValidFrom() == null) keys.add("validFrom");
                if (target.getValidTo() == null) keys.add("validTo");
            }
            if ("AFTER_ISSUE".equals(fields.validityType()) && target.getValidDays() == null) keys.add("validDays");
            if (Integer.valueOf(1).equals(fields.scopeType())) keys.add("storeNames");
        }
        var requested = keys.stream().map(this::editField).toList();
        for (var field : requested) {
            if (!field.types().isEmpty() && !field.types().contains(target.getType()))
                throw new CoolException("这张券不支持修改" + field.label());
        }
        return requested.stream().filter(field -> !values.hasNonNull(field.key())).findFirst().orElse(null);
    }

    private ObjectNode fieldValues(Fields fields) {
        return fields == null ? json.createObjectNode() : json.valueToTree(fields);
    }

    private Fields answerEditField(Chat input, FollowUp plan, String text) {
        var field = editField(plan.fieldKey());
        var values = fieldValues(plan.intent().fields());
        if ("TEXT".equals(field.kind())) {
            values.put(field.key(), text);
        } else if ("STRUCTURED".equals(field.kind())) {
            var parsed = interpret(new Chat(text,
                List.of(new Turn("assistant", plan.question() + " 当前字段为 " + field.key() + "，仅提取用户明确提供的字段值。")),
                input.merchantId(), input.storeId(), plan.target()));
            var supplied = fieldValues(parsed.fields()).get(field.key());
            if (supplied == null || supplied.isNull()) throw new CoolException("请补充有效的" + field.label());
            values.set(field.key(), supplied);
        } else {
            String number = text.replaceFirst("^(改成|改为|设为)", "").replaceFirst(java.util.regex.Pattern.quote(field.unit()) + "$", "").strip();
            JsonNode answer;
            if (number.matches("[+-]?[0-9]{1,19}(?:[.][0-9]{1,12})?")) {
                answer = json.valueToTree(new BigDecimal(number));
            } else {
                var parsed = interpret(new Chat(text,
                    List.of(new Turn("assistant", plan.question() + " 这是对字段 " + field.key() + " 的续答，仅提取该字段。")),
                    input.merchantId(), input.storeId(), plan.target()));
                answer = fieldValues(parsed.fields()).get(field.key());
            }
            if (answer == null || !answer.isNumber()) throw new CoolException("请填写有效的" + field.label());
            if ("INTEGER".equals(field.kind())) {
                try { values.put(field.key(), answer.decimalValue().intValueExact()); }
                catch (ArithmeticException invalid) { throw new CoolException(field.label() + "必须为有效整数"); }
            } else values.put(field.key(), answer.decimalValue());
        }
        try { return json.treeToValue(values, Fields.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new CoolException("澄清数据格式无效"); }
    }

    private FollowUp readFollowUp(String key) {
        if (cache.get(key) instanceof String value) {
            try { return json.readValue(value, FollowUp.class); }
            catch (Exception invalid) { throw new CoolException("澄清数据无效，请重新描述操作"); }
        }
        throw new CoolException("此澄清已失效，请重新描述操作");
    }

    private BigDecimal parseQuantity(String value) {
        if (value.matches("[0-9]{1,19}")) return new BigDecimal(value);
        throw new CoolException("张数必须为有效的非负整数");
    }

    @Transactional(rollbackFor = Exception.class)
    public String confirm(Long actor, boolean platform, String token) {
        if(featureControl!=null)featureControl.check("admin");
        if (token == null || !token.matches("[a-f0-9-]{36}")) throw new CoolException("请先生成操作预览");
        String key = key(actor, platform, token);
        Object raw = cache.get(key);
        if (!(raw instanceof String value)) throw new CoolException("确认已过期，请重新预览");
        Pending pending;
        try { pending = json.readValue(value, Pending.class); }
        catch (Exception invalid) { throw new CoolException("确认无效，请重新预览"); }
        if (pending.expires() < System.currentTimeMillis()) throw new CoolException("确认已过期，请重新预览");
        var locked = templates.selectOneByQuery(QueryWrapper.create().eq(CouponTemplateEntity::getId, pending.id()).forUpdate());
        if (locked == null || cache.get(key) == null) throw new CoolException("操作已执行或目标不存在，请重新预览");
        require(actor, platform, "view");
        requireOperation(actor, platform, pending.action());
        if (!platform) workspace.couponInfo(actor, pending.id());
        if (pending.merchantId() != null || pending.storeId() != null) {
            var account = platform ? null : auth.user(actor);
            if (pending.storeId() != null) {
                var branch = merchantStores.selectOneById(pending.storeId());
                if (branch == null || !Objects.equals(branch.getMerchantId(), pending.merchantId())
                    || account != null && !auth.storeIds(account).contains(pending.storeId()))
                    throw new CoolException("门店已不在当前授权范围，请重新预览", 403);
            }
            var query = queries.visible(account, pending.merchantId(), pending.storeId());
            if (templates.selectCountByQuery(query.eq(CouponTemplateEntity::getId, pending.id())) != 1)
                throw new CoolException("优惠券已不在当前授权范围，请重新预览", 403);
        }
        var current = (CouponTemplateEntity) coupons.info(pending.id());
        if (!pending.fingerprint().equals(fingerprint(current))) throw new CoolException("优惠券已变化，请重新预览");
        String action = pending.action();
        var prerequisites = pending.prerequisites() == null ? List.<String>of() : pending.prerequisites();
        if (!allowedPrerequisites(actor, platform, current, action).containsAll(prerequisites))
            throw new CoolException("前置步骤或权限已变化，请重新预览", 403);
        for (String step : prerequisites) {
            requireOperation(actor, platform, step);
            executePublication(actor, platform, current, step, null);
            current = (CouponTemplateEntity) coupons.info(pending.id());
        }
        boolean savedChange = false;
        if (Set.of("DELETE", "DISABLE_AND_DELETE").contains(action)) {
            try {
            checkDeletion(current);
            if ("DISABLE_AND_DELETE".equals(action)) {
                var disabled = copy(current);
                disabled.setStatus(2);
                Object updated = platform ? coupons.update(disabled) : workspace.couponSave(actor, disabled);
                if (!Boolean.TRUE.equals(updated)) throw new CoolException("禁用未完成，删除未执行，两步均未生效");
            }
            Object removed = platform ? coupons.delete(pending.id()) : workspace.couponDelete(actor, List.of(pending.id()));
            if (!Boolean.TRUE.equals(removed)) throw new CoolException("优惠券未删除，请刷新后重试");
            } catch (RuntimeException failure) {
                if ("DISABLE_AND_DELETE".equals(action))
                    throw new CoolException("禁用并删除未完成，两步均未生效。" + Objects.toString(failure.getMessage(), "请重试"));
                throw failure;
            }
        } else if (Set.of("UPDATE", UPDATE_AND_SUBMIT, "ENABLE", "DISABLE").contains(action)) {
            var update = copy(current);
            var edits = new ArrayList<Change>();
            if ("UPDATE".equals(action) || UPDATE_AND_SUBMIT.equals(action)) {
                this.edits.apply(update, pending.fields(), edits);
                this.edits.applyStock(update, pending.stock(), edits);
            }
            else {
                this.edits.apply(update, pending.fields(), edits);
                this.edits.applyStock(update, pending.stock(), edits);
                update.setStatus("ENABLE".equals(action) ? 1 : 2);
            }
            if (UPDATE_AND_SUBMIT.equals(action)) checkSubmitState(current);
            savedChange = !UPDATE_AND_SUBMIT.equals(action) || !edits.isEmpty();
            if (savedChange) {
                Object saved = platform ? coupons.update(update) : workspace.couponSave(actor, update);
                if (!Boolean.TRUE.equals(saved)) throw new CoolException("优惠券未更新，请刷新后重试");
            }
            if (UPDATE_AND_SUBMIT.equals(action)) {
                var updated = (CouponTemplateEntity) coupons.info(pending.id());
                executePublication(actor, platform, updated, "SUBMIT", null);
            }
        } else executePublication(actor, platform, current, action, pending.reason());
        String result = UPDATE_AND_SUBMIT.equals(action)
            ? queries.label(current) + (savedChange ? "已修改并提交审核"
                : (pending.stock() != null ? "库存" : "所选字段") + "已是指定值，已提交审核") + "，当前状态为待审核。" : null;
        cache.del(key);
        if ("DISABLE_AND_DELETE".equals(action)) return "我已经帮你禁用了这张券，随后也把它删掉了。";
        if ("DELETE".equals(action)) return "这张券已经删除。";
        return result;
    }

    private void require(Long actor, boolean platform, String action) {
        if (platform) {
            String suffix = switch (action) { case "view" -> "page"; case "create" -> "add"; default -> action; };
            access.platform("coupon:template:" + suffix);
        } else auth.require(actor, "coupon:" + action);
    }

    private void requireOperation(Long actor, boolean platform, String action) {
        if ("DISABLE_AND_DELETE".equals(action)) {
            require(actor, platform, "update");
            require(actor, platform, "delete");
            return;
        }
        if (UPDATE_AND_SUBMIT.equals(action)) {
            require(actor, platform, "update");
            requireOperation(actor, platform, "SUBMIT");
            return;
        }
        if (Set.of("APPROVE", "REJECT").contains(action) && !platform)
            throw new CoolException("仅运营可以审核优惠券", 403);
        String permission = switch (action) {
            case "DELETE" -> "delete";
            case "SUBMIT", "WITHDRAW", "PUBLISH", "UNPUBLISH" -> platform ? "update" : publicationAction(action);
            case "UPDATE", "ENABLE", "DISABLE", "APPROVE", "REJECT" -> "update";
            default -> throw new CoolException("不支持此优惠券操作");
        };
        require(actor, platform, permission);
    }

    private void checkDeletion(CouponTemplateEntity row) {
        if (Objects.requireNonNullElse(row.getIssuedCount(), 0L) > 0)
            throw new CoolException("这张券已经发放，不能删除。禁用也不能解除这项限制。");
        if (couponRelations.references(row.getId()) > 0)
            throw new CoolException("这张券已有发布历史、商品内容或发放关联记录，不能删除。撤回、下架或禁用也不能解除这项限制。");
    }

    private String publicationAction(String action) {
        return switch (action) {
            case "SUBMIT" -> "submitAudit";
            case "WITHDRAW" -> "withdrawAudit";
            case "PUBLISH" -> "publish";
            case "UNPUBLISH" -> "unpublish";
            default -> throw new CoolException("不支持此发布操作");
        };
    }

    private void executePublication(Long actor, boolean platform, CouponTemplateEntity row, String action, String reason) {
        Long id = row.getId();
        Integer version = row.getRuleVersion();
        if (!platform) {
            workspace.publication(actor, id, version, publicationAction(action));
            return;
        }
        switch (action) {
            case "SUBMIT" -> publications.submitAudit(id, version);
            case "WITHDRAW" -> publications.withdrawAudit(id, version);
            case "PUBLISH" -> publications.publish(id, version);
            case "UNPUBLISH" -> publications.unpublish(id, version);
            case "APPROVE", "REJECT" -> publications.audit(id, version, "APPROVE".equals(action), reason);
            default -> throw new CoolException("不支持此发布操作");
        }
    }

    public static List<Long> selected(Chat input) {
        var ids = input.selectedIds();
        if (ids == null) return List.of();
        if (ids.size() > 30 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new CoolException("每次最多选择30款有效优惠券");
        return ids.stream().distinct().toList();
    }

    private String token(Long actor, boolean platform, CouponTemplateEntity row, Intent intent, Long merchantId, Long storeId, Chat input) {
        return token(actor, platform, row, intent, merchantId, storeId, input, List.of());
    }

    private String token(Long actor, boolean platform, CouponTemplateEntity row, Intent intent, Long merchantId, Long storeId, Chat input, List<String> prerequisites) {
        String token = UUID.randomUUID().toString();
        cache.set(key(actor, platform, token), encode(new Pending(row.getId(), intent.fields(), intent.action(), intent.reason(),
            fingerprint(row), System.currentTimeMillis() + 600_000, intent.stock(), merchantId, storeId, intent, selection(input), prerequisites)), 600);
        String index = key(actor, platform, "previews");
        var tokens = new ArrayList<String>();
        if (cache.get(index) instanceof String encoded) {
            try { tokens.addAll(Arrays.asList(json.readValue(encoded, String[].class))); }
            catch (java.io.IOException invalid) { throw new CoolException("待办记录无效"); }
        }
        tokens.add(token);
        cache.set(index, encode(tokens), 600);
        return token;
    }

    private String publicationLabel(CouponTemplateEntity row, String field, List<String> labels) {
        var publication = row.getPublication();
        int index = publication != null && publication.get(field) instanceof Number number ? number.intValue() : 0;
        return index >= 0 && index < labels.size() ? labels.get(index) : "未知";
    }

    private String operationName(String action) {
        if (UPDATE_AND_SUBMIT.equals(action)) return "修改并提交审核";
        return switch (action) {
            case "DELETE" -> "删除"; case "ENABLE" -> "启用"; case "DISABLE" -> "停用";
            case "SUBMIT" -> "提交审核"; case "WITHDRAW" -> "撤回审核";
            case "PUBLISH" -> "上架"; case "UNPUBLISH" -> "下架";
            case "APPROVE" -> "审核通过"; case "REJECT" -> "驳回";
            default -> "修改";
        };
    }

    private String impact(String action, CouponTemplateEntity row) {
        return switch (action) {
            case "DELETE" -> "成功后将从列表移除。当前状态为" + queries.statusName(row.getStatus()) + "，已发放 "
                + Objects.requireNonNullElse(row.getIssuedCount(), 0L) + " 张。关联记录会触发删除限制。";
            case "SUBMIT" -> "将当前规则版本 " + row.getRuleVersion() + " 送入平台审核。";
            case UPDATE_AND_SUBMIT -> "将先保存下列变更，再提交新版本。任一步失败，两步都不会生效。";
            case "WITHDRAW" -> "将撤回待审核的提交。";
            case "PUBLISH" -> "通过业务校验后，将当前已审核版本对用户展示。";
            case "UNPUBLISH" -> "将停止对用户展示此上架版本。";
            case "APPROVE", "REJECT" -> "将记录当前提交版本的审核结论。";
            case "ENABLE" -> "将启用模板，后续可按现有流程提交审核。";
            case "DISABLE" -> "将停用模板，审核中或上架中的券受原业务限制。";
            default -> "将保存下列变更。";
        };
    }

    private Reply report(Long actor, boolean platform, Chat input, Intent intent) {
        var metrics = intent.query() == null || intent.query().metrics() == null ? List.<String>of() : intent.query().metrics();
        if (metrics.isEmpty() || metrics.size() > 3 || !Set.of("COUNT", "SALES", "REDEEM").containsAll(metrics))
            return say("请指定要汇总的指标：券款数、单券已支付销量、单券成功核销，最多三项。其他比较口径暂不支持。");
        var sections = new ArrayList<String>();
        for (String metric : new LinkedHashSet<>(metrics)) {
            String label = switch (metric) { case "COUNT" -> "券款数"; case "SALES" -> "已支付销量"; default -> "成功核销"; };
            var spec = new QuerySpec("RESET", intent.query().price(), List.of(),
                "COUNT".equals(metric) ? intent.query().groupBy() : "NONE", intent.query().unsupportedConditions(), null, intent.query().relativeDays());
            var step = new Intent(metric, null, intent.couponName(), intent.type(), intent.startDay(), intent.endDay(), null,
                intent.status(), intent.auditStatus(), intent.publishStatus(), null, false, intent.merchantName(), intent.storeName(), null, null, spec);
            try {
                var result = handle(actor, platform, input, step);
                if (!result.choices().isEmpty() && !"COUNT".equals(metric))
                    sections.add(label + "：请先选定一张优惠券，再查询此指标。");
                else sections.add(label + "：\n" + result.text());
            } catch (CoolException limited) { sections.add(label + "未完成：" + limited.getMessage()); }
        }
        return new Reply(String.join("\n\n", sections), List.of(), null, List.of(), null, null, false, List.of(), "查询汇总", null,
            context.rememberQuery(actor, platform, input, intent), null);
    }

    private Reply draft(BaseSysUserEntity account, Long merchant, Long store, Fields fields) {
        if (merchant == null) return say("请在优惠券页面选择所属商家后继续。");
        if (fields == null || fields.name() == null || fields.name().isBlank() || !TYPES.contains(Objects.toString(fields.type(), "")))
            return say("请补充券名称及类型。可选满减、折扣、代金、套餐、体验或兑换券。");
        if (fields.salePrice() == null) return say("请补充售价，免费领取请填写0。");
        if ("CASH".equals(fields.type()) && fields.deductAmount() == null)
            return say("请补充代金券的抵扣金额。");
        if ("FULL_REDUCTION".equals(fields.type()) && (fields.minSpend() == null || fields.discountAmount() == null))
            return say("请补充满减券的" + (fields.minSpend() == null ? "消费门槛" : "减免金额") + "。");
        if ("DISCOUNT".equals(fields.type()) && fields.discountRate() == null)
            return say("请补充折扣数，例如八五折。");
        if (account != null && !scope.all(account) && store == null) return say("请先选择授权门店。");
        var draft = new CouponTemplateEntity();
        draft.setMerchantId(merchant);
        draft.setMerchantName(queries.merchantName(merchant));
        draft.setType(fields.type());
        draft.setScopeType(store == null ? 0 : 1);
        draft.setStoreIds(store == null ? List.of() : List.of(store));
        draft.setStatus(0);
        draft.setValidDays(fields.validDays());
        draft.setPerCustomerLimit(fields.perCustomerLimit());
        if (Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE").contains(fields.type())) {
            var items = fields.contentItems();
            if (items == null || items.isEmpty()) return say("请补充套餐或服务内容，并说明各项目的数量。");
        }
        edits.apply(draft, fields, new ArrayList<>());
        return new Reply("草稿已准备好。请核对优惠规则、适用门店和有效期，再保存。", List.of(), draft, List.of(), null);
    }

    private void checkSubmitState(CouponTemplateEntity row) {
        if (!Integer.valueOf(1).equals(row.getStatus())) throw new CoolException("请先启用优惠券模板，再修改并提交审核");
        var publication = row.getPublication();
        if (publication != null && (isActive(publication.get("auditStatus")) || isActive(publication.get("publishStatus"))))
            throw new CoolException("已上架或正在审核，请先下架或撤回审核");
    }

    private boolean isActive(Object state) {
        return state instanceof Number number && number.intValue() == 1;
    }

    private CouponTemplateEntity copy(CouponTemplateEntity source) {
        var target = new CouponTemplateEntity();
        org.springframework.beans.BeanUtils.copyProperties(source, target);
        return target;
    }

    public String narrate(Chat input, Reply result) { return narrator.narrate(input, result); }
    public boolean narrationSupported(String text, Reply result) { return narrator.narrationSupported(text, result); }
    public String renderCountNarration(String text, CountResult count) { return narrator.renderCountNarration(text, count); }
    public Intent interpret(Chat input) { return interpreter.interpret(input); }

    private Reply say(String text) { return new Reply(text, List.of(), null, List.of(), null); }
    public static String key(Long actor, boolean platform, String token) { return "ai:coupon:" + platform + ":" + actor + ":" + token; }
    private String fingerprint(CouponTemplateEntity row) { return cn.hutool.crypto.SecureUtil.sha256(encode(row)); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new CoolException("助手数据格式无效"); }
    }
}
