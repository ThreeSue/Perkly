package com.cool.modules.user;

import cn.hutool.json.JSONObject;
import com.cool.core.request.CrudOption;
import com.cool.core.request.PageResult;
import com.cool.modules.user.controller.admin.AdminUserInfoController;
import com.cool.modules.user.service.AdminUserAccountQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserInfoControllerTest {
    private AdminUserAccountQueryService query;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        query = mock(AdminUserAccountQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TestController(query)).build();
    }

    @Test
    void pageAcceptsFrameworkRequestAttributes() throws Exception {
        JSONObject params = new JSONObject().set("accountGroup", "formal");
        when(query.page(params)).thenReturn(new PageResult<Map<String, Object>>());
        mvc.perform(post("/test/page").requestAttr("requestParams", params))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000));
        verify(query).page(params);
    }

    @Test
    void listAcceptsFrameworkRequestAttributes() throws Exception {
        JSONObject params = new JSONObject().set("accountGroup", "history");
        when(query.list(params)).thenReturn(List.of(Map.of("id", 5)));
        mvc.perform(post("/test/list").requestAttr("requestParams", params))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(5));
        verify(query).list(params);
    }

    @Test
    void infoAcceptsFrameworkRequestAttributes() throws Exception {
        when(query.info(2L)).thenReturn(Map.of("id", 2));
        mvc.perform(get("/test/info").param("id", "2").requestAttr("requestParams", new JSONObject()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(2));
        verify(query).info(2L);
    }

    @RestController
    @RequestMapping("/test")
    static class TestController extends AdminUserInfoController {
        TestController(AdminUserAccountQueryService query) { super(query); }

        // Isolate HTTP argument binding from the framework's database-backed CRUD setup.
        @Override
        @ModelAttribute
        protected void preHandle(HttpServletRequest request,
            @RequestAttribute("requestParams") JSONObject requestParams) {
            request.setAttribute("COOL_PAGE_OP", mock(CrudOption.class));
            request.setAttribute("COOL_LIST_OP", mock(CrudOption.class));
            request.setAttribute("COOL_INFO_OP", mock(CrudOption.class));
        }
    }
}
