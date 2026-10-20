package com.cool.modules.user.service;

import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.mapper.query.AdminUserAccountQueries;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 运营端按平台账号查询绑定状态；不将密码哈希或微信凭据返回前端。 */
@Service
@RequiredArgsConstructor
public class AdminUserAccountQueryService {
    private final UserInfoMapper users;

    public PageResult<Map<String, Object>> page(JSONObject params) {
        int page = Math.max(1, params.getInt("page", 1));
        int size = Math.max(1, Math.min(1000, params.getInt("size", 20)));
        QueryWrapper query = AdminUserAccountQueries.list(params);
        Page<Row> records = users.paginateAs(page, size, query, Row.class);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setList(records.getRecords().stream().map(row -> publicFields(row, query)).toList());
        result.getPagination().setPage((long) page);
        result.getPagination().setSize((long) size);
        result.getPagination().setTotal(records.getTotalRow());
        return result;
    }

    public List<Map<String, Object>> list(JSONObject params) {
        return rows(AdminUserAccountQueries.list(params));
    }

    public Map<String, Object> info(Long id) {
        if (id == null) throw new CoolException("用户不存在");
        List<Map<String, Object>> rows = rows(AdminUserAccountQueries.info(id));
        if (rows.isEmpty()) throw new CoolException("用户不存在");
        Map<String, Object> result = rows.get(0);
        result.put("legacyAccounts", rows(AdminUserAccountQueries.legacyAccounts(id)));
        return result;
    }

    private List<Map<String, Object>> rows(QueryWrapper query) {
        return users.selectRowsByQuery(query).stream().map(row -> publicFields(row, query)).toList();
    }

    private Map<String, Object> publicFields(Row row, QueryWrapper query) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (QueryColumn column : CPI.getSelectColumns(query)) {
            String key = column.getAlias() == null ? column.getName() : column.getAlias();
            Object value = row.get(key);
            if (List.of("wechatBound", "passwordSet", "historyPending").contains(key)) {
                value = value instanceof Boolean flag ? (flag ? 1 : 0) : ((Number) value).intValue();
            }
            if (value instanceof Timestamp time) value = time.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            data.put(key, value);
        }
        return data;
    }
}
