package com.cool.modules.workspace;
import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.security.jwt.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.*;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.support.entity.*;
import com.cool.modules.support.mapper.*;
import com.cool.modules.support.service.*;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.wallet.service.*;
import com.cool.modules.workspace.entity.WorkspaceGrantEntity;
import com.cool.modules.workspace.mapper.WorkspaceGrantMapper;
import com.cool.modules.workspace.service.*;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.coupon.service.CouponTradeService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.Filter;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用事务回滚数据验证反馈聊天储值与折扣活动完整接口和账务隔离
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:50:51
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"sms-auth.access-key-id=features-test-unused","sms-auth.access-key-secret=features-test-unused"})
@ActiveProfiles("local") @Transactional
class FeatureModulesIntegrationTest {
    @Autowired UserInfoMapper customers;
    @Autowired BaseSysUserMapper users;
    @Autowired MerchantInfoMapper merchants;
    @Autowired MerchantStoreMapper stores;
    @Autowired MerchantCategoryMapper categories;
    @Autowired MerchantStoreCategoryMapper storeTypes;
    @Autowired MerchantUserStoreMapper bindings;
    @Autowired WorkspaceLegacyAccountBridge bridge;
    @Autowired WorkspaceAuthorization auth;
    @Autowired WorkspaceGrantMapper grants;
    @Autowired com.cool.modules.workspace.mapper.WorkspaceUserScopeMapper userScopes;
    @Autowired SupportImageMapper imageRows;
    @Autowired UserFeedbackService feedback;
    @Autowired StoreChatService chats;
    @Autowired PlatformWalletService wallet;
    @Autowired WalletActivityService activity;
    @Autowired WalletActivityMapper activityRows;
    @Autowired WalletBillMapper billRows;
    @Autowired WalletTransactionMapper transactions;
    @Autowired PlatformWalletMapper walletRows;
    @Autowired BaseSysUserRoleMapper roleLinks;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired SupportImageService imageService;
    @Autowired com.cool.core.config.LocalFileProperties localFiles;
    @Autowired CouponTemplateMapper coupons;
    @Autowired CouponPublicationMapper publications;
    @Autowired CouponTradeService trade;
    @Autowired com.cool.modules.coupon.service.ConsumptionOrderService consumption;
    @Autowired ConsumptionOrderMapper consumptionRows;
    @Autowired ActivityAudienceService activityAudience;
    @Autowired ActivityNoticeService activityNotices;
    @Autowired WalletActivityEventMapper activityEvents;
    @Autowired WalletActivityDeliveryMapper activityDeliveries;
    @Autowired WalletActivityExclusionMapper activityExclusions;
    @Autowired WalletActivityContactMapper activityContacts;
    @Autowired com.cool.modules.workspace.mapper.WorkspaceNoticeMapper noticeRows;
    @Autowired com.cool.modules.workspace.mapper.WorkspaceNoticeRecipientMapper noticeRecipients;
    @org.springframework.boot.test.mock.mockito.SpyBean WorkspaceNoticeService autoNotice;
    @Autowired CoolCache cache;
    @Autowired JwtTokenUtil tokens;
    @Autowired WebApplicationContext web;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.modules.user.util.UserSmsUtil sms;
    String prefix="feat_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
    Long customer,otherCustomer,merchant,store,otherStore,staff,otherStaff,owner,admin;
    List<String> keys=new ArrayList<>();MockMvc mvc;
    @BeforeEach void setup(){
        var c=new MerchantCategoryEntity();c.setName(prefix);c.setSort(0);c.setStatus(1);categories.insertSelective(c);
        var type=new MerchantStoreCategoryEntity();type.setName(prefix);type.setSort(0);type.setStatus(1);storeTypes.insertSelective(type);
        var m=new MerchantInfoEntity();m.setName(prefix);m.setCategoryId(c.getId());m.setContactName("测试");m.setContactPhone("13800000001");m.setStatus(1);merchants.insertSelective(m);merchant=m.getId();
        store=store(type.getId());otherStore=store(type.getId());
        owner=account("owner","OWNER",null);staff=account("staff","STAFF",store);otherStaff=account("other","STAFF",otherStore);admin=account("admin",null,null);
        customer=customer();otherCustomer=customer();
        for(var menu:auth.catalog()){var g=new WorkspaceGrantEntity();g.setSubjectType("MERCHANT");g.setSubjectId(merchant);g.setMenuId(menu.getId());g.setAllowed(true);grants.insert(g);
            if("activity:apply".equals(menu.getPerms())){g=new WorkspaceGrantEntity();g.setSubjectType("USER");g.setSubjectId(owner);g.setMenuId(menu.getId());g.setAllowed(true);grants.insert(g);}}
        mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();
        platform();
    }
    Long store(Long type){
        var s=new MerchantStoreEntity();s.setMerchantId(merchant);s.setCategoryId(type);s.setName(prefix+"店");s.setAddress("测试地址");s.setPhone("13800000001");s.setStatus(1);stores.insertSelective(s);return s.getId();
    }
    Long account(String suffix,String role,Long store){
        var u=new BaseSysUserEntity();u.setUsername(prefix+suffix);u.setName("测试"+suffix);u.setNickName(u.getName());u.setPassword("not-login-password");u.setPasswordV(1);u.setStatus(1);
        if(role!=null){u.setMerchantId(merchant);u.setMerchantRole(role);u.setAccountType("OWNER".equals(role)?"MERCHANT":"STORE");u.setStoreScope("OWNER".equals(role)?"ALL":"SELF");}else u.setAccountType("PLATFORM");
        users.insertSelective(u);if(role!=null)bridge.bind(u,role,false);
        if(store!=null){var b=new MerchantUserStoreEntity();b.setUserId(u.getId());b.setMerchantId(merchant);b.setStoreId(store);bindings.insert(b);}
        return u.getId();
    }
    Long customer(){var c=new UserInfoEntity();c.setNickName(prefix+"顾客");c.setStatus(1);customers.insertSelective(c);return c.getId();}
    JwtUser principal(Long id,UserTypeEnum type,String... perms){
        List<GrantedAuthority> a=new ArrayList<>();a.add(new SimpleGrantedAuthority("ROLE_"+type.name()));for(String p:perms)a.add(new SimpleGrantedAuthority(p));
        String username=type==UserTypeEnum.APP?String.valueOf(id):users.selectOneById(id).getUsername();
        var u=new JwtUser(id,username,"",a,true);u.setUserTypeEnum(type);return u;
    }
    void platform(){var u=principal(admin,UserTypeEnum.ADMIN,"feedback:manage","activity:manage","wallet:manage");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));}
    String token(Long id,UserTypeEnum type,String... perms){
        var u=principal(id,type,perms);var data=Dict.create().set("userType",type.name()).set("userId",id);
        if(type==UserTypeEnum.APP){String k="app:userDetails:"+id;cache.set(k,u,120);keys.add(k);data.set("credentialVersion",com.cool.modules.user.util.UserPasswordUtil.credentialVersion(customers.selectOneById(id)));}
        else if(type==UserTypeEnum.ADMIN){
            String k="admin:userDetails:"+u.getUsername(),v="admin:passwordVersion:"+id;cache.set(k,u,120);cache.set(v,1,120);keys.add(k);keys.add(v);data.set("username",u.getUsername()).set("passwordVersion",1);
        }else{
            String session=UUID.randomUUID().toString();String k="merchant:session:"+session;cache.set(k,id,120);keys.add(k);
            var actor=users.selectOneById(id);data.set("sessionId",session).set("merchantId",merchant).set("merchantRole",actor.getMerchantRole()).set("passwordVersion",1);
        }
        SecurityContextHolder.clearContext();return tokens.generateToken(data);
    }
    String image(String kind,Long actor){
        String url="http://localhost/test/"+UUID.randomUUID()+".png";var row=new SupportImageEntity();row.setOwnerKind(kind);row.setOwnerId(actor);row.setUrl(url);imageRows.insert(row);return url;
    }
    Long activity(String title,String rate){
        platform();return activity.create(admin,true,new WalletActivityService.Input(title,"测试折扣",new Date(System.currentTimeMillis()-1000),new Date(System.currentTimeMillis()+600000),List.of(store),new BigDecimal(rate)));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();keys.forEach(cache::del);}
    void activityPermission(Long actor,String permission) {
        var menu=auth.catalog().stream().filter(m->permission.equals(m.getPerms())).findFirst().orElseThrow();
        var grant=new WorkspaceGrantEntity();grant.setSubjectType("USER");grant.setSubjectId(actor);
        grant.setMenuId(menu.getId());grant.setAllowed(true);grants.insert(grant);
    }

    @Test void platformParticipationDefaultsExitScopeAndCompletedPaymentHistory() {
        activityPermission(owner,"activity:participate");
        Long id=activity.create(admin,true,new WalletActivityService.Input("默认参与","九折规则",
            new Date(System.currentTimeMillis()-1000),new Date(System.currentTimeMillis()+600000),List.of(store,otherStore),new BigDecimal("9")));
        assertEquals(900L,activity.quote(store,1000).amount());
        wallet.recharge(customer,new PlatformWalletService.Recharge("participate_topup",3000L,null));
        var paid=wallet.createBill(staff,new PlatformWalletService.BillInput("participate_paid",store,1000L,null));
        wallet.consumptionPay(customer,settlementPay("participate_pay",paid.getBillNo(),"WALLET",900));
        String history=billRows.selectOneById(paid.getId()).getSettlementSnapshot();
        var pending=wallet.createBill(staff,new PlatformWalletService.BillInput("participate_pending",store,1000L,null));
        var state=activity.participation(owner,id);
        activity.participate(owner,new ActivityAudienceService.ParticipationInput(id,state.version(),false,List.of(store)));
        var change=activityEvents.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,id)
            .eq(WalletActivityEventEntity::getKind,"PARTICIPATION").eq(WalletActivityEventEntity::getActivityVersion,2));
        var involved=cn.hutool.json.JSONUtil.parseObj(change.getPayload()).getJSONArray("participatingStoreIds").toList(Long.class);
        assertFalse(involved.contains(store));assertTrue(involved.contains(otherStore));
        assertEquals(1000L,activity.quote(store,1000).amount());
        assertEquals(900L,activity.quote(otherStore,1000).amount());
        assertEquals(history,billRows.selectOneById(paid.getId()).getSettlementSnapshot());
        assertThrows(CoolException.class,()->wallet.consumptionPay(customer,settlementPay("participate_pending_pay",pending.getBillNo(),"WALLET",900)));
        state=activity.participation(owner,id);
        activity.participate(owner,new ActivityAudienceService.ParticipationInput(id,state.version(),true,List.of(store)));
        assertEquals(1000L,activity.quote(otherStore,1000).amount());
        var u=users.selectOneById(owner);u.setMerchantRole("MEMBER");u.setStoreScope("SELECTED");users.update(u);
        var scope=new com.cool.modules.workspace.entity.WorkspaceUserScopeEntity();scope.setUserId(owner);scope.setStoreId(store);userScopes.insert(scope);
        var limited=activity.participation(owner,id);
        assertFalse(limited.canExit());
        var closed=stores.selectOneById(otherStore);closed.setStatus(3);stores.update(closed);
        assertFalse(activity.participation(owner,id).canExit(),"已停业但仍属商家的范围不能被绕过");
        assertThrows(CoolException.class,()->activity.participate(owner,new ActivityAudienceService.ParticipationInput(id,limited.version(),false,List.of())));
        assertThrows(CoolException.class,()->activity.participate(owner,new ActivityAudienceService.ParticipationInput(id,limited.version(),null,List.of(otherStore))));
        assertThrows(CoolException.class,()->activity.participation(otherStaff,id));
        var newStore=stores.selectOneById(otherStore);newStore.setId(null);newStore.setName("发布后新店");newStore.setStatus(1);stores.insertSelective(newStore);
        assertEquals(1000L,activity.quote(newStore.getId(),1000).amount());
    }

    @Test void activityContactsRejectForeignAccountsAndUnauthorizedScope() {
        activityPermission(owner,"activity:recipients:manage");
        var foreign=merchants.selectOneById(merchant);foreign.setId(null);foreign.setName(prefix+"外商家");merchants.insertSelective(foreign);
        var user=new BaseSysUserEntity();user.setUsername(prefix+"foreignOwner");user.setName("外商家老板");user.setNickName(user.getName());user.setStatus(1);user.setPassword("not-login");user.setPasswordV(1);
        user.setMerchantId(foreign.getId());user.setMerchantRole("OWNER");user.setAccountType("MERCHANT");user.setStoreScope("ALL");
        users.insertSelective(user);bridge.bind(user,"OWNER",false);
        assertThrows(CoolException.class,()->activityAudience.saveContacts(owner,false,new ActivityAudienceService.ContactInput(merchant,store,List.of(user.getId()))));
        assertThrows(CoolException.class,()->activityAudience.saveContacts(staff,false,new ActivityAudienceService.ContactInput(merchant,store,List.of(staff))));
        activityAudience.saveContacts(owner,false,new ActivityAudienceService.ContactInput(merchant,store,List.of(staff)));
        assertEquals(List.of(staff),activityAudience.contactIds(owner,false,merchant,store));
        var changed=users.selectOneById(staff);changed.setStatus(0);users.update(changed);
        assertTrue(activityAudience.recipients(List.of(new ActivityAudienceService.StoreTarget(store,merchant,"测试店"))).recipients()
            .stream().noneMatch(r->staff.equals(r.id())));
    }

    @Test void automaticActivityNoticesRetryWithoutDuplicatesAndIsolateUnread() {
        activityPermission(owner,"activity:recipients:manage");
        Long manager=account("manager","MANAGER",store);
        Long pending=activity.create(owner,false,new WalletActivityService.Input("待审核","规则",new Date(),
            new Date(System.currentTimeMillis()+600000),List.of(store),null));
        assertEquals(0,activityEvents.selectCountByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,pending)));
        activity.review(admin,new WalletActivityService.Review(pending,false,null,"驳回"));
        assertEquals(0,activityEvents.selectCountByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,pending)));
        Long id=activity.create(admin,true,new WalletActivityService.Input("未来活动","储值九折",
            new Date(System.currentTimeMillis()+600000),new Date(System.currentTimeMillis()+1200000),List.of(store),new BigDecimal("9")));
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        try {
            org.mockito.Mockito.doThrow(new IllegalStateException("模拟站内投递故障")).when(autoNotice).activityNotice(
                org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(owner),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
            activityNotices.tick();
            assertEquals("PUBLISHED",activityRows.selectOneById(id).getStatus());
            var published=activityEvents.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,id).eq(WalletActivityEventEntity::getKind,"PUBLISHED"));
            assertEquals(2,activityDeliveries.selectCountByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getEventId,published.getId())));
            assertEquals(0,autoNotice.unread(staff));
            assertEquals(1,autoNotice.unread(manager));
            var managerDelivery=activityDeliveries.selectOneByQuery(QueryWrapper.create()
                .eq(WalletActivityDeliveryEntity::getEventId,published.getId()).eq(WalletActivityDeliveryEntity::getRecipientId,manager));
            var managerNotice=autoNotice.detail(manager,managerDelivery.getNoticeId());
            assertEquals(id,managerNotice.businessId());
            assertTrue(managerNotice.content().contains("北京时间"));
            assertTrue(managerNotice.content().contains("已排除或退出的门店"));
            org.mockito.Mockito.doCallRealMethod().when(autoNotice).activityNotice(
                org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(owner),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
            platform();activityNotices.retry(published.getId());activityNotices.tick();activityNotices.tick();
            assertEquals(1,autoNotice.unread(owner));
            var ownerDelivery=activityDeliveries.selectOneByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getEventId,published.getId())
                .eq(WalletActivityDeliveryEntity::getRecipientId,owner));
            autoNotice.read(owner,ownerDelivery.getNoticeId());
            assertEquals(0,autoNotice.unread(owner));assertEquals(1,autoNotice.unread(manager));
            assertThrows(CoolException.class,()->autoNotice.detail(otherStaff,ownerDelivery.getNoticeId()));
            var futureEnd=activityEvents.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,id).eq(WalletActivityEventEntity::getKind,"ENDED"));
            activityNotices.retry(futureEnd.getId());activityNotices.tick();
            assertEquals("PENDING",activityEvents.selectOneById(futureEnd.getId()).getStatus());
            activityAudience.saveContacts(owner,false,new ActivityAudienceService.ContactInput(merchant,store,List.of(staff)));
            var reminder=activityEvents.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,id).eq(WalletActivityEventEntity::getKind,"REMINDER"));
            reminder.setNextAttemptAt(new Date(System.currentTimeMillis()-1000));
            reminder.setDueAt(reminder.getNextAttemptAt());activityEvents.update(reminder);
            activityNotices.tick();activityNotices.tick();
            assertEquals(1,autoNotice.unread(staff));assertEquals(0,autoNotice.unread(otherStaff));
            platform();activity.schedule(new WalletActivityService.Schedule(id,true,new Date(System.currentTimeMillis()-60000),new Date(System.currentTimeMillis()-1000)));
            activityNotices.tick();
            assertEquals(1000L,activity.quote(store,1000).amount());
            assertTrue(activityEvents.selectListByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,id)
                .eq(WalletActivityEventEntity::getKind,"ENDED").eq(WalletActivityEventEntity::getActivityVersion,2)).stream().anyMatch(e->"DONE".equals(e.getStatus())));
            Long early=activity("提前结束","9");
            activity.offline(admin,true,early);activityNotices.tick();
            assertTrue(activityEvents.selectListByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,early)
                .eq(WalletActivityEventEntity::getKind,"CLOSED")).stream().anyMatch(e->"DONE".equals(e.getStatus())));
        } finally {
            org.mockito.Mockito.reset(autoNotice);
            cleanupActivityNoticeFixtures(manager);
        }
    }

    void cleanupActivityNoticeFixtures(Long manager) {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
            var activityIds=activityRows.selectListByQuery(QueryWrapper.create().in(WalletActivityEntity::getCreatorId,List.of(admin,owner)))
                .stream().map(WalletActivityEntity::getId).toList();
            if(!activityIds.isEmpty()){
                var eventIds=activityEvents.selectListByQuery(QueryWrapper.create().in(WalletActivityEventEntity::getActivityId,activityIds))
                    .stream().map(WalletActivityEventEntity::getId).toList();
                if(!eventIds.isEmpty()){
                    var noticeIds=activityDeliveries.selectListByQuery(QueryWrapper.create().in(WalletActivityDeliveryEntity::getEventId,eventIds))
                        .stream().map(WalletActivityDeliveryEntity::getNoticeId).filter(Objects::nonNull).toList();
                    if(!noticeIds.isEmpty()){
                        noticeRecipients.deleteByQuery(QueryWrapper.create().in(com.cool.modules.workspace.entity.WorkspaceNoticeRecipientEntity::getNoticeId,noticeIds));
                        noticeRows.deleteBatchByIds(noticeIds);
                    }
                    activityDeliveries.deleteByQuery(QueryWrapper.create().in(WalletActivityDeliveryEntity::getEventId,eventIds));
                    activityEvents.deleteBatchByIds(eventIds);
                }
                activityExclusions.deleteByQuery(QueryWrapper.create().in(WalletActivityExclusionEntity::getActivityId,activityIds));
                activityRows.deleteBatchByIds(activityIds);
            }
            activityContacts.deleteByQuery(QueryWrapper.create().eq(WalletActivityContactEntity::getMerchantId,merchant));
            var ids=List.of(owner,staff,otherStaff,admin,manager);
            grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"MERCHANT").eq(WorkspaceGrantEntity::getSubjectId,merchant));
            grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").in(WorkspaceGrantEntity::getSubjectId,ids));
            roleLinks.deleteByQuery(QueryWrapper.create().in(BaseSysUserRoleEntity::getUserId,ids));
            bindings.deleteByQuery(QueryWrapper.create().in(MerchantUserStoreEntity::getUserId,ids));
            users.deleteBatchByIds(ids);
            var m=merchants.selectOneById(merchant);
            var type=stores.selectOneById(store).getCategoryId();
            stores.deleteByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant));
            merchants.deleteById(merchant);categories.deleteById(m.getCategoryId());storeTypes.deleteById(type);
            customers.deleteBatchByIds(List.of(customer,otherCustomer));
        });
    }
    String settlementCode() {
        var c=new CouponTemplateEntity();
        c.setMerchantId(merchant);c.setName("结算代金券");c.setType("CASH");
        c.setDeductAmount(new BigDecimal("20"));c.setSalePrice(new BigDecimal("10"));
        c.setScopeType(0);c.setValidityType("AFTER_ISSUE");c.setValidDays(30);
        c.setStatus(1);c.setRuleVersion(1);c.setIssuedCount(0L);coupons.insertSelective(c);
        var pub=new CouponPublicationEntity();pub.setMerchantId(merchant);pub.setCouponTemplateId(c.getId());
        pub.setChannel("PLATFORM");pub.setAuditStatus(2);pub.setPublishStatus(1);
        pub.setPublishedVersion(1);pub.setPublishedAt(new Date());publications.insertSelective(pub);
        var order=trade.create(customer,c.getId(),1,UUID.randomUUID().toString());
        var paid=trade.simulatePay(customer,((Number)order.get("id")).longValue(),"SUCCESS");
        Long id=((Number)((List<?>)paid.get("couponIds")).get(0)).longValue();
        return (String)trade.walletInfo(customer,id).get("code");
    }
    PlatformWalletService.ConsumptionPay settlementPay(String key,String bill,String mode,long amount) {
        return new PlatformWalletService.ConsumptionPay(key,bill,mode,amount,"SUCCESS");
    }
    @Test void settlementStacksCouponAndBestWalletActivityAndRetriesOnce() {
        String code=settlementCode();
        activity("九折","9");activity("较小优惠","9.5");
        wallet.recharge(customer,new PlatformWalletService.Recharge("stack_recharge",10000L,null));
        var bill=wallet.createBill(staff,new PlatformWalletService.BillInput("stack_open",store,10000L,"消费"));
        bill=wallet.applyCoupon(staff,new PlatformWalletService.ApplyCoupon(bill.getId(),1,code));
        assertEquals(8000L,bill.getRemainingAmount());
        var same=wallet.applyCoupon(staff,new PlatformWalletService.ApplyCoupon(bill.getId(),1,code));
        assertEquals(bill.getConsumptionOrderId(),same.getConsumptionOrderId());
        var request=settlementPay("stack_payment",bill.getBillNo(),"WALLET",7200);
        assertEquals(7200L,wallet.consumptionQuote(customer,request).payment().amount());
        Long linked=bill.getConsumptionOrderId();
        assertThrows(CoolException.class,()->consumption.confirm(staff,
            new com.cool.modules.coupon.service.ConsumptionOrderService.ConfirmInput(linked,1,8000L)));
        var completed=wallet.consumptionPay(customer,request);
        assertEquals(7200L,completed.getPaidAmount());assertEquals(2800L,wallet.balance(customer));
        assertEquals("COMPLETED",consumptionRows.selectOneById(linked).getStatus());
        assertEquals(7200L,consumptionRows.selectOneById(linked).getReceivedAmount());
        assertEquals(completed.getId(),wallet.consumptionPay(customer,request).getId());
        assertEquals(1,transactions.selectCountByQuery(QueryWrapper.create().eq(WalletTransactionEntity::getBillId,bill.getId())));
    }
    @Test void closingDiscountRequiresReconfirmationAndKeepsCouponAndCompletedSnapshot() {
        String code=settlementCode();Long activityId=activity("可关闭折扣","9");
        wallet.recharge(customer,new PlatformWalletService.Recharge("switch_recharge",10000L,null));
        var b=wallet.createBill(staff,new PlatformWalletService.BillInput("switch_open",store,10000L,null));
        b=wallet.applyCoupon(staff,new PlatformWalletService.ApplyCoupon(b.getId(),1,code));
        var old=settlementPay("switch_payment",b.getBillNo(),"WALLET",7200);
        assertEquals(7200L,wallet.consumptionQuote(customer,old).payment().amount());
        activity.schedule(new WalletActivityService.Schedule(activityId,false,new Date(System.currentTimeMillis()-1000),new Date(System.currentTimeMillis()+60000)));
        assertThrows(CoolException.class,()->wallet.consumptionPay(customer,old));
        assertEquals(10000L,wallet.balance(customer));
        var done=wallet.consumptionPay(customer,settlementPay("switch_payment",b.getBillNo(),"WALLET",8000));
        assertEquals(2000L,done.getCouponDiscount());assertEquals(8000L,done.getPaidAmount());
        String snapshot=done.getSettlementSnapshot();
        activity.schedule(new WalletActivityService.Schedule(activityId,true,new Date(System.currentTimeMillis()+60000),new Date(System.currentTimeMillis()+120000)));
        assertEquals(snapshot,billRows.selectOneById(done.getId()).getSettlementSnapshot());
        assertEquals(8000L,activity.quote(store,8000).amount());
    }
    @Test void simulatedConsumptionFailureDoesNotRedeemAndSuccessNeedsNoWallet() {
        var b=wallet.createBill(staff,new PlatformWalletService.BillInput("simulate_open",store,10000L,null));
        b=wallet.applyCoupon(staff,new PlatformWalletService.ApplyCoupon(b.getId(),1,settlementCode()));
        final String no=b.getBillNo();
        assertThrows(CoolException.class,()->wallet.consumptionPay(otherCustomer,settlementPay("foreign_pay",no,"SIMULATED",8000)));
        wallet.consumptionPay(customer,new PlatformWalletService.ConsumptionPay("simulate_pay",no,"SIMULATED",8000L,"FAILED"));
        assertEquals("PENDING",consumptionRows.selectOneById(b.getConsumptionOrderId()).getStatus());
        var paid=wallet.consumptionPay(customer,settlementPay("simulate_pay",no,"SIMULATED",8000));
        assertEquals("SIMULATED",paid.getPaymentMode());assertEquals(0L,wallet.balance(customer));
        assertEquals(0,transactions.selectCountByQuery(QueryWrapper.create().eq(WalletTransactionEntity::getCustomerId,customer)));
        assertThrows(CoolException.class,()->wallet.consumptionPay(customer,settlementPay("another_pay",no,"WALLET",8000)));
    }
    @Test void cancellationReleasesCouponAndLegacyPendingCanBeImported() {
        String code=settlementCode();
        var b=wallet.createBill(staff,new PlatformWalletService.BillInput("cancel_open",store,10000L,null));
        b=wallet.applyCoupon(staff,new PlatformWalletService.ApplyCoupon(b.getId(),1,code));
        wallet.cancelBill(staff,b.getId());
        assertNull(consumptionRows.selectOneById(b.getConsumptionOrderId()).getActiveCouponId());
        var legacy=consumption.prepare(staff,new com.cool.modules.coupon.service.ConsumptionOrderService.PrepareInput(store,code,10000L,null,"legacy_prepare"));
        var imported=wallet.importConsumption(staff,legacy.id());
        assertEquals(imported.getId(),wallet.importConsumption(staff,legacy.id()).getId());
        wallet.consumptionPay(customer,settlementPay("legacy_pay",imported.getBillNo(),"SIMULATED",8000));
        assertEquals("COMPLETED",consumptionRows.selectOneById(legacy.id()).getStatus());
    }
    @Test void unifiedHttpPaymentAndSchedulePermissions() throws Exception {
        var bill=wallet.createBill(staff,new PlatformWalletService.BillInput("http_unified",store,500L,null));
        String app=token(customer,UserTypeEnum.APP);
        String body="{\"requestId\":\"http_unified_pay\",\"billNo\":\""+bill.getBillNo()+"\",\"paymentMode\":\"SIMULATED\",\"expectedAmount\":500}";
        mvc.perform(post("/app/wallet/consumption/quote").header("Authorization",app).contentType("application/json").content(body)).andExpect(jsonPath("$.data.payment.amount").value(500));
        mvc.perform(post("/app/wallet/consumption/pay").header("Authorization",app).contentType("application/json").content(body)).andExpect(jsonPath("$.data.status").value("PAID"));
        String denied=token(admin,UserTypeEnum.ADMIN);
        mvc.perform(post("/admin/wallet/activities/schedule").header("Authorization",denied).contentType("application/json").content("{}")).andExpect(jsonPath("$.code").value(403));
        String staffToken=token(otherStaff,UserTypeEnum.MERCHANT);
        mvc.perform(post("/merchant/governance/wallet/bills/coupon").header("Authorization",staffToken).contentType("application/json").content("{\"id\":"+bill.getId()+",\"version\":1,\"code\":\"invalid\"}")).andExpect(jsonPath("$.code").value(403));
    }
    @Test void fullyCoveredCouponPaysZeroAndSimulatedRequestCannotPayAnotherBill() {
        var zero=wallet.createBill(staff,new PlatformWalletService.BillInput("zero_open",store,1000L,null));
        zero=wallet.applyCoupon(staff,new PlatformWalletService.ApplyCoupon(zero.getId(),1,settlementCode()));
        var done=wallet.consumptionPay(customer,settlementPay("zero_payment",zero.getBillNo(),"WALLET",0));
        assertEquals(0L,done.getPaidAmount());
        assertEquals(0L,wallet.balance(customer));
        assertEquals("COMPLETED",consumptionRows.selectOneById(done.getConsumptionOrderId()).getStatus());
        var first=wallet.createBill(staff,new PlatformWalletService.BillInput("key_first",store,500L,null));
        var second=wallet.createBill(staff,new PlatformWalletService.BillInput("key_second",store,500L,null));
        wallet.consumptionPay(customer,settlementPay("same_simulated_key",first.getBillNo(),"SIMULATED",500));
        assertThrows(CoolException.class,()->wallet.consumptionPay(customer,settlementPay("same_simulated_key",second.getBillNo(),"SIMULATED",500)));
        assertEquals("PENDING",billRows.selectOneById(second.getId()).getStatus());
    }
    @Test void feedbackImageOwnershipSingleReplyAndCustomerIsolation(){
        String url=image("APP",customer);Long id=feedback.submit(customer,new UserFeedbackService.Submit("feedback_123","问题",List.of(url)));
        assertEquals(id,feedback.submit(customer,new UserFeedbackService.Submit("feedback_123","问题",List.of(url))));
        assertThrows(CoolException.class,()->feedback.detail(otherCustomer,id));
        assertThrows(CoolException.class,()->feedback.submit(otherCustomer,new UserFeedbackService.Submit("feedback_123","借图",List.of(url))));
        feedback.reply(admin,new UserFeedbackService.Reply(id,"已处理"));assertEquals("REPLIED",feedback.detail(customer,id).getStatus());
        feedback.reply(admin,new UserFeedbackService.Reply(id,"已处理"));
        assertThrows(CoolException.class,()->feedback.reply(admin,new UserFeedbackService.Reply(id,"覆盖")));
    }
    @Test void feedbackHttpAndAnonymousDenied() throws Exception {
        String app=token(customer,UserTypeEnum.APP);
        String submitted=mvc.perform(post("/app/support/feedback/submit").header("Authorization",app).contentType("application/json").content("{\"requestId\":\"feedback_http\",\"content\":\"问题\"}")).andExpect(jsonPath("$.code").value(1000)).andReturn().getResponse().getContentAsString();
        Long id=cn.hutool.json.JSONUtil.parseObj(submitted).getLong("data");
        mvc.perform(get("/app/support/feedback/page").header("Authorization",app)).andExpect(jsonPath("$.data.total").value(1));
        String adminToken=token(admin,UserTypeEnum.ADMIN,"feedback:manage");
        mvc.perform(post("/admin/support/feedback/reply").header("Authorization",adminToken).contentType("application/json").content("{\"id\":"+id+",\"content\":\"已处理\"}")).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(get("/app/support/feedback/detail?id="+id).header("Authorization",app)).andExpect(jsonPath("$.data.status").value("REPLIED"));
        mvc.perform(get("/app/support/feedback/page")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/support/feedback/page").header("Authorization",app)).andExpect(status().isForbidden());
    }
    @Test void chatAllStaffReplyAndCrossStoreAndCustomerRejected(){
        Long id=chats.open(customer,store);assertEquals(id,chats.open(customer,store));
        Long a=chats.send("APP",customer,new StoreChatService.Send(id,"message_123","你好",List.of()));
        assertEquals(a,chats.send("APP",customer,new StoreChatService.Send(id,"message_123","你好",List.of())));
        String url=image("WORKSPACE",staff);
        chats.send("WORKSPACE",staff,new StoreChatService.Send(id,"message_234","您好",List.of(url)));
        assertEquals(2,chats.messages("APP",customer,id,null,null,50).size());
        assertEquals(1,chats.messages("APP",customer,id,a,null,50).size());
        assertThrows(CoolException.class,()->chats.messages("APP",otherCustomer,id,null,null,50));
        assertThrows(CoolException.class,()->chats.send("WORKSPACE",otherStaff,new StoreChatService.Send(id,"message_bad","越店",List.of())));
        assertThrows(CoolException.class,()->chats.send("APP",customer,new StoreChatService.Send(id,"message_123","冲突",List.of())));
    }
    @Test void chatStaffTokenAndAppTokenReachCorrectRoutes() throws Exception {
        Long id=chats.open(customer,store);String staffToken=token(staff,UserTypeEnum.MERCHANT);
        mvc.perform(post("/merchant/governance/chats/send").header("Authorization",staffToken).contentType("application/json").content("{\"chatId\":"+id+",\"requestId\":\"chat_http_123\",\"content\":\"回复\"}")).andExpect(jsonPath("$.code").value(1000));
        String app=token(customer,UserTypeEnum.APP);
        mvc.perform(get("/app/support/chats/messages?chatId="+id).header("Authorization",app)).andExpect(jsonPath("$.data[0].content").value("回复"));
        mvc.perform(get("/merchant/governance/chats/page").header("Authorization",app)).andExpect(status().isUnauthorized());
    }
    @Test void rechargeFailureIdempotencyAndDirectDiscountLedger(){
        wallet.recharge(customer,new PlatformWalletService.Recharge("recharge_failed",10000L,"FAILED"));assertEquals(0,wallet.balance(customer));
        wallet.recharge(customer,new PlatformWalletService.Recharge("recharge_success",20000L,"SUCCESS"));
        wallet.recharge(customer,new PlatformWalletService.Recharge("recharge_success",20000L,"SUCCESS"));assertEquals(20000,wallet.balance(customer));
        activity("九折","9");Long best=activity("八折","8");
        var bill=wallet.createBill(staff,new PlatformWalletService.BillInput("direct_bill_01",store,10000L,"测试"));
        var pay=new PlatformWalletService.Pay("direct_pay_01",bill.getBillNo(),null,null,8000L);
        assertEquals(best,wallet.quote(customer,pay).activityId());
        var tx=wallet.pay(customer,pay);assertNotNull(tx.createTime());assertEquals(8000,tx.amount());assertEquals(2000,tx.discountAmount());assertEquals(12000,wallet.balance(customer));
        assertEquals(tx.id(),wallet.pay(customer,pay).id());
        assertThrows(CoolException.class,()->wallet.pay(otherCustomer,new PlatformWalletService.Pay("other_pay_01",bill.getBillNo(),null,null,8000L)));
        assertEquals("PAID",billRows.selectOneById(bill.getId()).getStatus());
        assertThrows(CoolException.class,()->wallet.cancelBill(staff,bill.getId()));
    }
    @Test void insufficientBalanceLeavesBillUnpaidAndNoDebit(){
        var bill=wallet.createBill(staff,new PlatformWalletService.BillInput("direct_bill_02",store,999L,null));
        assertThrows(CoolException.class,()->wallet.pay(customer,new PlatformWalletService.Pay("direct_pay_02",bill.getBillNo(),null,null,999L)));
        assertEquals("PENDING",billRows.selectOneById(bill.getId()).getStatus());assertEquals(0,wallet.balance(customer));
        assertEquals(0,transactions.selectCountByQuery(QueryWrapper.create().eq(WalletTransactionEntity::getCustomerId,customer)));
        assertThrows(CoolException.class,()->wallet.createBill(otherStaff,new PlatformWalletService.BillInput("direct_bill_03",store,999L,null)));
    }
    @Test void activitiesRequireReviewOperatorRateAndOwnStoreScope(){
        Long id=activity.create(owner,false,new WalletActivityService.Input("商家申请","测试",new Date(System.currentTimeMillis()-1000),new Date(System.currentTimeMillis()+600000),List.of(store),null));
        assertTrue(activity.publicList(store).isEmpty());
        activity.review(admin,new WalletActivityService.Review(id,true,new BigDecimal("8.5"),"通过"));assertEquals(1,activity.publicList(store).size());
        assertEquals(850,activity.quote(store,1000).amount());
        activity.offline(owner,false,id);assertTrue(activity.publicList(store).isEmpty());
        assertThrows(CoolException.class,()->activity.create(owner,false,new WalletActivityService.Input("自设折扣","",new Date(),new Date(System.currentTimeMillis()+60000),List.of(store),BigDecimal.ONE)));
    }
    @Test void activityBoundsAndDifferentStoreDoNotApply(){
        activity("仅本店","9");assertEquals(0,activity.quote(otherStore,10000).discountAmount());
        assertThrows(CoolException.class,()->activity("非法","10"));
        Long id=activity("过期","8");var row=activityRows.selectOneById(id);row.setEndAt(new Date(System.currentTimeMillis()-2000));activityRows.update(row);
        assertEquals(9000,activity.quote(store,10000).amount());
        var s=stores.selectOneById(store);s.setStatus(2);stores.update(s);assertThrows(CoolException.class,()->activity.quote(store,100));
    }
    @Test void walletPurchaseReusesExistingIssueAndChargesOnlyDiscountedAmount(){
        var c=new CouponTemplateEntity();c.setMerchantId(merchant);c.setName("测试储值购券");c.setType("CASH");c.setDeductAmount(BigDecimal.TEN);c.setSalePrice(new BigDecimal("10.00"));c.setScopeType(0);c.setValidityType("AFTER_ISSUE");c.setValidDays(30);c.setStatus(1);c.setRuleVersion(1);c.setIssuedCount(0L);coupons.insertSelective(c);
        var pub=new CouponPublicationEntity();pub.setMerchantId(merchant);pub.setCouponTemplateId(c.getId());pub.setChannel("PLATFORM");pub.setAuditStatus(2);pub.setPublishStatus(1);pub.setPublishedVersion(1);pub.setPublishedAt(new Date());publications.insertSelective(pub);
        wallet.recharge(customer,new PlatformWalletService.Recharge("purchase_recharge",2000L,null));activity("九折","9");
        var order=trade.create(customer,c.getId(),1,"wallet_order_01");Long id=((Number)order.get("id")).longValue();
        var pay=new PlatformWalletService.Pay("wallet_pay_01",null,id,store,900L);var tx=wallet.pay(customer,pay);
        assertEquals(900,tx.amount());assertEquals(1100,wallet.balance(customer));assertEquals("PAID",trade.orderInfo(customer,id).get("orderStatus"));
        assertEquals(tx.id(),wallet.pay(customer,pay).id());
        assertEquals(1,((List<?>)trade.orderInfo(customer,id).get("couponIds")).size());
        assertThrows(CoolException.class,()->wallet.pay(customer,new PlatformWalletService.Pay("wallet_pay_02",null,id,store,900L)));
    }
    @Test void walletHttpRechargeAndActivityPermissionBoundaries() throws Exception {
        String app=token(customer,UserTypeEnum.APP);
        mvc.perform(post("/app/wallet/recharge").header("Authorization",app).contentType("application/json").content("{\"requestId\":\"recharge_http\",\"amount\":1234}")).andExpect(jsonPath("$.data.amount").value(1234));
        mvc.perform(get("/app/wallet/balance").header("Authorization",app)).andExpect(jsonPath("$.data").value(1234));
        String denied=token(admin,UserTypeEnum.ADMIN);
        mvc.perform(get("/admin/wallet/transactions").header("Authorization",denied)).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/admin/wallet/activities/page").header("Authorization",denied)).andExpect(jsonPath("$.code").value(403));
    }
    @Test void quoteChangeDoesNotChargeAndMerchantCannotSeeCustomerBalance(){
        wallet.recharge(customer,new PlatformWalletService.Recharge("quote_recharge_01",10000L,null));
        Long id=activity("九折","9");var bill=wallet.createBill(staff,new PlatformWalletService.BillInput("quote_bill_01",store,1000L,null));
        var pay=new PlatformWalletService.Pay("quote_pay_01",bill.getBillNo(),null,null,900L);
        activity.offline(admin,true,id);assertThrows(CoolException.class,()->wallet.pay(customer,pay));assertEquals(10000,wallet.balance(customer));
        wallet.pay(customer,new PlatformWalletService.Pay("quote_pay_01",bill.getBillNo(),null,null,1000L));
        var result=wallet.transactions(staff,"WORKSPACE",1,20);assertEquals(1,result.total());assertNull(result.list().get(0).balanceAfter());
        assertEquals(0,wallet.transactions(otherStaff,"WORKSPACE",1,20).total());
    }
    @Test void fractionalMoneyAndForeignImageAreRejectedOverHttp() throws Exception {
        String app=token(customer,UserTypeEnum.APP);
        mvc.perform(post("/app/wallet/recharge").header("Authorization",app).contentType("application/json").content("{\"requestId\":\"fractional_01\",\"amount\":1.9}"))
            .andExpect(result->assertFalse(result.getResponse().getContentAsString().contains("\"code\":1000")));
        assertEquals(0,wallet.balance(customer));
        mvc.perform(post("/app/support/feedback/submit").header("Authorization",app).contentType("application/json").content("{\"requestId\":\"foreign_image\",\"content\":\"内容\",\"images\":[\"http://example.invalid/image.png\"]}"))
            .andExpect(jsonPath("$.code").value(403));
    }
    @Test void realImageUploadChecksContentAndRecordsOwnership() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var file=new org.springframework.mock.web.MockMultipartFile("file","test.png","image/png",bytes.toByteArray());
        var request=new org.springframework.mock.web.MockHttpServletRequest();String url=null;
        try {
            url=imageService.upload("APP",customer,file,request);
            assertEquals(List.of(url),new SupportImageService(imageRows,null).validate("APP",customer,List.of(url)));
            String foreign=url;assertThrows(CoolException.class,()->new SupportImageService(imageRows,null).validate("APP",otherCustomer,List.of(foreign)));
            var fake=new org.springframework.mock.web.MockMultipartFile("file","fake.jpg","image/jpeg",bytes.toByteArray());
            assertThrows(CoolException.class,()->imageService.upload("APP",customer,fake,request));
        } finally {
            if(url!=null){var uri=java.net.URI.create(url);String[] parts=uri.getPath().split("/");var root=java.nio.file.Path.of(localFiles.getAbsoluteUploadFolder()).toAbsolutePath().normalize();var target=root.resolve(parts[parts.length-2]).resolve(parts[parts.length-1]).normalize();assertTrue(target.startsWith(root));java.nio.file.Files.deleteIfExists(target);}
        }
    }
    @Test void concurrentRetriesAndCompetingPaymentsDoNotDoubleDebit() throws Exception {
        wallet.recharge(customer,new PlatformWalletService.Recharge("concurrent_recharge",2500L,null));
        var first=wallet.createBill(staff,new PlatformWalletService.BillInput("concurrent_bill_1",store,1000L,null));
        var pay=new PlatformWalletService.Pay("concurrent_pay_1",first.getBillNo(),null,null,1000L);
        // 仅本方法的专用夹具提交以便两个独立数据库连接可见，finally精确清理这些ID。
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();org.springframework.test.context.transaction.TestTransaction.end();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var one=pool.submit(()->{gate.await();return wallet.pay(customer,pay);});var two=pool.submit(()->{gate.await();return wallet.pay(customer,pay);});gate.countDown();
            assertEquals(one.get(20,java.util.concurrent.TimeUnit.SECONDS).id(),two.get(20,java.util.concurrent.TimeUnit.SECONDS).id());assertEquals(1500,wallet.balance(customer));
            var a=wallet.createBill(staff,new PlatformWalletService.BillInput("concurrent_bill_2",store,1000L,null));
            var b=wallet.createBill(staff,new PlatformWalletService.BillInput("concurrent_bill_3",store,1000L,null));
            var next=new java.util.concurrent.CountDownLatch(1);
            var results=List.of(pool.submit(()->{next.await();try{wallet.pay(customer,new PlatformWalletService.Pay("concurrent_pay_2",a.getBillNo(),null,null,1000L));return true;}catch(CoolException insufficient){return false;}}),pool.submit(()->{next.await();try{wallet.pay(customer,new PlatformWalletService.Pay("concurrent_pay_3",b.getBillNo(),null,null,1000L));return true;}catch(CoolException insufficient){return false;}}));next.countDown();
            int success=0;for(var result:results)if(result.get(20,java.util.concurrent.TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(500,wallet.balance(customer));
            assertEquals(2,transactions.selectCountByQuery(QueryWrapper.create().eq(WalletTransactionEntity::getCustomerId,customer).eq(WalletTransactionEntity::getType,"DIRECT")));
            var mixed=wallet.createBill(staff,new PlatformWalletService.BillInput("race_modes_bill",store,500L,null));
            var start=new java.util.concurrent.CountDownLatch(1);
            var walletResult=pool.submit(()->{start.await();try{wallet.consumptionPay(customer,settlementPay("race_wallet",mixed.getBillNo(),"WALLET",500));return true;}catch(CoolException handled){return false;}});
            var simulatedResult=pool.submit(()->{start.await();try{wallet.consumptionPay(customer,settlementPay("race_simulate",mixed.getBillNo(),"SIMULATED",500));return true;}catch(CoolException handled){return false;}});
            start.countDown();
            assertNotEquals(walletResult.get(20,java.util.concurrent.TimeUnit.SECONDS),simulatedResult.get(20,java.util.concurrent.TimeUnit.SECONDS));
            var completed=billRows.selectOneById(mixed.getId());
            assertEquals("PAID",completed.getStatus());
            assertEquals("WALLET".equals(completed.getPaymentMode())?0L:500L,wallet.balance(customer));
        } finally {
            pool.shutdownNow();assertTrue(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
                var m=merchants.selectOneById(merchant);var category=m.getCategoryId();var type=stores.selectOneById(store).getCategoryId();var ids=List.of(owner,staff,otherStaff,admin);
                transactions.deleteByQuery(QueryWrapper.create().in(WalletTransactionEntity::getCustomerId,List.of(customer,otherCustomer)));
                walletRows.deleteByQuery(QueryWrapper.create().in(PlatformWalletEntity::getCustomerId,List.of(customer,otherCustomer)));
                billRows.deleteByQuery(QueryWrapper.create().in(WalletBillEntity::getOperatorId,ids));
                grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").in(WorkspaceGrantEntity::getSubjectId,ids));
                grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"MERCHANT").eq(WorkspaceGrantEntity::getSubjectId,merchant));
                roleLinks.deleteByQuery(QueryWrapper.create().in(BaseSysUserRoleEntity::getUserId,ids));bindings.deleteByQuery(QueryWrapper.create().in(MerchantUserStoreEntity::getUserId,ids));
                users.deleteByQuery(QueryWrapper.create().in(BaseSysUserEntity::getId,ids));stores.deleteByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant));merchants.deleteById(merchant);
                categories.deleteById(category);storeTypes.deleteById(type);customers.deleteByQuery(QueryWrapper.create().in(UserInfoEntity::getId,List.of(customer,otherCustomer)));
            });
            assertNull(merchants.selectOneById(merchant));assertEquals(0,transactions.selectCountByQuery(QueryWrapper.create().eq(WalletTransactionEntity::getCustomerId,customer)));
        }
    }
    @Test void activityApplicationReviewAndOfflineThroughRealTokenChain() throws Exception {
        String ownerToken=token(owner,UserTypeEnum.MERCHANT);
        var format=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String body="{\"title\":\"接口活动申请\",\"startAt\":\""+format.format(new Date(System.currentTimeMillis()-60000))+"\",\"endAt\":\""+format.format(new Date(System.currentTimeMillis()+600000))+"\",\"storeIds\":["+store+"]}";
        String response=mvc.perform(post("/merchant/governance/wallet/activities/create").header("Authorization",ownerToken).contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(1000)).andReturn().getResponse().getContentAsString();
        Long id=cn.hutool.json.JSONUtil.parseObj(response).getLong("data");String platform=token(admin,UserTypeEnum.ADMIN,"activity:manage");
        mvc.perform(post("/admin/wallet/activities/review").header("Authorization",platform).contentType("application/json").content("{\"id\":"+id+",\"approve\":true,\"discountRate\":8.8}")).andExpect(jsonPath("$.code").value(1000));
        String app=token(customer,UserTypeEnum.APP);
        mvc.perform(get("/app/wallet/activities?storeId="+store).header("Authorization",app)).andExpect(jsonPath("$.data[0].discountRate").value(8.8));
        mvc.perform(post("/merchant/governance/wallet/activities/offline").header("Authorization",ownerToken).contentType("application/json").content("{\"id\":"+id+"}")).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(get("/app/wallet/activities?storeId="+store).header("Authorization",app)).andExpect(jsonPath("$.data").isEmpty());
    }
    @Test void staffBillAndCustomerPaymentAreAtomicThroughHttp() throws Exception {
        activity("九折接口活动","9");wallet.recharge(customer,new PlatformWalletService.Recharge("http_pay_recharge",1000L,null));
        String staffToken=token(staff,UserTypeEnum.MERCHANT);
        String response=mvc.perform(post("/merchant/governance/wallet/bills/create").header("Authorization",staffToken).contentType("application/json").content("{\"requestId\":\"http_bill_create\",\"storeId\":"+store+",\"amount\":300}")).andExpect(jsonPath("$.code").value(1000)).andReturn().getResponse().getContentAsString();
        String no=cn.hutool.json.JSONUtil.parseObj(response).getJSONObject("data").getStr("billNo");String app=token(customer,UserTypeEnum.APP);
        mvc.perform(post("/app/wallet/quote").header("Authorization",app).contentType("application/json").content("{\"billNo\":\""+no+"\"}")).andExpect(jsonPath("$.data.amount").value(270));
        String body="{\"requestId\":\"http_wallet_pay\",\"billNo\":\""+no+"\",\"expectedAmount\":270}";
        mvc.perform(post("/app/wallet/pay").header("Authorization",app).contentType("application/json").content(body)).andExpect(jsonPath("$.data.amount").value(270));
        mvc.perform(post("/app/wallet/pay").header("Authorization",app).contentType("application/json").content(body)).andExpect(jsonPath("$.data.balanceAfter").value(730));
        assertEquals(730,wallet.balance(customer));
        mvc.perform(get("/merchant/governance/wallet/transactions").header("Authorization",staffToken)).andExpect(jsonPath("$.data.list[0].balanceAfter").doesNotExist());
    }
    @Test void disabledStoreCannotAcceptNewMessagesButCustomerKeepsHistory(){
        Long id=chats.open(customer,store);chats.send("APP",customer,new StoreChatService.Send(id,"before_closed_01","历史消息",List.of()));
        var s=stores.selectOneById(store);s.setStatus(0);stores.update(s);
        assertThrows(CoolException.class,()->chats.send("APP",customer,new StoreChatService.Send(id,"after_closed_01","不能继续发送",List.of())));
        assertEquals(1,chats.messages("APP",customer,id,null,null,50).size());
    }
    @Test void merchantActivityCannotFollowStoreIntoAnotherMerchant(){
        Long id=activity.create(owner,false,new WalletActivityService.Input("商家活动","",new Date(System.currentTimeMillis()-2000),new Date(System.currentTimeMillis()+600000),List.of(store),null));
        activity.review(admin,new WalletActivityService.Review(id,true,new BigDecimal("8"),null));
        var m=new MerchantInfoEntity();m.setName(prefix+"另一个商家");m.setCategoryId(merchants.selectOneById(merchant).getCategoryId());m.setContactName("测试");m.setContactPhone("13800000001");m.setStatus(1);merchants.insertSelective(m);
        var s=stores.selectOneById(store);s.setMerchantId(m.getId());stores.update(s);
        assertTrue(activity.publicList(store).isEmpty());assertEquals(1000,activity.quote(store,1000).amount());
    }
    @Test void restrictedMerchantCannotListOrOfflineActivityOutsideCurrentStoreScope(){
        Long id=activity.create(owner,false,new WalletActivityService.Input("授权范围外活动","",new Date(System.currentTimeMillis()-2000),new Date(System.currentTimeMillis()+600000),List.of(otherStore),null));
        var u=users.selectOneById(owner);u.setMerchantRole("MEMBER");u.setStoreScope("SELECTED");users.update(u);
        var scope=new com.cool.modules.workspace.entity.WorkspaceUserScopeEntity();scope.setUserId(owner);scope.setStoreId(store);userScopes.insert(scope);
        assertEquals(0,activity.page(owner,false,1,20).total());
        assertThrows(CoolException.class,()->activity.offline(owner,false,id));
        activity.create(owner,false,new WalletActivityService.Input("范围内活动","",new Date(),new Date(System.currentTimeMillis()+600000),List.of(store),null));
        assertEquals(1,activity.page(owner,false,1,20).total());
    }
}
