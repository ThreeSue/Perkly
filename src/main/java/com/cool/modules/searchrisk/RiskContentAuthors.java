package com.cool.modules.searchrisk;

import cn.hutool.crypto.digest.DigestUtil;
import com.cool.modules.searchrisk.mapper.RiskContentAuthorMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按业务对象、字段及版本留存提交者，供后续重扫核对
 * @Version: v1.1
 * @Date: 2026-11-15
 * @Time: 03:45:00
 */
@Service
@RequiredArgsConstructor
public class RiskContentAuthors {
    private final RiskContentAuthorMapper mapper;

    public void saveChanged(String previous, String value, String field, Long objectId,
                            SearchRiskService.Subject actor) {
        if (objectId == null || actor == null || Objects.equals(previous, value)) return;
        var row = new RiskContentAuthor();
        row.setFieldKey(key(field, objectId));
        row.setContentVersion(version(value));
        row.setAuthorType(actor.type());
        row.setAuthorId(actor.id());
        if (mapper.selectOneById(row.getFieldKey()) == null) mapper.insert(row);
        else mapper.update(row, false);
    }

    public Map<String, SearchRiskService.Subject> find(Long objectId, Map<String, String> fields) {
        var keys = fields.keySet().stream().map(field -> key(field, objectId)).toList();
        if (keys.isEmpty()) return Map.of();
        Map<String, SearchRiskService.Subject> found = new HashMap<>();
        for (var row : mapper.selectListByQuery(QueryWrapper.create().in(RiskContentAuthor::getFieldKey, keys))) {
            String field = row.getFieldKey().substring(0, row.getFieldKey().lastIndexOf(':'));
            String value = fields.get(field);
            if (value != null && !value.isBlank() && Objects.equals(row.getContentVersion(), version(value))) {
                found.put(field,new SearchRiskService.Subject(row.getAuthorType(),row.getAuthorId()));
            }
        }
        return found;
    }

    private static String key(String field, Long objectId) {
        return field + ":" + objectId;
    }

    private static String version(String value) {
        return value == null ? null : DigestUtil.sha256Hex(value);
    }
}
