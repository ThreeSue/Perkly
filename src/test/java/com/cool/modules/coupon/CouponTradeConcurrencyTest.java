package com.cool.modules.coupon;

import static org.junit.jupiter.api.Assertions.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.*;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserInfoService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 用独立数据库事务竞争验证幂等下单支付库存与单券核销
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:26:35
 */
@SpringBootTest(properties={"sms-auth.access-key-id=trade-concurrency-unused","sms-auth.access-key-secret=trade-concurrency-unused"})
@ActiveProfiles("local")
class CouponTradeConcurrencyTest {
    @Autowired CouponTradeService trade;
    @Autowired CouponTemplateService templates;
    @Autowired CouponPublicationService publications;
    @Autowired MerchantAccountService accounts;
    @Autowired UserInfoService users;
    @Autowired SqlSessionTemplate session;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired com.cool.modules.approval.mapper.ApprovalMapper auditRecords;
    @Autowired com.cool.modules.merchant.mapper.MerchantStoreCategoryMapper storeTypes;
    record Fixture(Long merchant,Long customerA,Long customerB,Long template,Long store,Long operator) {}
    JdbcTemplate jdbc() { return new JdbcTemplate(new SingleConnectionDataSource(session.getConnection(),true)); }
    Long insert(String sql,Object...args) {
        var keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc().update(connection->{var s=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);
        return keys.getKey().longValue();
    }
    Long user() {
        String phone;
        do {phone="198"+String.format("%08d",ThreadLocalRandom.current().nextInt(100_000_000));}
        while(jdbc().queryForObject("SELECT COUNT(*) FROM user_info WHERE phone=?",Integer.class,phone)>0);
        var u=new UserInfoEntity();u.setPhone(phone);u.setStatus(1);u.setNickName("购券并发自动测试");users.save(u);return u.getId();
    }
    Fixture fixture() {
        Long category=jdbc().queryForObject("SELECT id FROM merchant_category LIMIT 1",Long.class);
        Long merchant=insert("INSERT INTO merchant_info(name,category_id,contact_name,contact_phone,status) VALUES('购券并发测试临时商家',?,'自动测试','13800000001',1)",category);
        var storeType = storeTypes.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                .eq(com.cool.modules.merchant.entity.MerchantStoreCategoryEntity::getStatus, 1).limit(1));
        assertNotNull(storeType);
        Long storeCategory = storeType.getId();
        Long store=insert("INSERT INTO store(merchant_id,category_id,name,address,phone,status) VALUES(?,?,'并发临时门店','测试地址','13800000001',1)",merchant,storeCategory);
        Long operator=accounts.provision("race_"+UUID.randomUUID().toString().replace("-","").substring(0,12),"并发测试管理员","MerchantTest123!",merchant);
        Long a=user(),b=user();
        var c=new CouponTemplateEntity();c.setMerchantId(merchant);c.setName("并发测试临时券");c.setType("CASH");c.setDeductAmount(BigDecimal.TEN);
        c.setSalePrice(BigDecimal.ONE);c.setScopeType(1);c.setStoreIds(List.of(store));c.setStatus(1);c.setValidityType("AFTER_ISSUE");c.setValidDays(30);c.setIssueLimit(2L);c.setPerCustomerLimit(5);
        Long template=templates.add(c);publications.submitAudit(template,1);publications.audit(template,1,true);publications.publish(template,1);
        return new Fixture(merchant,a,b,template,store,operator);
    }
    List<Object> race(List<Supplier<Object>> actions) throws Exception {
        var ready=new CountDownLatch(actions.size());var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(actions.size());
        try {
            List<Future<Object>> futures=new ArrayList<>();
            for(var action:actions)futures.add(pool.submit(()->{ready.countDown();start.await();try{return action.get();}catch(CoolException expected){return expected;}}));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            List<Object> results=new ArrayList<>();for(var future:futures)results.add(future.get(30,TimeUnit.SECONDS));return results;
        } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}
    }
    List<Supplier<Object>> repeated(Supplier<Object> action) { return java.util.stream.IntStream.range(0,6).mapToObj(i->action).toList(); }
    @Test void concurrentRetriesCannotDuplicateOrdersOversellOrRedeemTwice() throws Exception {
        var tx=new TransactionTemplate(transactionManager);Fixture f=tx.execute(status->fixture());
        try {
            String key=UUID.randomUUID().toString();
            var creates=race(repeated(()->trade.create(f.customerA(),f.template(),1,key)));
            assertTrue(creates.stream().allMatch(Map.class::isInstance));
            Long order=((Number)((Map<?,?>)creates.get(0)).get("id")).longValue();
            assertTrue(creates.stream().map(r->((Map<?,?>)r).get("id")).allMatch(order::equals));
            var payments=race(repeated(()->trade.simulatePay(f.customerA(),order,"SUCCESS")));
            assertTrue(payments.stream().allMatch(Map.class::isInstance));
            assertEquals(1L,trade.wallet(f.customerA(),1,20,null).getPagination().getTotal());
            var p1=trade.create(f.customerA(),f.template(),1,UUID.randomUUID().toString());
            var p2=trade.create(f.customerB(),f.template(),1,UUID.randomUUID().toString());
            var stock=race(List.of(()->trade.simulatePay(f.customerA(),((Number)p1.get("id")).longValue(),"SUCCESS"),
                    ()->trade.simulatePay(f.customerB(),((Number)p2.get("id")).longValue(),"SUCCESS")));
            assertEquals(1,stock.stream().filter(Map.class::isInstance).count());
            assertEquals(1,stock.stream().filter(CoolException.class::isInstance).count());
            tx.execute(status->{assertEquals(2L,templates.getById(f.template()).getIssuedCount());return null;});
            Long coupon=((Number)trade.wallet(f.customerA(),1,20,null).getList().get(0).get("id")).longValue();
            String code=(String)trade.walletInfo(f.customerA(),coupon).get("code");
            var redeemed=race(repeated(()->trade.redeem(f.operator(),f.store(),code,2000L)));
            assertEquals(1,redeemed.stream().filter(Map.class::isInstance).count());
            assertEquals(5,redeemed.stream().filter(CoolException.class::isInstance).count());
            assertEquals(1L,trade.redemptions(f.operator(),1,20,null).getPagination().getTotal());
        } finally {
            tx.execute(status->{
                // 仅清理本测试新建商家和顾客的临时数据，绝不触碰既有业务记录。
                auditRecords.deleteByQuery(com.mybatisflex.core.query.QueryWrapper.create()
                        .eq(com.cool.modules.approval.entity.AuditRecordEntity::getMerchantId, f.merchant()));
                for(String table:List.of("coupon_redemption","customer_coupon","purchase_order_item","purchase_order","coupon_publication","coupon_store","coupon_product","coupon_template","store","base_sys_user_store","base_sys_user"))
                    jdbc().update("DELETE FROM "+table+" WHERE merchant_id=?",f.merchant());
                jdbc().update("DELETE FROM merchant_info WHERE id=?",f.merchant());
                jdbc().update("DELETE FROM user_info WHERE id IN (?,?)",f.customerA(),f.customerB());return null;
            });
        }
    }
}

