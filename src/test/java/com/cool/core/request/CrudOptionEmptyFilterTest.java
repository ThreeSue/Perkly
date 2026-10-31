package com.cool.core.request;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import static com.cool.modules.merchant.entity.table.MerchantStoreEntityTableDef.MERCHANT_STORE_ENTITY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证门店列表空筛选不产生额外条件且禁用状态零值仍然生效
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 15:00:00
 */
class CrudOptionEmptyFilterTest {
    private String sql(String body) {
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(Environment.class)).thenReturn(new MockEnvironment());
            return new CrudOption<MerchantStoreEntity>(JSONUtil.parseObj(body))
                .fieldEq(MERCHANT_STORE_ENTITY.MERCHANT_ID, MERCHANT_STORE_ENTITY.CATEGORY_ID, MERCHANT_STORE_ENTITY.STATUS)
                .keyWordLikeFields(MERCHANT_STORE_ENTITY.NAME, MERCHANT_STORE_ENTITY.PHONE, MERCHANT_STORE_ENTITY.ADDRESS)
                .getQueryWrapper(MerchantStoreEntity.class).toSQL();
        }
    }

    @Test void explicitNullFiltersBehaveLikeOmittedFilters() {
        assertEquals(sql("{\"merchantId\":1113}"),
            sql("{\"merchantId\":1113,\"status\":null,\"categoryId\":null,\"keyWord\":null,\"name\":null}"));
    }

    @Test void disabledStatusZeroIsStillFiltered() {
        assertNotEquals(sql("{\"merchantId\":1113}"), sql("{\"merchantId\":1113,\"status\":0}"));
        assertTrue(sql("{\"merchantId\":1113,\"status\":0}").contains("0"));
    }

    @Test void individualNullSearchFieldsAreNotLiteralSearchTerms() {
        assertEquals(sql("{\"merchantId\":1113}"), sql("{\"merchantId\":1113,\"name\":null,\"phone\":null,\"address\":null}"));
    }

    @Test void realKeywordStillBuildsSearchAcrossFields() {
        String query = sql("{\"merchantId\":1113,\"keyWord\":\"金山\"}");
        assertTrue(query.contains("金山"));
        assertTrue(query.contains(" OR "));
    }

    @Test void literalNullTextRemainsAValidKeyword() {
        assertTrue(sql("{\"merchantId\":1113,\"keyWord\":\"null\"}").contains("%null%"));
    }
}
