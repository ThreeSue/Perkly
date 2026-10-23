package com.cool.modules.merchant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.hutool.json.*;
import com.cool.core.cache.CoolCache;
import java.nio.file.*;
import java.util.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 显式使用已准备的门店实验账号经HTTP鉴权验证真实券预检核销及店长员工记录范围
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 10:45:30
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"sms-auth.access-key-id=staff-pilot-unused","sms-auth.access-key-secret=staff-pilot-unused"})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named="merchant.staff.pilot",matches="true")
class MerchantStaffPilotFlowTest {
 @Autowired CoolCache cache;
 @Autowired WebApplicationContext web;
 @Autowired @Qualifier("springSecurityFilterChain") Filter security;
 MockMvc mvc;
 JSONObject post(String path, Object body, String token) throws Exception {
   var req=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content(JSONUtil.toJsonStr(body));
   if(token!=null)req.header("Authorization",token);
   return JSONUtil.parseObj(mvc.perform(req).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
 }
 String login(JSONObject account) throws Exception {
   String fixture="pilot_fixture_"+UUID.randomUUID();cache.set("verify:img:"+fixture,"1234",30);
   JSONObject result=post("/merchant/auth/login",Map.of("username",account.getStr("username"),"password",account.getStr("password"),"captchaId",fixture,"verifyCode","1234"),null);
   assertEquals(1000,result.getInt("code"));return result.getJSONObject("data").getStr("token");
 }
 @Test void runPilot() throws Exception {
   mvc=MockMvcBuilders.webAppContextSetup(web).addFilters(security).build();
   var data=JSONUtil.parseObj(Files.readString(Path.of("../testing/private/merchant-store-pilot.json")));
   var accounts=data.getJSONArray("accounts");String manager=login(accounts.getJSONObject(0)),staff=login(accounts.getJSONObject(1));
   for(String token:List.of(manager,staff)) mvc.perform(get("/merchant/staff/me").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.storeId").value(1));
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/merchant/workspace/store/save").header("Authorization",staff).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   assertNotEquals(1000,post("/merchant/redemption/page",Map.of("scope","store"),staff).getInt("code"));
   String code=data.getJSONArray("coupons").getJSONObject(0).getStr("code");
   assertNotEquals(1000,post("/merchant/redemption/preview",Map.of("code","bad","consumptionAmount",12000),staff).getInt("code"));
   assertNotEquals(1000,post("/merchant/redemption/preview",Map.of("code",code,"storeId",11,"consumptionAmount",12000),staff).getInt("code"));
   assertNotEquals(1000,post("/merchant/redemption/preview",Map.of("code",code,"consumptionAmount",10000),staff).getInt("code"));
   var input=Map.of("code",code,"consumptionAmount",12000);
   JSONObject preview=post("/merchant/redemption/preview",input,staff);assertEquals(1000,preview.getInt("code"));assertEquals(2000,preview.getJSONObject("data").getInt("discountAmount"));
   JSONObject confirm=post("/merchant/redemption/confirm",input,staff);assertEquals(1000,confirm.getInt("code"));assertTrue(confirm.getJSONObject("data").getBool("confirmed"));
   JSONObject duplicate=post("/merchant/redemption/confirm",input,staff);assertNotEquals(1000,duplicate.getInt("code"));assertTrue(duplicate.getStr("message").contains("已核销"));
   assertEquals(1,post("/merchant/redemption/page",Map.of("scope","self"),staff).getJSONObject("data").getJSONArray("list").size());
   assertEquals(0,post("/merchant/redemption/page",Map.of("scope","self"),manager).getJSONObject("data").getJSONArray("list").size());
   assertEquals(1,post("/merchant/redemption/page",Map.of("scope","store"),manager).getJSONObject("data").getJSONArray("list").size());
   assertEquals(1000,post("/merchant/auth/logout",Map.of(),staff).getInt("code"));
   mvc.perform(get("/merchant/staff/me").header("Authorization",staff)).andExpect(status().isUnauthorized());
   post("/merchant/auth/logout",Map.of(),manager);
   Files.writeString(Path.of("../testing/merchant-store-pilot-result.json"),JSONUtil.toJsonPrettyStr(Map.of("storeName",data.getStr("storeName"),"redemption",confirm.getJSONObject("data"),"checks",List.of("店长员工登录","自动绑定门店","员工运营接口403","员工全店记录拒绝","无效券码拒绝","跨门店拒绝","消费门槛拒绝","120元减20元预检","确认核销","重复核销拒绝","本人和本店记录隔离","退出后Token失效"))));
 }
}
