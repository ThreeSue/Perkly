package com.cool.modules.ai.app;

import com.cool.CoolApplication;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.security.jwt.JwtUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证旧Docker种子库迁移后顾客集章列表可读取
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 22:08:00
 */
@SpringBootTest(classes=CoolApplication.class,properties={"spring.profiles.active=local","auto-table.enable=false","cool.initData=false","perkly.content-risk.scheduler-enabled=false","perkly.content-risk.dispatch-enabled=false","perkly.profile.worker-enabled=false","perkly.stamp.scheduler-enabled=false","perkly.support.ai-dispatch-enabled=false","perkly.ai.worker-enabled=false"})
@AutoConfigureMockMvc(addFilters=false)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PERKLY_MOBILE_UPGRADE_TEST",matches="true")
class MobileUpgradeStampTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 @Test void migratedDockerDataServesCustomerStampListWithoutAutoTable() throws Exception {
  assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("perkly_mobile_upgrade_"));
  Long id=jdbc.queryForObject("SELECT MIN(id) FROM user_info WHERE status=1",Long.class);
  assertNotNull(id);
  var u=new JwtUser(id,List.of(),true);u.setUserTypeEnum(UserTypeEnum.APP);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of()));
  try {mvc.perform(get("/app/promotions/stamps/page").param("pageNumber","1").param("pageSize","20"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.list").isArray());}
  finally {SecurityContextHolder.clearContext();}
 }
}
