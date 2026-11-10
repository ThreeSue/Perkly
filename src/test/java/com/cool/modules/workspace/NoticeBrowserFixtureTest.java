package com.cool.modules.workspace;

import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.base.entity.sys.BaseSysUserRoleEntity;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.workspace.entity.*;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.transaction.TestTransaction;
import java.nio.file.*;
import java.util.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供通知浏览器验收隔离账号与真实HTTP服务并清理测试数据
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 23:15:00
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"sms-auth.access-key-id=features-test-unused","sms-auth.access-key-secret=features-test-unused","server.address=127.0.0.1"})
@EnabledIfEnvironmentVariable(named="PERKLY_NOTICE_BROWSER_TEST",matches="true")
class NoticeBrowserFixtureTest extends FeatureModulesIntegrationTest {
 @LocalServerPort int port;
 @Autowired javax.sql.DataSource source;
 @Autowired com.cool.modules.base.service.sys.BaseSysPermsService perms;
 @Autowired com.cool.modules.workspace.mapper.WorkspaceNoticeMapper noticeRows;
 @Autowired com.cool.modules.workspace.mapper.WorkspaceNoticeRecipientMapper recipientRows;
 @Test void serveNotices() throws Exception {
  try(var c=source.getConnection()){if(!c.getMetaData().getURL().contains("perkly_ai_regression_20261106"))throw new IllegalStateException("Dedicated regression DB required");}
  Long category=merchants.selectOneById(merchant).getCategoryId(), storeType=stores.selectOneById(store).getCategoryId();
  var role=new BaseSysUserRoleEntity();role.setUserId(admin);role.setRoleId(1L);roleLinks.insert(role);
  var adminToken=token(admin,UserTypeEnum.ADMIN,perms.getPerms(admin));var ownerToken=token(owner,UserTypeEnum.MERCHANT);
  keys.forEach(k->cache.set(k,cache.get(k),1800));
  for(int i=0;i<3;i++) {
   var n=new WorkspaceNoticeEntity();n.setSenderId(admin);n.setRequestId(prefix+"notice"+i);n.setTitle(i==0?"轻通知验收":"强提醒验收"+i);n.setContent("请核对明日营业安排。确认后不再提醒；当前编辑内容保留。");n.setNoticeType(i==0?"LIGHT":"STRONG");noticeRows.insert(n);
   var r=new WorkspaceNoticeRecipientEntity();r.setNoticeId(n.getId());r.setRecipientId(owner);recipientRows.insert(r);
  }
  TestTransaction.flagForCommit();TestTransaction.end();
  var file=Path.of("logs/test/notice-browser-session.json");var stop=Path.of("logs/test/notice-browser-stop");Files.deleteIfExists(stop);
  try {
   Files.writeString(file,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("port",port,"adminId",admin,"ownerId",owner,"adminToken",adminToken,"ownerToken",ownerToken,"merchantId",merchant,"prefix",prefix)));
   System.out.println("NOTICE_BROWSER_READY");long until=System.currentTimeMillis()+1200000;
   while(!Files.exists(stop)&&System.currentTimeMillis()<until)Thread.sleep(500);
  } finally {
   Files.deleteIfExists(file);Files.deleteIfExists(stop);
   new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
    var ids=List.of(owner,staff,otherStaff,admin);
    var ns=noticeRows.selectListByQuery(QueryWrapper.create().in(WorkspaceNoticeEntity::getSenderId,ids)).stream().map(WorkspaceNoticeEntity::getId).toList();
    if(!ns.isEmpty())recipientRows.deleteByQuery(QueryWrapper.create().in(WorkspaceNoticeRecipientEntity::getNoticeId,ns));
    noticeRows.deleteByQuery(QueryWrapper.create().in(WorkspaceNoticeEntity::getSenderId,ids));
    grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").in(WorkspaceGrantEntity::getSubjectId,ids));
    grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"MERCHANT").eq(WorkspaceGrantEntity::getSubjectId,merchant));
    roleLinks.deleteByQuery(QueryWrapper.create().in(BaseSysUserRoleEntity::getUserId,ids));bindings.deleteByQuery(QueryWrapper.create().in(MerchantUserStoreEntity::getUserId,ids));
    users.deleteByQuery(QueryWrapper.create().in(BaseSysUserEntity::getId,ids));stores.deleteByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant));
    merchants.deleteById(merchant);categories.deleteById(category);storeTypes.deleteById(storeType);customers.deleteById(customer);customers.deleteById(otherCustomer);
   });
   System.out.println("NOTICE_BROWSER_CLEANED");
  }
 }
}
