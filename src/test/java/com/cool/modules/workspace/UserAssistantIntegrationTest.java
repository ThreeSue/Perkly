package com.cool.modules.workspace;

import static com.cool.modules.ai.app.UserAssistantModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.app.*;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.entity.CouponPublicationEntity;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 用事务回滚测试数据验证用户AI真实查询上架过滤及券订单归属
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
@TestPropertySource(properties="perkly.ai.worker-enabled=false")
class UserAssistantIntegrationTest extends FeatureModulesIntegrationTest {
    @Autowired UserAssistantQueries assistantQueries;
    @MockBean UserAssistantModel assistantModel;

    @Test void userAssistantHttpAuthAndPublishedResults() throws Exception {
        fixture("有效券",0,1);fixture("下架券",0,2);
        when(assistantModel.interpret(anyString(),any(),anyList(),nullable(Target.class),anyBoolean()))
            .thenReturn(new Plan(Action.COUPONS,true,new Conditions(null,prefix,PriceMode.FREE,null,null,true,false),List.of(),null,null));
        when(assistantModel.answer(anyString(),any(),any())).thenReturn("请查看真实优惠券卡片。");
        mvc.perform(post("/app/ai/assistant/chat").contentType("application/json").content("{\"message\":\"找券\"}"))
            .andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/app/ai/assistant/chat").header("Authorization",token(customer,UserTypeEnum.APP))
            .contentType("application/json").content("{\"message\":\"找券\"}"))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.cards.length()").value(1))
            .andExpect(jsonPath("$.data.cards[0].title").value(prefix+"有效券"));
    }
    @Test void userAssistantRealPrivateIsolationAndHistoricalRules() {
        var template=fixture("历史规则券",0,1);
        var claimed=trade.claim(customer,template.getId(),1,UUID.randomUUID().toString());
        Long orderId=((Number)claimed.get("id")).longValue();
        Long walletId=((Number)((List<?>)claimed.get("couponIds")).get(0)).longValue();
        assertThrows(CoolException.class,()->assistantQueries.query(otherCustomer,Action.RULES,Conditions.empty(),null,new Target(Kind.WALLET,walletId),null));
        assertThrows(CoolException.class,()->assistantQueries.query(otherCustomer,Action.ORDERS,Conditions.empty(),null,new Target(Kind.ORDER,orderId),null));
        var disabled=new CouponTemplateEntity();disabled.setId(template.getId());disabled.setStatus(2);coupons.update(disabled);
        var historical=assistantQueries.query(customer,Action.RULES,Conditions.empty(),null,new Target(Kind.WALLET,walletId),null);
        assertEquals(prefix+"历史规则券",historical.cards().get(0).title());assertFalse(historical.cards().get(0).purchasable());
        assertTrue(historical.cards().get(0).facts().stream().anyMatch(f->"抵扣金额".equals(f.label()) && "10.00元".equals(f.value())));
        var current=assistantQueries.query(customer,Action.COUPONS,new Conditions(null,prefix,null,null,null,true,false),null,null,null);
        assertTrue(current.cards().isEmpty());
    }
    private CouponTemplateEntity fixture(String name,int cents,int published) {
        var c=new CouponTemplateEntity();c.setMerchantId(merchant);c.setName(prefix+name);c.setType("CASH");
        c.setSalePrice(BigDecimal.valueOf(cents,2));c.setDeductAmount(BigDecimal.TEN);c.setScopeType(0);
        c.setValidityType("AFTER_ISSUE");c.setValidDays(30);c.setIssuedCount(0L);c.setPerCustomerLimit(2);c.setRuleVersion(1);c.setStatus(1);
        coupons.insertSelective(c);
        var publication=new CouponPublicationEntity();publication.setMerchantId(merchant);publication.setCouponTemplateId(c.getId());
        publication.setChannel("PLATFORM");publication.setAuditStatus(2);publication.setPublishStatus(published);publication.setPublishedVersion(1);publication.setPublishedAt(new Date());publications.insertSelective(publication);
        return c;
    }
}
