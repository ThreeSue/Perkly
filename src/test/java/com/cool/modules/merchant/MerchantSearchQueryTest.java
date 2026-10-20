package com.cool.modules.merchant;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cool.core.base.BaseController;
import com.cool.core.request.CrudOption;
import com.cool.modules.merchant.controller.admin.AdminMerchantCategoryController;
import com.cool.modules.merchant.controller.admin.AdminMerchantInfoController;
import com.cool.modules.merchant.controller.admin.AdminMerchantStoreCategoryController;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real framework query construction; no database or SMS calls. */
class MerchantSearchQueryTest {
    private GenericApplicationContext context;
    private Object previousApplicationContext;

    @BeforeEach
    void setup() {
        previousApplicationContext = ReflectionTestUtils.getField(SpringUtil.class, "applicationContext");
        context = new GenericApplicationContext();
        context.registerBean("environment", Environment.class, StandardEnvironment::new);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", previousApplicationContext);
        context.close();
    }

    private String sql(BaseController<?, ?> controller, Class<?> entity, String endpoint, JSONObject params) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/" + endpoint);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ReflectionTestUtils.invokeMethod(controller, "preHandle", request, params);
        CrudOption option = (CrudOption) request.getAttribute("COOL_" + endpoint.toUpperCase() + "_OP");
        assertNotNull(option);
        return option.getQueryWrapper(entity).toSQL();
    }

    @Test
    void categoryTreeListAndPageBothApplyNameAndZeroValuedFilters() {
        for (String endpoint : List.of("list", "page")) {
            String query = sql(new AdminMerchantCategoryController(), MerchantCategoryEntity.class, endpoint,
                    new JSONObject().set("name", "咖啡").set("status", 0).set("showOnFront", 0));
            assertTrue(query.contains("咖啡"), query);
            assertTrue(query.contains("LIKE"), query);
            assertTrue(query.matches("(?s).*status`? = 0.*"), query);
            assertTrue(query.matches("(?s).*show_on_front`? = 0.*"), query);
        }
    }

    @Test
    void storeCategoryTreeListAndPageApplyMerchantNameAndVisibilityTogether() {
        for (String endpoint : List.of("list", "page")) {
            String query = sql(new AdminMerchantStoreCategoryController(), MerchantStoreCategoryEntity.class, endpoint,
                    new JSONObject().set("merchantId", 3).set("name", "加盟")
                            .set("status", 1).set("showOnFront", 0));
            assertTrue(query.matches("(?s).*merchant_id`? = 3.*"), query);
            assertTrue(query.contains("加盟"), query);
            assertTrue(query.matches("(?s).*status`? = 1.*"), query);
            assertTrue(query.matches("(?s).*show_on_front`? = 0.*"), query);
        }
    }

    @Test
    void merchantPageAcceptsCategorySubtreeAsAnInFilter() {
        String query = sql(new AdminMerchantInfoController(), MerchantInfoEntity.class, "page",
                JSONUtil.parseObj("{\"name\":\"咖啡\",\"categoryId\":[3,6],\"status\":0}"));
        assertTrue(query.contains("IN (3, 6)"), query);
        assertTrue(query.contains("咖啡"), query);
        assertTrue(query.matches("(?s).*status`? = 0.*"), query);
    }

    @Test
    void merchantListStillAcceptsSingleCategory() {
        String query = sql(new AdminMerchantInfoController(), MerchantInfoEntity.class, "list",
                new JSONObject().set("categoryId", 6).set("status", 1));
        assertTrue(query.matches("(?s).*category_id`? = 6.*"), query);
        assertTrue(query.matches("(?s).*status`? = 1.*"), query);
    }

    @Test
    void clearingFiltersDoesNotLeaveAWhereClause() {
        String query = sql(new AdminMerchantCategoryController(), MerchantCategoryEntity.class, "list",
                new JSONObject().set("name", "").set("status", null).set("showOnFront", null));
        assertFalse(query.contains("WHERE"), query);
    }
}
