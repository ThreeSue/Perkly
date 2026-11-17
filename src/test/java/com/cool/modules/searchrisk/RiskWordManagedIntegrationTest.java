package com.cool.modules.searchrisk;

import static org.junit.jupiter.api.Assertions.*;

import com.cool.modules.searchrisk.mapper.SearchWordMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.cool.core.security.jwt.JwtUser;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在隔离库验收完整词表导入和删除后的检测行为
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:32:32
 */
@SpringBootTest(properties = {
        "perkly.profile.worker-enabled=false", "perkly.stamp.scheduler-enabled=false",
        "perkly.support.ai-dispatch-enabled=false", "perkly.content-risk.dispatch-enabled=false",
        "perkly.ai.worker-enabled=false", "auto-table.enable=false",
        "perkly.content-risk.scheduler-enabled=false", "perkly.activity-notifications.scheduler-enabled=false",
        "spring.quartz.auto-startup=false", "logging.level.com.cool=INFO"
})
@ActiveProfiles("local")
@AutoConfigureMockMvc(addFilters = false)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/perkly_ai_regression_20261106\\?.+")
class RiskWordManagedIntegrationTest {

    @Autowired RiskWordService service;
    @Autowired SearchWordMapper words;
    @Autowired ContentRiskDictionary dictionary;
    @Autowired RiskWordController controller;
    @Autowired MockMvc mvc;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional
    void importedWordIsSearchableAndDoesNotReturnAfterDeletion() throws Exception {
        var principal = new JwtUser(1L, "risk-test", "",
                List.of(new SimpleGrantedAuthority("search:risk:view")), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        String testWord = "管理字典测试" + UUID.randomUUID();
        long id = service.save(null, testWord, "BLOCK", true);
        assertTrue(dictionary.find(testWord).stream().anyMatch(hit -> hit.word().equals(testWord)));
        service.save(id, testWord, "BLOCK", false);
        assertTrue(dictionary.find(testWord).stream().noneMatch(hit -> hit.word().equals(testWord)));

        var imported = service.importHoubb("1.0.0", "managed-" + UUID.randomUUID());
        assertTrue(imported.inserted() + imported.existing() + imported.tombstoned() > 60_000);
        assertFalse((Boolean) dictionary.sources().get("bundledEnabled"));
        assertTrue((Boolean) dictionary.sources().get("managed"));
        var entry = words.selectOneByQuery(QueryWrapper.create()
                .eq(SearchWord::getWord, "联系方式").eq(SearchWord::getDeleted, false));
        assertNotNull(entry);
        mvc.perform(get("/admin/content-risk/words/page").param("keyword", entry.getWord()).param("size", "100"))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.list[?(@.word == '" + entry.getWord() + "')]").isNotEmpty());
        assertTrue(dictionary.find(entry.getWord()).stream().anyMatch(hit -> hit.word().equals(entry.getWord())));

        service.delete(entry.getId());
        var again = service.importHoubb("1.0.0", "managed-" + UUID.randomUUID());
        assertEquals(0, again.inserted());
        assertTrue(again.tombstoned() > imported.tombstoned());
        assertEquals(imported.checksum(), again.checksum());
        assertTrue(dictionary.find(entry.getWord()).stream().noneMatch(hit -> hit.word().equals(entry.getWord())));
        mvc.perform(get("/admin/content-risk/words/page").param("source", "HOUBB")
                        .param("keyword", entry.getWord()).param("size", "100"))
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.list[?(@.word == '" + entry.getWord() + "')]").isEmpty());
    }

    @Test
    void unauthorizedWordEndpointsRejectReadAndWrite() {
        assertThrows(com.cool.core.exception.CoolException.class,
                () -> controller.page(1, 20, null, null, null));
        assertThrows(com.cool.core.exception.CoolException.class,
                () -> controller.save(new RiskWordController.WordInput(null, "示例", "REVIEW", true)));
    }
}
