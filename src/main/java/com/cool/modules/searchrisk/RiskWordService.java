package com.cool.modules.searchrisk;

import cn.hutool.crypto.digest.DigestUtil;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.searchrisk.mapper.RiskWordStateMapper;
import com.cool.modules.searchrisk.mapper.SearchRiskAuditMapper;
import com.cool.modules.searchrisk.mapper.SearchWordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.houbb.sensitive.word.support.allow.WordAllowSystem;
import com.github.houbb.sensitive.word.support.deny.WordDenySystem;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理风险词条及固定版本词表的事务导入
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:26:39
 */
@Service
@lombok.RequiredArgsConstructor
public class RiskWordService {

    static final String VERSION = "1.0.0";
    static final String HOUBB = "HOUBB";
    static final String LICENSE = "Apache-2.0";
    private static final String SOURCE_URL = "https://github.com/houbb/sensitive-word-data";
    private static final Set<String> MODES = Set.of("BLOCK", "REVIEW", "ALLOW");

    private final SearchWordMapper words;
    private final RiskWordStateMapper states;
    private final SearchRiskAuditMapper audits;
    private final ContentRiskDictionary dictionary;
    private final ObjectMapper json;

    public record ImportResult(Long importId, int inserted, int existing, int tombstoned,
                               String sourceVersion, String checksum) {}

    @Transactional
    public long save(Long id, String word, String mode, Boolean enabled) {
        if (word == null || word.isBlank() || word.length() > 100 || mode == null
                || !MODES.contains(mode) || enabled == null) {
            throw new CoolException("词条参数无效", 400);
        }
        var state = lockState();
        var row = id == null ? new SearchWord() : getActive(id);
        String normalized = SearchRiskService.normalize(word.trim());
        boolean duplicate = words.selectListByQuery(QueryWrapper.create().eq(SearchWord::getDeleted, false))
                .stream().anyMatch(other -> !other.getId().equals(id)
                        && normalized.equals(SearchRiskService.normalize(other.getWord())));
        if (duplicate) throw new CoolException("已存在相同词条", 409);

        row.setWord(word.trim());
        row.setMode(mode);
        row.setEnabled(enabled);
        if (id == null) {
            row.setDeleted(false);
            row.setSource("CUSTOM");
            words.insert(row);
        } else {
            words.update(row);
        }
        publishRevision(state);
        audit(row.getId(), "WORD_SAVE", row);
        return row.getId();
    }

    @Transactional
    public void delete(Long id) {
        var state = lockState();
        var row = getActive(id);
        row.setDeleted(true);
        row.setEnabled(false);
        words.update(row);
        publishRevision(state);
        audit(id, "WORD_DELETE", row);
    }

    @Transactional
    public ImportResult importHoubb(String version, String requestId) {
        if (!VERSION.equals(version) || requestId == null || requestId.isBlank() || requestId.length() > 100) {
            throw new CoolException("仅支持审定词库版本 " + VERSION + "，且必须提供请求编号", 400);
        }
        var state = lockState();
        Map<String, String> entries = new LinkedHashMap<>();
        WordDenySystem.getInstance().deny().forEach(word -> addEntry(entries, word, "REVIEW"));
        WordAllowSystem.getInstance().allow().forEach(word -> addEntry(entries, word, "ALLOW"));
        if (entries.isEmpty()) throw new IllegalStateException("审定词表为空，未执行导入");
        var manifest = new StringBuilder();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> manifest.append(entry.getValue()).append(':').append(entry.getKey()).append('\n'));
        String checksum = DigestUtil.sha256Hex(manifest.toString());

        Set<String> present = new HashSet<>();
        Set<String> removed = new HashSet<>();
        for (SearchWord row : words.selectAll()) {
            Set<String> keys = Boolean.TRUE.equals(row.getDeleted()) ? removed : present;
            keys.add(sourceKey(SearchRiskService.normalize(row.getWord())));
            if (row.getSourceKey() != null) keys.add(row.getSourceKey());
        }
        boolean enableImported = dictionary.bundledEnabled();
        var pending = new ArrayList<SearchWord>();
        int existing = 0;
        int tombstoned = 0;
        for (var entry : entries.entrySet()) {
            String key = sourceKey(entry.getKey());
            if (removed.contains(key)) {
                tombstoned++;
            } else if (present.contains(key)) {
                existing++;
            } else {
                var row = new SearchWord();
                row.setWord(entry.getKey());
                row.setMode(entry.getValue());
                row.setEnabled(enableImported);
                row.setSource(HOUBB);
                row.setSourceVersion(VERSION);
                row.setLicense(LICENSE);
                row.setSourceKey(key);
                row.setDeleted(false);
                pending.add(row);
            }
        }
        for (int offset = 0; offset < pending.size(); offset += 500) {
            words.insertBatch(pending.subList(offset, Math.min(offset + 500, pending.size())));
        }
        if (!Boolean.TRUE.equals(state.getInitialized()) || !pending.isEmpty()) {
            state.setInitialized(true);
            state.setChecksum(checksum);
            publishRevision(state);
        }
        var audit = audit(null, "WORD_IMPORT", Map.of(
                "source", SOURCE_URL, "sourceVersion", VERSION, "license", LICENSE,
                "requestId", requestId, "checksum", checksum, "inserted", pending.size(),
                "existing", existing, "tombstoned", tombstoned));
        return new ImportResult(audit.getId(), pending.size(), existing, tombstoned, VERSION, checksum);
    }

    private void addEntry(Map<String, String> entries, String word, String mode) {
        if (word == null || word.isBlank()) return;
        String normalized = SearchRiskService.normalize(word.trim());
        if (normalized.length() > 100) throw new IllegalStateException("审定词表含超长词条");
        entries.put(normalized, mode);
    }

    private String sourceKey(String normalized) {
        return DigestUtil.sha256Hex(HOUBB + ":" + normalized);
    }

    private RiskWordState lockState() {
        var state = states.selectOneByQuery(QueryWrapper.create().eq(RiskWordState::getId, 1L).forUpdate());
        if (state == null) throw new IllegalStateException("风险词库状态缺失，请先执行词库迁移");
        return state;
    }

    private SearchWord getActive(Long id) {
        if (id == null) throw new CoolException("缺少词条编号", 400);
        var row = words.selectOneById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) throw new CoolException("词条不存在", 404);
        return row;
    }

    private void publishRevision(RiskWordState state) {
        state.setRevision(state.getRevision() + 1);
        states.update(state);
    }

    private SearchRiskAudit audit(Long targetId, String action, Object detail) {
        var row = new SearchRiskAudit();
        row.setOperatorId(CoolSecurityUtil.getCurrentUserId());
        row.setTargetId(targetId);
        row.setAction(action);
        row.setDetail(json.valueToTree(detail).toString());
        audits.insert(row);
        return row;
    }
}
