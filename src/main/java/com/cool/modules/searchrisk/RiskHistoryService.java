package com.cool.modules.searchrisk;

import cn.hutool.crypto.digest.DigestUtil;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.searchrisk.mapper.RiskHistoryPreviewMapper;
import com.cool.modules.searchrisk.mapper.SearchRiskAuditMapper;
import com.cool.modules.searchrisk.mapper.SearchRiskMapper;
import com.cool.modules.support.service.SupportAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 预览和审计历史风险整理并执行纯词库复扫
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:28:00
 */
@Service
@RequiredArgsConstructor
public class RiskHistoryService {
    private static final long PREVIEW_TTL = 15 * 60 * 1000L;
    private static final String UPDATE = "UPDATE_ORIGIN";
    private static final String MERGE = "MERGE_DUPLICATE";
    private static final String UNCHANGED = "UNCHANGED";
    private static final String REVIEW = "REVIEW_REQUIRED";

    private final RiskHistoryPreviewMapper previews;
    private final SearchRiskMapper records;
    private final SearchRiskAuditMapper audits;
    private final RiskOriginalContent content;
    private final SearchRiskService detection;
    private final ObjectMapper json;

    public record Scope(Long afterId, Integer limit) {}
    public record Scan(String source, Long afterId, Integer limit) {}
    public record Apply(Long previewId, String requestId, List<Long> ids) {}
    public record Origin(String originType, Long originId, String originField, String contentVersion,
                         String submittedByType, Long submittedById, String provenanceStatus) {}
    public record Item(Long id, String action, boolean applicable, Long duplicateOfId, String evidence,
                       Origin previousOrigin, String originType, Long originId, String originField,
                       String contentVersion, String rowVersion, String submittedByType, Long submittedById) {}
    public record Preview(Long previewId, Date expiresAt, Long nextAfterId, List<Item> items, Map<String, Long> counts) {}
    public record Applied(int updated, int merged, int unchanged) {}
    public record Receipt(List<Long> ids, Applied result) {}
    public record Scanned(int checked, int created, int existing, long nextAfterId, boolean hasMore) {}
    private record Change(Item plan, SearchRisk row, SearchRisk proposed) {}
    private record Audit(String evidence, Origin before, Origin after, Long duplicateOfId, Long previewId, String requestId) {}

    @Transactional
    public Preview preview(Scope input) {
        long afterId = input == null || input.afterId() == null ? 0 : input.afterId();
        int limit = input == null || input.limit() == null ? 100 : input.limit();
        validatePage(afterId, limit, 500);
        var rows = records.selectListByQuery(QueryWrapper.create().gt(SearchRisk::getId, afterId)
            .orderBy(SearchRisk::getId, true).limit(limit + 1));
        var plannedOrigins = new HashMap<String, Long>();
        var items = new ArrayList<Item>();
        for (var row : rows.stream().limit(limit).toList()) items.add(plan(row, plannedOrigins));
        var snapshot = new RiskHistoryPreview();
        snapshot.setOperatorId(CoolSecurityUtil.getCurrentUserId());
        snapshot.setExpiresAt(new Date(System.currentTimeMillis() + PREVIEW_TTL));
        snapshot.setSnapshot(encode(items));
        snapshot.setReceipts("{}");
        previews.insert(snapshot);
        var counts = new LinkedHashMap<String, Long>();
        for (String action : List.of(UPDATE, MERGE, UNCHANGED, REVIEW)) {
            counts.put(action, items.stream().filter(item -> action.equals(item.action())).count());
        }
        Long next = rows.size() > limit ? rows.get(limit - 1).getId() : null;
        return new Preview(snapshot.getId(), snapshot.getExpiresAt(), next, items, counts);
    }

    private Item plan(SearchRisk row, Map<String, Long> plannedOrigins) {
        if (row.getDuplicateOfId() != null) {
            return item(row, row, UNCHANGED, row.getDuplicateOfId(), "已有归并主记录");
        }
        if ("AI_GENERATED".equals(row.getOriginType()) && "VERIFIED".equals(row.getProvenanceStatus())) {
            return item(row, row, UNCHANGED, null, "检测发生在模型生成处，回复未绑定业务对象");
        }
        if ("VERIFIED".equals(row.getProvenanceStatus()) && Set.of("SEARCH", "AI_INPUT").contains(Objects.toString(row.getOriginType(), ""))) {
            return item(row, row, UNCHANGED, null, "来源已记录在当次请求，提交者按请求身份显示");
        }
        var proposed = supportedOrigin(row);
        if (proposed == null) return item(row, row, REVIEW, null, "现有记录无法核对到确定对象、字段和原文");
        String key = RiskOrigin.key(proposed);
        var existing = primary(key, false);
        Long anchor = existing == null ? plannedOrigins.putIfAbsent(key, row.getId()) : existing.getId();
        if (anchor != null && !anchor.equals(row.getId())) {
            return item(row, proposed, MERGE, anchor, "与主记录的原始对象、字段和内容版本相同");
        }
        if (Objects.equals(row.getDedupKey(), key) && origin(row).equals(origin(proposed))) {
            return item(row, proposed, UNCHANGED, null, "来源字段和内容版本已核对");
        }
        return item(row, proposed, UPDATE, null, "已核对明确的业务主键、原始字段与原文");
    }

    static boolean sameOriginal(String existing, String stored) {
        return existing != null && stored != null && existing.strip().equals(stored.strip());
    }

    private SearchRisk supportedOrigin(SearchRisk row) {
        boolean verified = "VERIFIED".equals(row.getProvenanceStatus()) && RiskOrigin.key(row) != null;
        if (verified && row.getSubmittedByType() != null) return row;
        var original = content.field(row.getBusinessType(), row.getBusinessId(), row.getContent());
        if (original == null || !Objects.equals(original.scene(), row.getScene())
            || !sameOriginal(row.getContent(), original.value())) return verified ? row : null;
        if (verified && original.author() == null) return row;
        var proposed = new SearchRisk();
        proposed.setContent(row.getContent());
        proposed.setScene(row.getScene());
        var author = row.getSubmittedByType() == null ? original.author()
            : new SearchRiskService.Subject(row.getSubmittedByType(), row.getSubmittedById());
        RiskOrigin.apply(proposed, row.getBusinessType(), row.getBusinessId(), author);
        return RiskOrigin.key(proposed) == null ? null : proposed;
    }

    @Transactional
    public Applied apply(Apply input) {
        if (input == null || input.previewId() == null || input.previewId() < 1) throw conflict("请先预览历史记录");
        SupportAccess.request(input.requestId());
        if (input.ids() == null || input.ids().isEmpty() || input.ids().size() > 500
            || input.ids().stream().anyMatch(id -> id == null || id < 1)
            || input.ids().stream().distinct().count() != input.ids().size()) {
            throw new CoolException("请选择1至500条不同记录", 400);
        }
        var ids = input.ids().stream().sorted().toList();
        Long operator = CoolSecurityUtil.getCurrentUserId();
        var snapshot = previews.selectOneByQuery(QueryWrapper.create().eq(RiskHistoryPreview::getId, input.previewId()).forUpdate());
        if (snapshot == null || operator == null || !operator.equals(snapshot.getOperatorId())) throw conflict("预览不存在或不属于当前账号");
        Map<String, Receipt> receipts = decode(snapshot.getReceipts(), new TypeReference<>() {});
        var prior = receipts.get(input.requestId());
        if (prior != null) {
            if (!prior.ids().equals(ids)) throw conflict("同一请求标识不能用于不同的记录");
            return prior.result();
        }
        if (!snapshot.getExpiresAt().after(new Date())) throw conflict("预览已过期，请重新预览");
        List<Item> items = decode(snapshot.getSnapshot(), new TypeReference<>() {});
        var plans = items.stream().collect(Collectors.toMap(Item::id, Function.identity()));
        var changes = new ArrayList<Change>();
        for (Long id : ids) {
            var item = plans.get(id);
            if (item == null || !item.applicable()) throw conflict("所选记录不在本次可应用的预览中");
            var row = records.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getId, id).forUpdate());
            if (row == null || !item.rowVersion().equals(version(row))) throw conflict("风险记录已变化，请重新预览");
            var candidate = supportedOrigin(row);
            if (candidate == null || !Objects.equals(item.originType(), candidate.getOriginType())
                || !Objects.equals(item.originId(), candidate.getOriginId())
                || !Objects.equals(item.originField(), candidate.getOriginField())
                || !Objects.equals(item.contentVersion(), candidate.getContentVersion())
                || !Objects.equals(item.submittedByType(), candidate.getSubmittedByType())
                || !Objects.equals(item.submittedById(), candidate.getSubmittedById())) {
                throw conflict("原始内容已变化，请重新预览");
            }
            changes.add(new Change(item, row, candidate));
        }
        int updated = 0;
        int merged = 0;
        for (var change : changes) {
            if (UPDATE.equals(change.plan().action())) {
                apply(change, snapshot.getId(), input.requestId(), operator);
                updated++;
            }
        }
        for (var change : changes) {
            if (MERGE.equals(change.plan().action())) {
                apply(change, snapshot.getId(), input.requestId(), operator);
                merged++;
            }
        }
        var result = new Applied(updated, merged, 0);
        receipts.put(input.requestId(), new Receipt(ids, result));
        snapshot.setReceipts(encode(receipts));
        previews.update(snapshot);
        return result;
    }

    private void apply(Change change, Long previewId, String requestId, Long operator) {
        var row = change.row();
        var proposed = change.proposed();
        var plan = change.plan();
        String key = RiskOrigin.key(proposed);
        if (MERGE.equals(plan.action())) {
            var primary = primary(key, true);
            if (primary == null || !primary.getId().equals(plan.duplicateOfId()) || primary.getDuplicateOfId() != null) {
                throw conflict("归并主记录尚未确认或已变化，请一并选择主记录或重新预览");
            }
        }
        Origin before = origin(row);
        row.setOriginType(proposed.getOriginType());
        row.setOriginId(proposed.getOriginId());
        row.setOriginField(proposed.getOriginField());
        row.setContentVersion(proposed.getContentVersion());
        row.setSubmittedByType(proposed.getSubmittedByType());
        row.setSubmittedById(proposed.getSubmittedById());
        if (proposed.getSubmittedByType() != null) {
            row.setSubjectType(proposed.getSubmittedByType());
            row.setSubjectId(proposed.getSubmittedById());
            row.setUserId("APP".equals(proposed.getSubmittedByType()) ? proposed.getSubmittedById() : null);
        }
        row.setProvenanceStatus("VERIFIED");
        row.setDuplicateOfId(plan.duplicateOfId());
        row.setDedupKey(plan.duplicateOfId() == null ? key : null);
        try {
            records.update(row, false);
        } catch (DuplicateKeyException concurrent) {
            throw conflict("已存在同来源版本记录，请重新预览");
        }
        var audit = new SearchRiskAudit();
        audit.setOperatorId(operator);
        audit.setTargetId(row.getId());
        audit.setAction(MERGE.equals(plan.action()) ? "HISTORY_MERGE" : "HISTORY_UPDATE_ORIGIN");
        audit.setDetail(encode(new Audit(plan.evidence(), before, origin(row), row.getDuplicateOfId(), previewId, requestId)));
        audits.insert(audit);
    }

    public Scanned rescan(Scan input) {
        if (input == null || input.source() == null || !RiskOriginalContent.SOURCES.contains(input.source())) {
            throw new CoolException("请选择可重扫的原始内容类型", 400);
        }
        long cursor = input.afterId() == null ? 0 : input.afterId();
        int limit = input.limit() == null ? 100 : input.limit();
        validatePage(cursor, limit, 100);
        var originals = content.page(input.source(), cursor, limit + 1);
        int checked = 0;
        int created = 0;
        int existing = 0;
        for (var original : originals.stream().limit(limit).toList()) {
            checked++;
            cursor = original.id();
            for (var field : original.fields()) {
                var match = detection.rescan(field, original.id());
                if (match.id() == null) continue;
                if (match.created()) created++;
                else existing++;
            }
        }
        var result = new Scanned(checked, created, existing, cursor, originals.size() > limit);
        var audit = new SearchRiskAudit();
        audit.setOperatorId(CoolSecurityUtil.getCurrentUserId());
        audit.setAction("HISTORY_RESCAN");
        audit.setDetail(encode(Map.of("source",input.source(),"afterId",input.afterId()==null?0:input.afterId(),
            "limit",limit,"checked",checked,"created",created,"existing",existing)));
        audits.insert(audit);
        return result;
    }

    private SearchRisk primary(String key, boolean lock) {
        var query = QueryWrapper.create().eq(SearchRisk::getDedupKey, key);
        return records.selectOneByQuery(lock ? query.forUpdate() : query);
    }

    private Item item(SearchRisk row, SearchRisk proposed, String action, Long duplicate, String evidence) {
        return new Item(row.getId(), action, UPDATE.equals(action) || MERGE.equals(action), duplicate, evidence,
            origin(row), proposed.getOriginType(), proposed.getOriginId(), proposed.getOriginField(),
            proposed.getContentVersion(), version(row), proposed.getSubmittedByType(), proposed.getSubmittedById());
    }

    private static Origin origin(SearchRisk row) {
        return new Origin(row.getOriginType(), row.getOriginId(), row.getOriginField(), row.getContentVersion(),
            row.getSubmittedByType(), row.getSubmittedById(), row.getProvenanceStatus());
    }

    private String version(SearchRisk row) {
        return DigestUtil.sha256Hex(encode(row));
    }

    private static void validatePage(long afterId, int limit, int max) {
        if (afterId < 0 || limit < 1 || limit > max) throw new CoolException("游标或处理条数无效", 400);
    }

    private static CoolException conflict(String message) {
        return new CoolException(message, 409);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("历史整理数据无法保存", error);
        }
    }

    private <T> T decode(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException error) {
            throw conflict("历史预览数据无法读取，请重新预览");
        }
    }
}
