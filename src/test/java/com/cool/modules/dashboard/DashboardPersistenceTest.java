package com.cool.modules.dashboard;

import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.dashboard.controller.admin.AdminDashboardOverviewController;
import com.cool.modules.dashboard.service.DashboardService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证看板真实账号口径日期补零及运营身份隔离
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 23:40:24
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
    "sms-auth.access-key-id=dashboard-test-unused", "sms-auth.access-key-secret=dashboard-test-unused"
})
@ActiveProfiles("local")
@Transactional
class DashboardPersistenceTest {
    @Autowired DashboardService dashboard;
    @Autowired AdminDashboardOverviewController controller;
    @Autowired SqlSessionTemplate session;
    private JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(session.getConnection(), true));
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> counts(Map<String, Object> data) { return (Map<String, Object>)data.get("counts"); }
    private long number(Object value) { return ((Number)value).longValue(); }

    @Test void realCountsAndFourteenDaysAreConsistent() {
        var result = dashboard.overview();
        var counts = counts(result);
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_info", Long.class), number(counts.get("merchants")));
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM store", Long.class), number(counts.get("stores")));
        var types = (List<Map<String, Object>>) result.get("couponTypes");
        assertEquals(number(counts.get("coupons")), types.stream().mapToLong(row -> number(row.get("total"))).sum());
        var trends = (List<Map<String, Object>>)result.get("trends");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertEquals(14, trends.size());
        for (int i = 0; i < 14; i++) {
            assertEquals(today.minusDays(13-i).toString(), trends.get(i).get("date"));
            for (String key : List.of("users", "merchants", "stores", "coupons")) assertTrue(number(trends.get(i).get(key)) >= 0);
        }
    }

    @Test void excludesHistoricalAndCancelledUsersAndFutureDates() {
        var before = dashboard.overview();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String suffix = String.format("%08d", Math.floorMod(java.util.UUID.randomUUID().hashCode(), 100000000));
        jdbc.update("INSERT INTO user_info(nick_name,phone,status,create_time) VALUES('看板事务测试',?,1,?)", "198"+suffix, today.atStartOfDay());
        jdbc.update("INSERT INTO user_info(nick_name,phone,status,create_time) VALUES('历史测试',NULL,1,?)", today.atStartOfDay());
        jdbc.update("INSERT INTO user_info(nick_name,phone,status,create_time) VALUES('注销测试',?,2,?)", "197"+suffix, today.atStartOfDay());
        jdbc.update("INSERT INTO user_info(nick_name,phone,status,create_time) VALUES('未来测试',?,1,?)", "196"+suffix, today.plusDays(1).atStartOfDay());
        session.clearCache();
        var after = dashboard.overview();
        assertEquals(number(counts(before).get("users"))+2, number(counts(after).get("users")));
        var oldDays = (List<Map<String, Object>>)before.get("trends");
        var newDays = (List<Map<String, Object>>)after.get("trends");
        assertEquals(number(oldDays.get(13).get("users"))+1, number(newDays.get(13).get("users")));
    }

    @Test void onlyOperationsAccountsMayReadDashboard() {
        try {
            SecurityContextHolder.clearContext();
            assertThrows(CoolException.class, () -> controller.info());
            for (UserTypeEnum type : List.of(UserTypeEnum.APP, UserTypeEnum.MERCHANT)) {
                JwtUser user = new JwtUser(1L, List.of(), true); user.setUserTypeEnum(type);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
                assertThrows(CoolException.class, () -> controller.info());
            }
            JwtUser admin = new JwtUser(1L, "dashboard-test", "unused", List.of(), true);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
            assertNotNull(controller.info().getData());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
