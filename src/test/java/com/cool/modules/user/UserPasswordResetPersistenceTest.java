package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cool.core.exception.CoolException;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.util.UserPasswordUtil;
import com.cool.modules.user.util.UserSmsUtil;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以事务回滚方式验证公开重设密码接口只更新目标账号密码
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 20:01:24
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=reset-test-unused", "sms-auth.access-key-secret=reset-test-unused"
})
@ActiveProfiles("local")
@Transactional
class UserPasswordResetPersistenceTest {
    @Autowired private UserInfoService users;
    @Autowired private SqlSessionTemplate sqlSession;
    @Autowired private WebApplicationContext webContext;
    @MockBean private UserSmsUtil sms;
    private JdbcTemplate jdbc;

    @BeforeEach void connection() {
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(sqlSession.getConnection(), true));
    }

    private UserInfoEntity account() {
        String phone;
        do { phone = "199" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000)); }
        while (jdbc.queryForObject("SELECT COUNT(*) FROM user_info WHERE phone=?", Integer.class, phone) > 0);
        var user = new UserInfoEntity(); user.setPhone(phone); user.setStatus(1);
        user.setNickName("自动测试重设密码_事务回滚"); user.setPassword(UserPasswordUtil.encode("OldPass123"));
        assertTrue(users.save(user));
        return user;
    }

    @Test void anonymousHttpResetPersistsOnlyIntendedPasswordAndNoToken() throws Exception {
        var target = account(); var other = account();
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new com.cool.core.request.RequestParamsFilter()).build();
        mvc.perform(post("/app/user/login/resetPassword").contentType("application/json")
                .content("{\"phone\":\"" + target.getPhone() + "\",\"smsCode\":\"123456\",\"password\":\"NewPass456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        String encoded = jdbc.queryForObject("SELECT password FROM user_info WHERE id=?", String.class, target.getId());
        assertTrue(UserPasswordUtil.matches("NewPass456", encoded));
        assertFalse(UserPasswordUtil.matches("OldPass123", encoded));
        assertEquals(other.getPassword(), jdbc.queryForObject("SELECT password FROM user_info WHERE id=?", String.class, other.getId()));
        assertEquals(target.getNickName(), jdbc.queryForObject("SELECT nick_name FROM user_info WHERE id=?", String.class, target.getId()));
        assertEquals(target.getPhone(), jdbc.queryForObject("SELECT phone FROM user_info WHERE id=?", String.class, target.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM user_info WHERE id=?", Integer.class, target.getId()));
        verify(sms).checkVerifyCode(target.getPhone(), "123456", UserSmsUtil.SendSceneEnum.ALL);
    }

    @Test void invalidSmsNeverChangesDatabasePassword() throws Exception {
        var target = account();
        doThrow(new CoolException("验证码错误")).when(sms)
                .checkVerifyCode(target.getPhone(), "654321", UserSmsUtil.SendSceneEnum.ALL);
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(new com.cool.core.request.RequestParamsFilter()).build();
        mvc.perform(post("/app/user/login/resetPassword").contentType("application/json")
                .content("{\"phone\":\"" + target.getPhone() + "\",\"smsCode\":\"654321\",\"password\":\"NewPass456\"}"))
                .andExpect(jsonPath("$.code").value(500));
        assertEquals(target.getPassword(), jdbc.queryForObject("SELECT password FROM user_info WHERE id=?", String.class, target.getId()));
    }
}
