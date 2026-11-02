package com.cool.modules.workspace;

import cn.hutool.core.lang.Dict;
import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.security.jwt.*;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.workspace.entity.*;
import com.cool.modules.workspace.mapper.*;
import com.cool.modules.workspace.service.WorkspaceNoticeService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.Filter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * @Description: 在事务回滚的真实数据库与令牌过滤链中验证下级通知
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:33:46
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"sms-auth.access-key-id=notice-test-unused","sms-auth.access-key-secret=notice-test-unused"})
@ActiveProfiles("local") @Transactional
class WorkspaceNoticeIntegrationTest {
    @Autowired WorkspaceNoticeService service;
    @Autowired WorkspaceNoticeMapper notices;
    @Autowired WorkspaceNoticeRecipientMapper recipients;
    @Autowired BaseSysUserMapper users;
    @Autowired MerchantInfoMapper merchants;
    @Autowired MerchantCategoryMapper categories;
    @Autowired CoolCache cache;
    @Autowired JwtTokenUtil tokens;
    @Autowired WebApplicationContext web;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @org.springframework.boot.test.mock.mockito.MockBean com.cool.modules.user.util.UserSmsUtil sms;
    List<String> keys=new ArrayList<>();
    String prefix="notice_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security,new com.cool.core.request.RequestParamsFilter()).build();}
    @AfterEach void clean(){SecurityContextHolder.clearContext();keys.forEach(cache::del);}
    BaseSysUserEntity account(String name,Long merchantId){
        var u=new BaseSysUserEntity();u.setUsername(prefix+name);u.setName("通知测试"+name);u.setNickName(u.getName());
        u.setPassword("not-a-login-password");u.setPasswordV(1);u.setStatus(1);u.setMerchantId(merchantId);
        u.setAccountType(merchantId==null?"PLATFORM":"MERCHANT");u.setMerchantRole(merchantId==null?null:"OWNER");
        u.setStoreScope("ALL");users.insertSelective(u);return u;
    }
    String token(BaseSysUserEntity u,String... perms){
        List<org.springframework.security.core.GrantedAuthority> authorities=new ArrayList<>();authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for(String p:perms)authorities.add(new SimpleGrantedAuthority(p));
        var principal=new JwtUser(u.getId(),u.getUsername(),"",authorities,true);principal.setUserTypeEnum(UserTypeEnum.ADMIN);
        String details="admin:userDetails:"+u.getUsername(),version="admin:passwordVersion:"+u.getId();
        cache.set(details,principal,120);cache.set(version,1,120);keys.add(details);keys.add(version);
        return tokens.generateToken(Dict.create().set("userType","ADMIN").set("userId",u.getId()).set("username",u.getUsername()).set("passwordVersion",1));
    }
    BaseSysUserEntity merchant(){
        var c=new MerchantCategoryEntity();c.setName(prefix);c.setStatus(1);c.setSort(0);categories.insertSelective(c);
        var m=new MerchantInfoEntity();m.setName(prefix);m.setCategoryId(c.getId());m.setContactName("通知测试");m.setContactPhone("13800000001");m.setStatus(1);merchants.insertSelective(m);
        return account("recipient",m.getId());
    }
    @Test void authenticatedSendIdempotencyAndIndependentReadPersist() throws Exception {
        var admin=account("sender",null);var target=merchant();String token=token(admin,"notice:send");
        String body="{\"requestId\":\"notice_integration_01\",\"title\":\"通知测试\",\"content\":\"内容\",\"recipientIds\":["+target.getId()+"]}";
        mvc.perform(post("/admin/workspace/notices/send").header("Authorization",token).contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(1000));
        mvc.perform(post("/admin/workspace/notices/send").header("Authorization",token).contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(1000));
        var list=notices.selectListByQuery(QueryWrapper.create().eq(WorkspaceNoticeEntity::getSenderId,admin.getId()));assertEquals(1,list.size());
        Long id=list.get(0).getId();assertEquals(1,service.unread(target.getId()));
        assertEquals("通知测试",service.detail(target.getId(),id).title());service.read(target.getId(),id);
        Date read=service.detail(target.getId(),id).readAt();assertNotNull(read);service.read(target.getId(),id);assertEquals(read,service.detail(target.getId(),id).readAt());assertEquals(0,service.unread(target.getId()));
        mvc.perform(get("/admin/workspace/notices/detail?id="+id).header("Authorization",token)).andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/admin/workspace/notices/receipts?id="+id).header("Authorization",token)).andExpect(jsonPath("$.data[0].name").value(target.getName()));
        mvc.perform(post("/admin/workspace/notices/send").header("Authorization",token).contentType("application/json").content(body.replace("内容","变更"))).andExpect(jsonPath("$.code").value(409));
    }
    @Test void platformWithoutSendPermissionAndMixedInvalidBatchAreRejected() throws Exception {
        var admin=account("denied",null);String denied=token(admin);var target=merchant();
        String body="{\"requestId\":\"notice_integration_02\",\"title\":\"标题\",\"content\":\"正文\",\"recipientIds\":["+target.getId()+"]}";
        mvc.perform(post("/admin/workspace/notices/send").header("Authorization",denied).contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(403));
        String allowed=token(admin,"notice:send");
        mvc.perform(post("/admin/workspace/notices/send").header("Authorization",allowed).contentType("application/json").content(body.replace(target.getId()+"]",target.getId()+","+admin.getId()+"]"))).andExpect(jsonPath("$.code").value(403));
        assertEquals(0,notices.selectCountByQuery(QueryWrapper.create().eq(WorkspaceNoticeEntity::getSenderId,admin.getId())));
        mvc.perform(get("/admin/workspace/notices/unread")).andExpect(status().isUnauthorized());
    }
}
