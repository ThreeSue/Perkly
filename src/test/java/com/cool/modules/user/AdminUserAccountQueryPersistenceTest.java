package com.cool.modules.user;

import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.service.AdminUserAccountQueryService;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 对照原查询验证账号筛选与绑定关联的返回兼容性并回滚测试数据
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:54:05
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=account-query-test-unused", "sms-auth.access-key-secret=account-query-test-unused"
})
@ActiveProfiles("local")
@Transactional
class AdminUserAccountQueryPersistenceTest {
    @Autowired private AdminUserAccountQueryService service;
    @Autowired private SqlSessionTemplate session;
    private JdbcTemplate jdbc;
    private String prefix;
    private long formal;
    private long legacy;

    // 旧实现仅留在测试中作为独立结果基准，生产代码不再执行拼接SQL。
    private static final String PHONE = "COALESCE(u.phone,'') REGEXP '^1[3-9][0-9]{9}$'";
    private static final String PASSWORD = "(u.password IS NOT NULL AND u.password<>'')";
    private static final String WX = "EXISTS (SELECT 1 FROM user_wx w WHERE w.user_id=u.id OR (w.user_id IS NULL AND u.unionid IS NOT NULL AND u.unionid<>'' AND (w.openid=u.unionid OR w.unionid=u.unionid)))";
    private static final String FIELDS = "SELECT u.id,u.nick_name AS nickName,u.avatar_url AS avatarUrl,u.phone,u.gender,u.status,u.create_time AS createTime,u.update_time AS updateTime,"
            + WX + " AS wechatBound," + PASSWORD + " AS passwordSet,NOT (" + PHONE + ") AS historyPending,"
            + "(SELECT w.user_id FROM user_wx w WHERE w.user_id<>u.id AND u.unionid IS NOT NULL AND u.unionid<>'' AND (w.openid=u.unionid OR w.unionid=u.unionid) LIMIT 1) AS associatedAccountId FROM user_info u";

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(session.getConnection(), true));
        prefix = "FlexParity" + Long.toUnsignedString(System.nanoTime());
        String phoneBase = Long.toString(Math.floorMod(System.nanoTime(), 90000000L) + 10000000L);
        formal = user("A", "139" + phoneBase, null, "hash", 1);
        user("B", "138" + phoneBase, null, "", 0);
        user("C", "137" + phoneBase, null, null, 2);
        legacy = user("D", null, prefix + "wx1", null, 1);
        user("E", "invalid", prefix + "wx2", "hash", 1);
        jdbc.update("INSERT INTO user_wx(user_id,openid) VALUES(?,?)", formal, prefix + "wx1");
        jdbc.update("INSERT INTO user_wx(user_id,openid) VALUES(NULL,?)", prefix + "wx2");
        session.clearCache();
    }

    @Test void accountGroupsBindingFlagsAndSortsMatchOriginalQueries() {
        for (String group : List.of("formal", "history", "all")) {
            for (String field : List.of("id", "nickName", "updateTime", "unrecognized")) {
                for (String direction : List.of("asc", "desc")) {
                    for (int flag : List.of(0, 1)) {
                        JSONObject params = params(group).set("order", field).set("sort", direction).set("wechatBound", flag);
                        String condition = " AND (" + WX + ")=" + flag;
                        assertEquals(expected(group, condition, field, direction), service.list(params), params.toString());
                    }
                }
            }
        }
        for (int flag : List.of(0, 1)) {
            assertEquals(expected("all", " AND (" + PASSWORD + ")=" + flag, "id", "asc"),
                    service.list(params("all").set("passwordSet", flag).set("order", "id").set("sort", "asc")));
        }
    }

    @Test void paginationStatusAndIdSearchKeepOriginalContract() {
        JSONObject params = params("all").set("order", "id").set("sort", "asc").set("page", 2).set("size", 2);
        var all = expected("all", "", "id", "asc");
        var page = service.page(params);
        assertEquals(all.subList(2, 4), page.getList());
        assertEquals(5L, page.getPagination().getTotal());
        params.set("page", 99);
        assertTrue(service.page(params).getList().isEmpty());
        assertEquals(expected("formal", " AND u.status=2", "id", "asc", false),
                service.list(params("formal").set("status", 2).set("order", "id").set("sort", "asc")));
        JSONObject search = new JSONObject().set("accountGroup", "all").set("keyWord", Long.toString(formal));
        assertEquals(formal, ((Number) service.list(search).get(0).get("id")).longValue());
        assertTrue(service.list(params("all").set("keyWord", prefix + "' OR 1=1 --")).isEmpty());
    }

    @Test void detailsKeepAssociationMaskingNullsAndSensitiveFieldExclusion() {
        Map<String, Object> actual = service.info(legacy);
        Map<String, Object> expected = baseline(FIELDS + " WHERE u.id=?", legacy).get(0);
        expected.put("legacyAccounts", List.of());
        assertEquals(expected, actual);
        assertEquals(formal, ((Number) actual.get("associatedAccountId")).longValue());
        Map<String, Object> owner = service.info(formal);
        List<?> legacyRows = (List<?>) owner.get("legacyAccounts");
        assertEquals(1, legacyRows.size());
        Map<?, ?> old = (Map<?, ?>) legacyRows.get(0);
        assertEquals(legacy, ((Number) old.get("id")).longValue());
        assertTrue(old.containsKey("phoneMasked"));
        assertNull(old.get("phoneMasked"));
        jdbc.update("UPDATE user_info SET phone='1234567' WHERE id=?", legacy);
        session.clearCache();
        Map<?, ?> masked = (Map<?, ?>) ((List<?>) service.info(formal).get("legacyAccounts")).get(0);
        assertEquals("123****4567", masked.get("phoneMasked"));
        assertFalse(owner.containsKey("password"));
        assertFalse(owner.containsKey("unionid"));
        assertFalse(owner.containsKey("openid"));
    }

    @Test void invalidFiltersRetainExistingErrors() {
        assertThrows(CoolException.class, () -> service.list(params("bad")));
        assertThrows(CoolException.class, () -> service.list(params("all").set("status", 3)));
        assertThrows(CoolException.class, () -> service.page(params("all").set("wechatBound", 9)));
        assertThrows(CoolException.class, () -> service.list(params("all").set("passwordSet", -1)));
    }

    private JSONObject params(String group) {
        return new JSONObject().set("accountGroup", group).set("keyWord", prefix);
    }

    private List<Map<String, Object>> expected(String group, String extra, String field, String direction) {
        return expected(group, extra, field, direction, true);
    }

    private List<Map<String, Object>> expected(String group, String extra, String field, String direction, boolean defaultStatus) {
        String where = " WHERE u.nick_name LIKE ?";
        if ("formal".equals(group)) where += " AND (" + PHONE + ")" + (defaultStatus ? " AND u.status IN (0,1)" : "");
        if ("history".equals(group)) where += " AND NOT (" + PHONE + ")";
        String order = switch (field) { case "id" -> "id"; case "nickName" -> "nick_name"; case "updateTime" -> "update_time"; default -> "create_time"; };
        return baseline(FIELDS + where + extra + " ORDER BY u." + order + " " + direction + ",u.id DESC", prefix + "%");
    }

    private List<Map<String, Object>> baseline(String sql, Object... args) {
        return jdbc.query(sql, (rs, index) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String key = rs.getMetaData().getColumnLabel(i);
                Object value = rs.getObject(i);
                if (List.of("wechatBound", "passwordSet", "historyPending").contains(key))
                    value = value instanceof Boolean flag ? (flag ? 1 : 0) : ((Number) value).intValue();
                if (value instanceof Timestamp time) value = time.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                data.put(key, value);
            }
            return data;
        }, args);
    }

    private long user(String suffix, String phone, String unionid, String password, int status) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO user_info(nick_name,phone,unionid,password,status,create_time,update_time) VALUES(?,?,?,?,?,'2026-10-01 01:02:03','2026-10-01 01:02:03')", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, prefix + suffix); statement.setString(2, phone); statement.setString(3, unionid);
            statement.setString(4, password); statement.setInt(5, status); return statement;
        }, key);
        return key.getKey().longValue();
    }
}
