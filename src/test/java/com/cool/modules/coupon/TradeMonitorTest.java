package com.cool.modules.coupon;
import com.cool.core.exception.CoolException;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.coupon.controller.admin.AdminPurchaseOrderController;
import com.cool.modules.coupon.controller.admin.AdminConsumptionOrderController;
import com.cool.modules.coupon.controller.admin.req.PurchaseOrderQuery;
import com.cool.modules.coupon.service.*;
import com.cool.modules.coupon.service.impl.PurchaseOrderReadServiceImpl;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.merchant.service.MerchantStaffService;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.paginate.Page;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 不接触真实业务数据验证交易只读查询金额超时筛选与身份隔离
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */
class TradeMonitorTest {
    @BeforeAll static void utilityContext(){
        var context=new org.springframework.context.support.GenericApplicationContext();
        context.registerBean(com.cool.core.cache.CoolCache.class,()->mock(com.cool.core.cache.CoolCache.class));
        context.refresh();new cn.hutool.extra.spring.SpringUtil().setApplicationContext(context);
    }
    PurchaseOrderMapper orders=mock(PurchaseOrderMapper.class);
    PurchaseOrderItemMapper items=mock(PurchaseOrderItemMapper.class);
    MerchantInfoMapper merchants=mock(MerchantInfoMapper.class);
    UserInfoMapper people=mock(UserInfoMapper.class);
    PurchaseOrderReadServiceImpl service=new PurchaseOrderReadServiceImpl(orders,items,merchants,people);
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private void identity(UserTypeEnum type,String permission){
        var grants=permission==null?List.<org.springframework.security.core.GrantedAuthority>of():List.<org.springframework.security.core.GrantedAuthority>of(new SimpleGrantedAuthority(permission));
        var user=new JwtUser(12L,"operator","",grants,true);user.setUserTypeEnum(type);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,grants));
    }
    @Test void rejectsInvalidPageStatusAndDate(){
        var p=new PurchaseOrderQuery();p.setSize(101);assertThrows(CoolException.class,p::validate);
        p.setSize(20);p.setStatus("wrong");assertThrows(CoolException.class,p::validate);
        p.setStatus(null);p.setMerchantId(-1L);assertThrows(CoolException.class,p::validate);
        p.setMerchantId(null);p.setStartTime(new Date(20));p.setEndTime(new Date(10));assertThrows(CoolException.class,p::validate);
        verifyNoInteractions(orders);
    }
    @Test void detailKeepsYuanAndComputesExpiryWithoutMutatingDatabase() throws Exception {
        var row=new PurchaseOrderEntity();row.setId(1L);row.setMerchantId(3L);row.setCustomerId(4L);row.setCreateTime(new Date(System.currentTimeMillis()-1800000));row.setOrderStatus("PENDING");row.setTotalAmount(new BigDecimal("9.90"));
        when(orders.selectOneById(1L)).thenReturn(row);
        var merchant=new MerchantInfoEntity();merchant.setId(3L);merchant.setName("真实商家名称");
        var person=new UserInfoEntity();person.setId(4L);person.setNickName("顾客昵称");
        var item=new PurchaseOrderItemEntity();item.setOrderId(1L);item.setCouponName("套餐");item.setQuantity(2);item.setUnitPrice(new BigDecimal("4.95"));item.setSubtotal(new BigDecimal("9.90"));
        when(items.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(item));
        when(merchants.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(merchant));when(people.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(person));
        var result=service.info(1L);assertEquals("EXPIRED",result.status());assertEquals("已超时",result.statusText());assertEquals(new BigDecimal("9.90"),result.totalAmount());assertEquals("真实商家名称",result.merchantName());assertEquals(2,result.items().get(0).quantity());assertEquals("PENDING",row.getOrderStatus());
        var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);assertFalse(json.contains("phone"));assertFalse(json.contains("password"));assertFalse(json.contains("couponCode"));
        verify(orders,never()).update(any(PurchaseOrderEntity.class));
    }
    @Test void emptyPageDoesNotFetchNamesOrDetails(){
        var page=new Page<PurchaseOrderEntity>(1,20,0);page.setRecords(List.of());
        when(orders.paginate(eq(1),eq(20),any(QueryWrapper.class))).thenReturn(page);
        assertTrue(service.page(new PurchaseOrderQuery()).getList().isEmpty());verifyNoInteractions(items,merchants,people);
    }
    @Test void filtersExpiryAndMerchantTogetherInDatabase(){
        var page=new Page<PurchaseOrderEntity>(1,20,0);page.setRecords(List.of());
        when(orders.paginate(eq(1),eq(20),any(QueryWrapper.class))).thenReturn(page);
        var p=new PurchaseOrderQuery();p.setStatus("EXPIRED");p.setMerchantId(99L);p.setOrderNo(" Q123 ");p.setStartTime(new Date(0));service.page(p);
        var query=ArgumentCaptor.forClass(QueryWrapper.class);verify(orders).paginate(eq(1),eq(20),query.capture());
        String sql=query.getValue().toSQL();assertTrue(sql.contains("99"));assertTrue(sql.contains("Q123"));assertTrue(sql.contains("expires_at"));assertTrue(sql.contains("IS NULL"));assertTrue(sql.contains("PENDING"));
    }
    @Test void missingOrInvalidDetailsFail(){assertThrows(CoolException.class,()->service.info(0L));assertThrows(CoolException.class,()->service.info(99L));}
    @Test void purchaseControllerRejectsAnonymousAndWrongRoles(){
        var read=mock(PurchaseOrderReadService.class);var controller=new AdminPurchaseOrderController(read);
        assertThrows(CoolException.class,()->controller.info(1L));identity(UserTypeEnum.APP,"coupon:purchaseOrder:info");assertThrows(CoolException.class,()->controller.info(1L));verifyNoInteractions(read);
    }
    @Test void purchasePageAndDetailHaveSeparatePermissions(){
        var read=mock(PurchaseOrderReadService.class);var controller=new AdminPurchaseOrderController(read);
        identity(UserTypeEnum.ADMIN,"coupon:purchaseOrder:page");controller.page(new PurchaseOrderQuery());assertThrows(CoolException.class,()->controller.info(1L));verify(read).page(any());verify(read,never()).info(any());
    }
    @Test void consumptionControllerRejectsOtherIdentities(){
        var read=mock(ConsumptionOrderService.class);var controller=new AdminConsumptionOrderController(read);identity(UserTypeEnum.MERCHANT,"coupon:consumptionOrder:info");assertThrows(CoolException.class,()->controller.info(1L));verifyNoInteractions(read);
    }
    private ConsumptionOrderService consumption(ConsumptionOrderMapper mapper,MerchantAccountService accounts,MerchantStaffService staff){return new ConsumptionOrderService(mapper,mock(CouponTradeService.class),accounts,staff,mock(BaseSysUserMapper.class));}
    @Test void ownerCannotUseMerchantFilterToReadAnotherMerchant(){
        var mapper=mock(ConsumptionOrderMapper.class);var accounts=mock(MerchantAccountService.class);var staff=mock(MerchantStaffService.class);var owner=new BaseSysUserEntity();owner.setId(3L);owner.setMerchantId(7L);owner.setMerchantRole("OWNER");when(accounts.current(3L)).thenReturn(owner);
        var page=new Page<ConsumptionOrderEntity>(1,20,0);page.setRecords(List.of());when(mapper.paginate(any(Page.class),any(QueryWrapper.class))).thenReturn(page);
        consumption(mapper,accounts,staff).page(3L,new ConsumptionOrderService.PageInput(1,20,999L,null,null,null,null));
        var query=ArgumentCaptor.forClass(QueryWrapper.class);verify(mapper).paginate(any(Page.class),query.capture());String sql=query.getValue().toSQL();assertTrue(sql.contains("7"));assertFalse(sql.contains("999"));
    }
    @Test void staffDetailUsesStoreAndOperatorScope(){
        var mapper=mock(ConsumptionOrderMapper.class);var accounts=mock(MerchantAccountService.class);var staff=mock(MerchantStaffService.class);var user=new BaseSysUserEntity();user.setId(3L);user.setMerchantId(7L);user.setMerchantRole("STAFF");when(accounts.current(3L)).thenReturn(user);when(staff.requireStore(user,null)).thenReturn(19L);
        assertThrows(CoolException.class,()->consumption(mapper,accounts,staff).info(3L,55L));
        var query=ArgumentCaptor.forClass(QueryWrapper.class);verify(mapper).selectOneByQuery(query.capture());String sql=query.getValue().toSQL();assertTrue(sql.contains("store_id"));assertTrue(sql.contains("operator_id"));assertTrue(sql.contains("19"));
    }
    @Test void consumptionRejectsReversedCreationDates(){
        var mapper=mock(ConsumptionOrderMapper.class);var input=new ConsumptionOrderService.PageInput(1,20,null,null,null,null,null,new Date(20),new Date(10));
        assertThrows(CoolException.class,()->consumption(mapper,mock(MerchantAccountService.class),mock(MerchantStaffService.class)).adminPage(input));verifyNoInteractions(mapper);
    }
}
