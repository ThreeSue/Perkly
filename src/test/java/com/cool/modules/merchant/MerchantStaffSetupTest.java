package com.cool.modules.merchant;
import static org.junit.jupiter.api.Assertions.*;
import cn.hutool.json.JSONUtil;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.MerchantUserStoreEntity;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.service.*;
import com.cool.modules.coupon.service.CouponTradeService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.util.UserPasswordUtil;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 显式准备单门店核销实验账号及可用持券并导出本机测试凭据
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 10:32:24
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"sms-auth.access-key-id=staff-setup-unused","sms-auth.access-key-secret=staff-setup-unused"})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named="merchant.staff.setup",matches="true")
class MerchantStaffSetupTest {
 @Autowired MerchantAccountService accounts;
 @Autowired BaseSysUserMapper users;
 @Autowired MerchantStoreMapper stores;
 @Autowired MerchantUserStoreMapper links;
 @Autowired CouponTradeService trade;
 @Autowired UserInfoService customers;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Test void prepare() {
    new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
       try { prepareData(); } catch (Exception e) { throw new RuntimeException(e); }
    });
 }
 void prepareData() throws Exception {
    var store=stores.selectOneById(1L);assertEquals(1L,store.getMerchantId());assertEquals(1,store.getStatus());
    Path output=Path.of("../testing/private/merchant-store-pilot.json");
    assertFalse(Files.exists(output),"测试凭据已存在，不覆盖账号或重复发券");
    List<Map<String,Object>> credentials=new ArrayList<>();
    for(String role:List.of("MANAGER","STAFF")){
      String username="pilot_store1_"+role.toLowerCase();
      assertNull(users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,username)),"账号已存在，停止避免覆盖");
      String password="Pilot_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
      Long id=accounts.provision(username,"MANAGER".equals(role)?"三坊七巷实验店长":"三坊七巷实验员工",password,1L);
      BaseSysUserEntity change=new BaseSysUserEntity();change.setId(id);change.setMerchantRole(role);users.update(change);
      MerchantUserStoreEntity link=new MerchantUserStoreEntity();link.setMerchantId(1L);link.setUserId(id);link.setStoreId(1L);links.insert(link);
      credentials.add(Map.of("username",username,"password",password,"role",role));
    }
    var customer=new UserInfoEntity();customer.setPhone("198"+String.format("%08d",new java.security.SecureRandom().nextInt(100_000_000)));
    customer.setNickName("单门店核销实验顾客");customer.setStatus(1);customer.setPassword(UserPasswordUtil.encode(UUID.randomUUID().toString().replace("-", "").substring(0,24)));customers.save(customer);
    List<Object> coupons=new ArrayList<>();
    for(int i=0;i<3;i++) {
      if(i>0) {
        customer=new UserInfoEntity();customer.setPhone("198"+String.format("%08d",new java.security.SecureRandom().nextInt(100_000_000)));
        customer.setNickName("单门店核销实验顾客"+(i+1));customer.setStatus(1);
        customer.setPassword(UserPasswordUtil.encode(UUID.randomUUID().toString().replace("-", "").substring(0,24)));customers.save(customer);
      }
      var order=trade.claim(customer.getId(),37L,1,"pilot_store1_"+UUID.randomUUID().toString());
      List<?> couponIds=(List<?>)order.get("couponIds");assertEquals(1,couponIds.size());
      coupons.add(trade.walletInfo(customer.getId(),((Number)couponIds.get(0)).longValue()));
    }
    Files.createDirectories(output.getParent());Files.writeString(output.getParent().resolve(".gitignore"),"*\n!.gitignore\n");
    Files.writeString(output,JSONUtil.toJsonPrettyStr(Map.of("merchantId",1,"storeId",1,"storeName",store.getName(),"accounts",credentials,"customerId",customer.getId(),"coupons",coupons)));
    System.out.println("PILOT_READY: store=1 template=37 accounts=2 coupons=3; credentials saved privately.");
 }
}
