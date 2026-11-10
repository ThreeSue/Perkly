package com.cool.modules.ai.app;

import static com.cool.modules.ai.app.UserAssistantModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.coupon.mapper.CustomerCouponMapper;
import com.cool.modules.coupon.service.CouponTradeService;
import com.cool.modules.merchant.service.AppDiscoveryService;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证可领购推荐价格筛选本人记录边界和历史券规则白名单
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
class UserAssistantQueriesTest {
    @Test void personalRecordTotalSurvivesCardPagination() {
        var p=page(List.of(Map.of("id",1L,"name","测试订单")));
        p.getPagination().setTotal(27L);
        when(trade.orders(7L,1,8,"PAID")).thenReturn(p);
        var result=queries.query(7L,Action.ORDERS,Conditions.empty(),null,null,"PAID");
        assertTrue(result.partial());assertEquals(1,result.cards().size());
        assertTrue(result.note().contains("全量总数为27条"));
        assertTrue(result.note().contains("展示1条"));
    }
    AppDiscoveryService discovery=mock(AppDiscoveryService.class);
    CouponTradeService trade=mock(CouponTradeService.class);
    CustomerCouponMapper owned=mock(CustomerCouponMapper.class);
    UserAssistantQueries queries=new UserAssistantQueries(discovery,trade,owned);
    private Map<String,Object> coupon(long id,long price,String state){return new HashMap<>(Map.of("id",id,"name","券"+id,"salePrice",price,"saleState",state,"perCustomerLimit",2,"minSpend",2000L));}
    private PageResult<Map<String,Object>> page(List<Map<String,Object>> rows){var p=new PageResult<Map<String,Object>>();p.setList(rows);p.getPagination().setTotal((long)rows.size());return p;}
    @Test void freeAndPaidAreFilteredAndEndedSoldOutNotStartedNeverRecommended(){
        when(discovery.coupons(any())).thenReturn(page(List.of(coupon(1,1000,"AVAILABLE"),coupon(2,0,"AVAILABLE"),coupon(3,0,"ENDED"),coupon(4,0,"SOLD_OUT"),coupon(5,0,"NOT_STARTED"))));
        var free=queries.query(7L,Action.COUPONS,new Conditions(null,null,PriceMode.FREE,null,null,true,false),null,null,null);
        assertEquals(List.of(2L),free.cards().stream().map(Card::id).toList());
        var paid=queries.query(7L,Action.COUPONS,new Conditions(null,null,PriceMode.PAID,null,null,true,false),null,null,null);
        assertEquals(List.of(1L),paid.cards().stream().map(Card::id).toList());
    }
    @Test void limitAndConsumptionThresholdAreRespected(){
        when(discovery.coupons(any())).thenReturn(page(List.of(coupon(1,0,"AVAILABLE"))));
        assertTrue(queries.query(7L,Action.COUPONS,new Conditions(null,null,null,null,1000L,false,false),null,null,null).cards().isEmpty());
        when(owned.selectCountByQuery(any())).thenReturn(2L);
        assertTrue(queries.query(7L,Action.COUPONS,Conditions.empty(),null,null,null).cards().isEmpty());
    }
    @Test void privateRecordsAlwaysReceiveAuthenticatedCustomerAndAccessDenialIsNotSwallowed(){
        when(trade.walletInfo(7L,99L)).thenThrow(new CoolException("无权访问"));
        assertThrows(CoolException.class,()->queries.query(7L,Action.RULES,Conditions.empty(),null,new Target(Kind.WALLET,99L),null));
        verify(trade).walletInfo(7L,99L);verifyNoInteractions(discovery);
        when(trade.orders(7L,1,8,null)).thenReturn(page(List.of()));queries.query(7L,Action.ORDERS,Conditions.empty(),null,null,null);verify(trade).orders(7L,1,8,null);
    }
    @Test void expiredWalletExplainsSnapshotButNeverExposesRedeemSecrets(){
        var row=new HashMap<String,Object>(Map.of("id",9L,"name","过期券","stateText","已过期","code","SECRET-CODE","qrCodeContent","SECRET-QR","rule",Map.of("description","仅周末使用","deductAmount",1000L)));
        when(trade.walletInfo(7L,9L)).thenReturn(row);
        var result=queries.query(7L,Action.RULES,Conditions.empty(),null,new Target(Kind.WALLET,9L),null);
        assertFalse(result.cards().get(0).purchasable());assertTrue(result.toString().contains("仅周末使用"));assertFalse(result.toString().contains("SECRET"));assertTrue(result.note().contains("快照"));
    }
    @Test void missingCoordinatesAreNotPretendedToBeNearbyAndAreaSearchUsesAddress(){
        var row=new HashMap<String,Object>(Map.of("id",3L,"name","福州店","address","福建福州鼓楼区"));when(discovery.stores(any())).thenReturn(page(List.of(row)));
        assertEquals(1,queries.query(7L,Action.STORES,new Conditions("福州",null,null,null,null,false,true),null,null,null).cards().size());
        assertTrue(queries.query(7L,Action.STORES,new Conditions(null,null,null,null,null,false,true),new Location(new java.math.BigDecimal("119.3"),new java.math.BigDecimal("26.1")),null,null).cards().isEmpty());
    }
    @Test void cheapSortAndEmptyResultsRemainTruthful(){
        when(discovery.coupons(any())).thenReturn(page(List.of(coupon(1,1500,"AVAILABLE"),coupon(2,0,"AVAILABLE"),coupon(3,500,"AVAILABLE"))));
        assertEquals(List.of(2L,3L,1L),queries.query(7L,Action.COUPONS,new Conditions(null,null,null,null,null,true,false),null,null,null).cards().stream().map(Card::id).toList());
        when(discovery.coupons(any())).thenReturn(page(List.of()));assertTrue(queries.query(7L,Action.COUPONS,Conditions.empty(),null,null,null).cards().isEmpty());
    }
}
