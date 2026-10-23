package com.cool.modules.merchant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.*;
import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.service.*;
import com.cool.modules.merchant.service.MerchantStoreAccountService.*;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.Filter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在事务回滚数据库验证门店账号管理归属与旧登录立即失效
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 12:02:00
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"sms-auth.access-key-id=store-account-test-unused","sms-auth.access-key-secret=store-account-test-unused"})
@ActiveProfiles("local") @Transactional
class MerchantStoreAccountManagementTest {
 @Autowired BaseSysUserMapper users;
 @Autowired MerchantStoreMapper stores;
 @Autowired MerchantInfoMapper merchants;
 @Autowired MerchantCategoryMapper categories;
 @Autowired MerchantUserStoreMapper links;
 @Autowired MerchantStoreAccountService management;
 @Autowired MerchantAccountService accounts;
 @Autowired BackendAccountService backendAccounts;
 @Autowired com.cool.modules.base.service.sys.BaseSysUserService platformAccounts;
 @Autowired com.cool.modules.base.mapper.sys.BaseSysDepartmentMapper departments;
 @Autowired CoolCache cache;
 @Autowired JwtTokenUtil tokens;
 @Autowired WebApplicationContext web;
 @Autowired @Qualifier("springSecurityFilterChain") Filter security;
 Long merchantA,merchantB,storeA,storeA2,storeB,ownerA,ownerB;
 MockMvc mvc;
 @BeforeEach void setup(){
    var category=categories.selectOneByQuery(QueryWrapper.create().eq(MerchantCategoryEntity::getStatus,1).limit(1));
    var example=stores.selectOneByQuery(QueryWrapper.create().limit(1));
    merchantA=merchant(category.getId()); merchantB=merchant(category.getId());
    storeA=store(merchantA,example.getCategoryId());storeA2=store(merchantA,example.getCategoryId());storeB=store(merchantB,example.getCategoryId());
    ownerA=accounts.provision(unique(),"商家甲管理员","123456",merchantA);ownerB=accounts.provision(unique(),"商家乙管理员","123456",merchantB);
    mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 String unique(){return "sa_"+UUID.randomUUID().toString().replace("-","").substring(0,18);}
 Long merchant(Long category){var m=new MerchantInfoEntity();m.setName(unique());m.setCategoryId(category);m.setContactName("测试联系人");m.setContactPhone("13800138000");m.setStatus(1);merchants.insertSelective(m);return m.getId();}
 Long store(Long merchant,Long category){var s=new MerchantStoreEntity();s.setMerchantId(merchant);s.setCategoryId(category);s.setName(unique());s.setAddress("测试地址");s.setPhone("13800138000");s.setStatus(1);stores.insertSelective(s);return s.getId();}
 Long create(String role){return management.save(ownerA,new SaveInput(null,storeA,storeA,unique(),"门店测试人员",role,1,"Test123456"));}
 Dict login(Long id,String password){var u=users.selectOneById(id);var input=new BaseSysLoginDto();input.setUsername(u.getUsername());input.setPassword(password);input.setCaptchaId(UUID.randomUUID().toString());input.setVerifyCode("1234");cache.set("verify:img:"+input.getCaptchaId(),"1234",60);return (Dict)accounts.login(input);}
 String adminToken(BaseSysUserEntity user){
    var principal=new JwtUser(user.getId(),user.getUsername(),"",List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),true);principal.setUserTypeEnum(UserTypeEnum.ADMIN);
    cache.set("admin:userDetails:"+user.getUsername(),principal,tokens.getExpire());cache.set("admin:passwordVersion:"+user.getId(),user.getPasswordV(),tokens.getExpire());
    return tokens.generateToken(Dict.create().set("userType","ADMIN").set("userId",user.getId()).set("username",user.getUsername()).set("passwordVersion",user.getPasswordV()));
 }
 @Test void createsBothRolesWithOneStoreAndSafePagedResponse() throws Exception {
    Long manager=create("MANAGER"),staff=create("STAFF");
    for(Long id:List.of(manager,staff)){
        assertEquals(1,links.selectCountByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId,id)));
        var session=login(id,"Test123456");
        mvc.perform(get("/merchant/staff/me").header("Authorization",session.getStr("token"))).andExpect(jsonPath("$.data.storeId").value(storeA.intValue()));
    }
    var owner=login(ownerA,"123456");
    mvc.perform(post("/merchant/workspace/storeAccount/page").header("Authorization",owner.getStr("token")).contentType("application/json").content("{\"storeId\":"+storeA+",\"page\":1,\"size\":20,\"keyWord\":\"门店测试\"}"))
        .andExpect(jsonPath("$.data.pagination.total").value(2)).andExpect(jsonPath("$.data.list[0].password").doesNotExist()).andExpect(jsonPath("$.data.list[0].passwordV").doesNotExist());
    assertTrue(management.page(ownerA,new PageInput(storeA2,1,20,null)).list().isEmpty());
 }
 @Test void disableThenEnableNeverRevivesAccessOrRefresh() throws Exception {
    Long id=create("STAFF");var session=login(id,"Test123456");var owner=login(ownerA,"123456");
    mvc.perform(post("/merchant/workspace/storeAccount/status").header("Authorization",owner.getStr("token")).contentType("application/json").content("{\"storeId\":"+storeA+",\"id\":"+id+",\"status\":0}"))
        .andExpect(jsonPath("$.code").value(1000));
    assertThrows(CoolException.class,()->accounts.validate(session.getStr("token"),false));assertThrows(CoolException.class,()->accounts.refresh(session.getStr("refreshToken")));
    mvc.perform(get("/merchant/staff/me").header("Authorization",session.getStr("token"))).andExpect(status().isUnauthorized());
    management.status(ownerA,new ActionInput(storeA,id,1));
    assertThrows(CoolException.class,()->accounts.validate(session.getStr("token"),false));assertThrows(CoolException.class,()->accounts.refresh(session.getStr("refreshToken")));
    assertNotNull(login(id,"Test123456").getStr("token"));
 }
 @Test void resetChangesPasswordAndRevokesSessionButKeepsDisabledState(){
    Long id=create("MANAGER");var session=login(id,"Test123456");
    management.resetPassword(ownerA,new ActionInput(storeA,id,null));
    assertThrows(CoolException.class,()->accounts.validate(session.getStr("token"),false));assertThrows(CoolException.class,()->accounts.refresh(session.getStr("refreshToken")));
    assertThrows(CoolException.class,()->login(id,"Test123456"));assertNotNull(login(id,"123456").getStr("token"));
    management.status(ownerA,new ActionInput(storeA,id,0));management.resetPassword(ownerA,new ActionInput(storeA,id,null));assertEquals(0,users.selectOneById(id).getStatus());
 }
 @Test void rebindingReplacesOnlyMembershipAndRevokesOldIdentity() throws Exception {
    Long id=create("MANAGER");var old=login(id,"Test123456");var user=users.selectOneById(id);
    management.save(ownerA,new SaveInput(id,storeA,storeA2,user.getUsername(),user.getName(),"STAFF",1,null));
    assertEquals(1,links.selectCountByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId,id)));
    assertEquals(storeA2,links.selectOneByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId,id)).getStoreId());
    assertThrows(CoolException.class,()->accounts.validate(old.getStr("token"),false));assertThrows(CoolException.class,()->accounts.refresh(old.getStr("refreshToken")));
    mvc.perform(get("/merchant/staff/me").header("Authorization",login(id,"Test123456").getStr("token"))).andExpect(jsonPath("$.data.storeId").value(storeA2.intValue())).andExpect(jsonPath("$.data.role").value("STAFF"));
 }
 @Test void rejectsForeignStoresOwnerAccountsDuplicateNamesAndRoleEscalation(){
    Long id=create("STAFF");var user=users.selectOneById(id);
    assertThrows(CoolException.class,()->management.page(ownerB,new PageInput(storeA,1,20,null)));
    assertThrows(CoolException.class,()->management.resetPassword(ownerB,new ActionInput(storeA,id,null)));
    assertThrows(CoolException.class,()->management.save(ownerA,new SaveInput(id,storeA,storeB,user.getUsername(),"测试","STAFF",1,null)));
    assertThrows(CoolException.class,()->management.save(ownerA,new SaveInput(null,storeA,null,user.getUsername(),"测试","STAFF",1,"123456")));
    assertThrows(CoolException.class,()->management.save(ownerA,new SaveInput(null,storeA,null,unique(),"测试","OWNER",1,"123456")));
    assertThrows(CoolException.class,()->management.resetPassword(ownerA,new ActionInput(storeA,ownerA,null)));
    assertThrows(CoolException.class,()->management.status(ownerA,new ActionInput(storeA2,id,0)));
    assertEquals(1,users.selectOneById(id).getStatus());
 }
 @Test void staffAndOtherOperatorsCannotManageWhileSuperAdminCan() throws Exception {
    Long id=create("STAFF");String staff=login(id,"Test123456").getStr("token");
    for(String path:List.of("/merchant/workspace/storeAccount/page","/merchant/workspace/storeAccount/save","/merchant/workspace/storeAccount/status","/merchant/workspace/storeAccount/resetPassword","/admin/merchant/storeAccount/page"))
      mvc.perform(post(path).header("Authorization",staff).contentType("application/json").content("{\"storeId\":"+storeA+",\"id\":"+id+"}")).andExpect(status().isForbidden());
    var admin=users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,"admin"));
    mvc.perform(post("/admin/merchant/storeAccount/page").header("Authorization",adminToken(admin)).contentType("application/json").content("{\"storeId\":"+storeA+"}"))
      .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.pagination.total").value(1));
    var ordinary=new BaseSysUserEntity();ordinary.setUsername(unique());ordinary.setName("普通运营");ordinary.setNickName("普通运营");ordinary.setPassword("unused");ordinary.setPasswordV(1);ordinary.setStatus(1);users.insertSelective(ordinary);
    mvc.perform(post("/admin/merchant/storeAccount/resetPassword").header("Authorization",adminToken(ordinary)).contentType("application/json").content("{\"storeId\":"+storeA+",\"id\":"+id+"}"))
      .andExpect(jsonPath("$.code").value(403));
 }
 @Test void databaseRejectsSecondStoreBinding(){Long id=create("STAFF");var link=new MerchantUserStoreEntity();link.setUserId(id);link.setMerchantId(merchantA);link.setStoreId(storeA2);assertThrows(org.springframework.dao.DuplicateKeyException.class,()->links.insert(link));}

 @Test void unifiedAccountsFilterByMerchantStoreRoleStatusAndKeyword(){
    Long staff=create("STAFF"), manager=create("MANAGER");
    var input=new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(merchantA,null,null,null,null,1,20);
    var all=backendAccounts.page(input);
    assertEquals(3,all.pagination().total());
    assertTrue(all.list().stream().allMatch(a->a.merchantId().equals(merchantA)&&a.bindingValid()));
    assertTrue(all.list().stream().anyMatch(a->a.id().equals(ownerA)&&a.storeName().equals("商家全部门店")));
    var filtered=backendAccounts.page(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(merchantA,storeA,"STAFF",1,"门店测试",1,20));
    assertEquals(1,filtered.pagination().total()); assertEquals(staff,filtered.list().get(0).id());
    assertEquals(stores.selectOneById(storeA).getName(),filtered.list().get(0).storeName());
    assertEquals(merchants.selectOneById(merchantA).getName(),filtered.list().get(0).merchantName());
    assertEquals(0,backendAccounts.page(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(merchantA,storeA2,null,null,null,1,20)).pagination().total());
    assertThrows(CoolException.class,()->backendAccounts.page(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(merchantA,storeB,null,null,null,1,20)));
    assertTrue(backendAccounts.groups().stream().anyMatch(g->g.merchantId().equals(merchantA)&&g.stores().size()==2));
 }
 @Test void unifiedOwnerMaintenanceRevokesOldSessionsAndPreservesIdentity(){
    var session=login(ownerA,"123456");
    backendAccounts.saveOwner(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.OwnerInput(ownerA,"更改姓名",0));
    assertEquals("更改姓名",users.selectOneById(ownerA).getName());
    assertThrows(CoolException.class,()->accounts.validate(session.getStr("token"),false));
    backendAccounts.resetOwner(ownerA); assertEquals(0,users.selectOneById(ownerA).getStatus());
    backendAccounts.saveOwner(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.OwnerInput(ownerA,"更改姓名",1));
    assertNotNull(login(ownerA,"123456").getStr("token"));
    assertThrows(CoolException.class,()->accounts.refresh(session.getStr("refreshToken")));
    assertEquals("OWNER",users.selectOneById(ownerA).getMerchantRole());assertEquals(merchantA,users.selectOneById(ownerA).getMerchantId());
    assertThrows(CoolException.class,()->backendAccounts.resetOwner(create("STAFF")));
 }
 @Test void platformCrudCannotMutateOrReadMerchantAccounts(){
    Long staff=create("STAFF"); var before=users.selectOneById(staff);
    assertThrows(CoolException.class,()->platformAccounts.info(staff));
    assertThrows(CoolException.class,()->platformAccounts.move(1L,new Long[]{staff}));
    assertThrows(CoolException.class,()->platformAccounts.delete(staff));
    var change=new BaseSysUserEntity();change.setId(staff);change.setStatus(0);change.setPassword("incorrect-hash-path");
    assertThrows(CoolException.class,()->platformAccounts.update(new cn.hutool.json.JSONObject(),change));
    var spoof=new BaseSysUserEntity();spoof.setUsername(unique());spoof.setMerchantId(merchantA);spoof.setMerchantRole("OWNER");
    assertThrows(CoolException.class,()->platformAccounts.add(new cn.hutool.json.JSONObject(),spoof));
    assertEquals(before.getPassword(),users.selectOneById(staff).getPassword());assertEquals(1,users.selectOneById(staff).getStatus());
 }
 @Test void unifiedEndpointsRequireSuperAdminAndNeverExposePassword() throws Exception {
    Long staff=create("STAFF");
    var admin=users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,"admin"));
    mvc.perform(post("/admin/merchant/backendAccount/page").header("Authorization",adminToken(admin)).contentType("application/json").content("{\"merchantId\":"+merchantA+"}"))
       .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.pagination.total").value(2))
       .andExpect(jsonPath("$.data.list[0].password").doesNotExist()).andExpect(jsonPath("$.data.list[0].passwordV").doesNotExist());
    var ordinary=new BaseSysUserEntity();ordinary.setUsername(unique());ordinary.setName("普通运营");ordinary.setNickName("普通运营");ordinary.setPassword("unused");ordinary.setPasswordV(1);ordinary.setStatus(1);users.insertSelective(ordinary);
    mvc.perform(get("/admin/merchant/backendAccount/groups").header("Authorization",adminToken(ordinary))).andExpect(jsonPath("$.code").value(403));
    mvc.perform(post("/admin/merchant/backendAccount/resetOwner").header("Authorization",login(staff,"Test123456").getStr("token")).contentType("application/json").content("{\"id\":"+ownerA+"}")).andExpect(status().isForbidden());
 }
 @Test void platformDepartmentPageExcludesMerchantEvenWithDepartmentAssigned() throws Exception {
    Long staff=create("STAFF");
    var department=departments.selectOneByQuery(QueryWrapper.create().limit(1));
    assertNotNull(department);
    var change=new BaseSysUserEntity();change.setId(staff);change.setDepartmentId(department.getId());users.update(change);
    var admin=users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,"admin"));
    mvc.perform(post("/admin/base/sys/user/page").header("Authorization",adminToken(admin)).contentType("application/json")
        .content("{\"page\":1,\"size\":20,\"departmentIds\":["+department.getId()+"],\"keyWord\":\""+users.selectOneById(staff).getUsername()+"\"}"))
        .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.pagination.total").value(0));
 }
 @Test void categoryFilterIncludesDescendantsAndKeepsOtherMerchantsOut(){
    var parent=new MerchantCategoryEntity();parent.setName(unique());parent.setSort(0);parent.setShowOnFront(1);parent.setStatus(1);categories.insertSelective(parent);
    var child=new MerchantCategoryEntity();child.setName(unique());child.setParentId(parent.getId());child.setSort(0);child.setShowOnFront(1);child.setStatus(1);categories.insertSelective(child);
    var change=new MerchantInfoEntity();change.setId(merchantA);change.setCategoryId(child.getId());merchants.update(change);
    create("STAFF");
    var result=backendAccounts.page(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(null,null,null,null,null,1,20,parent.getId()));
    assertEquals(2,result.pagination().total());assertTrue(result.list().stream().allMatch(a->a.merchantId().equals(merchantA)));
    assertEquals(0,backendAccounts.page(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(merchantB,null,null,null,null,1,20,parent.getId())).pagination().total());
    assertThrows(CoolException.class,()->backendAccounts.page(new com.cool.modules.merchant.controller.admin.params.BackendAccountParam.PageInput(null,null,null,null,null,1,20,-1L)));
    assertTrue(backendAccounts.groups().stream().anyMatch(g->g.merchantId().equals(merchantA)&&g.categoryId().equals(child.getId())));
 }
}
