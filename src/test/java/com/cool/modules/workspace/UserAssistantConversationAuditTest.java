package com.cool.modules.workspace;

import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.entity.CouponPublicationEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录真实模型用户助手接口输入输出及只读操作验收表并回滚测试数据
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:29:16
 */
@EnabledIfEnvironmentVariable(named="PERKLY_USER_AI_AUDIT",matches="true")
@TestPropertySource(properties={"perkly.ai.worker-enabled=false","logging.level.com.cool=INFO"})
class UserAssistantConversationAuditTest extends FeatureModulesIntegrationTest {
    @org.springframework.boot.test.mock.mockito.SpyBean com.cool.modules.ai.app.UserAssistantModel liveModel;
    @Autowired ObjectMapper json;
    @Autowired com.cool.modules.coupon.mapper.CouponTradeMapper tradeRows;
    final List<Map<String,Object>> records=new ArrayList<>();
    String app,other,merchantToken;

    @Test void recordRealConversations() throws Exception {
        org.mockito.Mockito.doAnswer(call->{
            var result=(com.cool.modules.ai.app.UserAssistantModels.Result)call.getArgument(2);
            if(result.cards().stream().anyMatch(card->!card.title().startsWith(prefix) && card.kind()!=com.cool.modules.ai.app.UserAssistantModels.Kind.ORDER))
                throw new IllegalStateException("测试保护：禁止向模型发送非本次夹具的公开记录");
            return call.callRealMethod();
        }).when(liveModel).answer(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        try {
            var branch=stores.selectOneById(store);branch.setAddress("福建省福州市鼓楼区测试街1号");stores.update(branch);
            var free=coupon("免费咖啡券",0,1);var paid=coupon("付费咖啡券",500,1);coupon("下架咖啡券",100,2);
            var history=coupon("历史咖啡券",0,1);
            var claim=trade.claim(customer,history.getId(),1,UUID.randomUUID().toString());
            long walletId=((Number)((List<?>)claim.get("couponIds")).get(0)).longValue();
            long orderId=((Number)claim.get("id")).longValue();
            tradeRows.change("customer_coupon",walletId,com.mybatisflex.core.row.Row.of("valid_to",new Date(System.currentTimeMillis()-60000)));
            var disabled=new CouponTemplateEntity();disabled.setId(history.getId());disabled.setStatus(2);coupons.update(disabled);
            long ordersBefore=trade.orders(customer,1,1,null).getPagination().getTotal();
            long walletBefore=trade.wallet(customer,1,1,null).getPagination().getTotal();
            app=token(customer,UserTypeEnum.APP);other=token(otherCustomer,UserTypeEnum.APP);merchantToken=token(owner,UserTypeEnum.MERCHANT);
            keys.forEach(k->cache.set(k,cache.get(k),3600));

            var guest=call("游客禁止",null,Map.of("message","推荐便宜的券"));check(guest,guest.path("code").asInt()!=1000,"游客请求被拒绝，未查询业务");
            var merchantReply=call("商家身份禁止",merchantToken,Map.of("message","推荐便宜的券"));check(merchantReply,merchantReply.path("code").asInt()!=1000,"商家Token不能冒充APP用户");
            var first=call("低价推荐",app,Map.of("message","找名字包含“"+prefix+"”的便宜优惠券"));
            check(first,ok(first)&&ids(first).contains(free.getId())&&ids(first).contains(paid.getId())&&ids(first).size()==2,"应仅推荐免费及5元在售券，排除下架和历史券");
            String session=first.path("data").path("sessionId").asText();
            var onlyFree=call("继承条件，只看免费",app,Map.of("sessionId",session,"message","只看免费的"));
            check(onlyFree,ok(onlyFree)&&ids(onlyFree).equals(List.of(free.getId())),"继承上一轮名称条件，筛选免费券");
            var onlyPaid=call("纠正条件，改付费",app,Map.of("sessionId",session,"message","改成付费的，买券不超过10元"));
            check(onlyPaid,ok(onlyPaid)&&ids(onlyPaid).equals(List.of(paid.getId())),"将免费条件改为付费，购券价上限1000分");
            var area=call("无GPS按城市找店",app,Map.of("message","找福州名字包含“"+prefix+"”的门店"));
            check(area,ok(area)&&ids(area).contains(store),"按门店地址和名称查询，不要求提供GPS");
            var nearby=call("缺定位找附近",app,Map.of("message","附近有什么门店"));
            check(nearby,ok(nearby)&&ids(nearby).isEmpty(),"没有GPS也没有区域时询问地点，不编造附近店");
            var none=call("没有匹配结果",app,Map.of("message","找名字包含绝不存在的测试商家xyz987654的优惠券"));
            check(none,ok(none)&&ids(none).isEmpty(),"返回无结果，不编造优惠券");
            var wallet=call("查询我的券",app,Map.of("message","看看我的券"));
            check(wallet,ok(wallet)&&ids(wallet).equals(List.of(walletId)),"仅查当前用户持券");
            var rule=call("解释过期下架历史券",app,Map.of("message","这张券怎么用，现在还能用吗", "target",Map.of("kind","WALLET","id",walletId)));
            check(rule,ok(rule)&&ids(rule).equals(List.of(walletId))&&!rule.toString().contains("qrCode")&&rule.path("data").path("cards").get(0).path("statusText").asText().equals("已过期"),"读取本人持券快照，过期券不表示可领购或可核销");
            var orders=call("查询我的订单",app,Map.of("message","查看我的订单"));
            check(orders,ok(orders)&&ids(orders).equals(List.of(orderId)),"仅查询当前用户购券订单");
            var cross=call("跨用户查看持券",other,Map.of("message","解释这张券", "target",Map.of("kind","WALLET","id",walletId)));
            check(cross,cross.path("code").asInt()!=1000,"拒绝查看其他用户持券");
            var crossOrder=call("跨用户查看订单",other,Map.of("message","查看这个订单", "target",Map.of("kind","ORDER","id",orderId)));
            check(crossOrder,crossOrder.path("code").asInt()!=1000,"拒绝查看其他用户购券订单");
            var crossSession=call("跨用户继续会话",other,Map.of("sessionId",session,"message","继续"));
            check(crossSession,crossSession.path("code").asInt()==404,"拒绝访问别人的会话");
            var write=call("拒绝代下单",app,Map.of("message","帮我买一张优惠券并支付"));
            check(write,ok(write)&&ids(write).isEmpty(),"仅引导用户进入业务页面，不实际下单支付");
            var badDelete=remove("跨用户清除会话",other,session);check(badDelete,badDelete.path("code").asInt()==404,"其他用户不能清除会话");
            var deleted=remove("本人清除会话",app,session);check(deleted,deleted.path("code").asInt()==1000,"删除当前用户短期会话");
            var expired=call("清除后继续旧会话",app,Map.of("sessionId",session,"message","继续找券"));
            check(expired,expired.path("code").asInt()==404,"已清除的会话不能继续使用");
            assertEquals(ordersBefore,trade.orders(customer,1,1,null).getPagination().getTotal(),"助手不得新增订单");
            assertEquals(walletBefore,trade.wallet(customer,1,1,null).getPagination().getTotal(),"助手不得发券");
        } finally {writeReport();}
        assertTrue(records.stream().allMatch(r->Boolean.TRUE.equals(r.get("pass"))),"存在未通过项，请查看原始记录与验收表");
    }
    private JsonNode call(String name,String token,Map<String,Object> body) throws Exception {
        var request=post("/app/ai/assistant/chat").contentType("application/json").content(json.writeValueAsString(body));
        if(token!=null)request.header("Authorization",token);
        long started=System.currentTimeMillis();var response=mvc.perform(request).andReturn().getResponse();
        return record(name,"POST /app/ai/assistant/chat",body,response.getStatus(),response.getContentAsString(StandardCharsets.UTF_8),started);
    }
    private JsonNode remove(String name,String token,String session) throws Exception {
        long started=System.currentTimeMillis();var response=mvc.perform(delete("/app/ai/assistant/session").header("Authorization",token).param("sessionId",session)).andReturn().getResponse();
        return record(name,"DELETE /app/ai/assistant/session",Map.of("sessionId",session),response.getStatus(),response.getContentAsString(StandardCharsets.UTF_8),started);
    }
    private JsonNode record(String name,String endpoint,Object input,int status,String body,long started) throws Exception {
        var response=json.readTree(body);var row=new LinkedHashMap<String,Object>();row.put("scenario",name);row.put("endpoint",endpoint);row.put("request",input);row.put("httpStatus",status);row.put("response",response);row.put("durationMs",System.currentTimeMillis()-started);records.add(row);writeReport();return response;
    }
    private void check(JsonNode response,boolean pass,String operation) throws Exception {var row=records.get(records.size()-1);row.put("pass",pass);row.put("operation",operation);writeReport();System.out.println("USER_AI_AUDIT "+row.get("scenario")+" "+pass);}
    private boolean ok(JsonNode node){return node.path("code").asInt()==1000&&!node.path("data").path("degraded").asBoolean();}
    private List<Long> ids(JsonNode node){List<Long> ids=new ArrayList<>();node.path("data").path("cards").forEach(c->ids.add(c.path("id").asLong()));return ids;}
    private void writeReport() throws Exception {
        Path dir=Path.of("logs/test/user-ai-audit");Files.createDirectories(dir);
        Files.writeString(dir.resolve("responses.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(records));
        StringBuilder table=new StringBuilder("# 用户端AI真实模型接口实测记录\n\n测试方式：MockMvc经过实际安全过滤器、控制器与服务，真实数据库查询及百炼模型调用；不是外部HTTP端口/真机测试。测试账号、门店、券、订单为事务内临时夹具，结束回滚。Token和模型密钥不记录。\n\n| 场景 | 接口输入 | 原样回复 | 返回卡片 | 执行/检查 | 结果 | 耗时 |\n|---|---|---|---|---|---|---|\n");
        for(var row:records){var r=(JsonNode)row.get("response");String reply=r.path("data").path("message").asText(r.path("message").asText());StringJoiner cards=new StringJoiner("；");r.path("data").path("cards").forEach(c->cards.add(c.path("title").asText()+"（"+c.path("statusText").asText()+"，"+c.path("priceCents").asText("未知")+"分）"));
            table.append("| ").append(cell(row.get("scenario"))).append(" | ").append(cell(json.writeValueAsString(row.get("request")))).append(" | ").append(cell(reply)).append(" | ").append(cell(cards)).append(" | ").append(cell(row.get("operation"))).append(" | ").append(row.containsKey("pass")?(Boolean.TRUE.equals(row.get("pass"))?"通过":"待核查/未通过"):"待检查").append(" | ").append(row.get("durationMs")).append("ms |\n");}
        Files.writeString(dir.resolve("测试记录.md"),table.toString());
    }
    private String cell(Object value){return Objects.toString(value,"").replace("|","\\|").replace("\r","").replace("\n","<br>");}
    private CouponTemplateEntity coupon(String suffix,int cents,int state){var c=new CouponTemplateEntity();c.setMerchantId(merchant);c.setName(prefix+suffix);c.setDescription("仅限到店使用，不找零，不兑换现金。");c.setType("CASH");c.setSalePrice(BigDecimal.valueOf(cents,2));c.setDeductAmount(BigDecimal.TEN);c.setScopeType(0);c.setValidityType("AFTER_ISSUE");c.setValidDays(30);c.setIssuedCount(0L);c.setPerCustomerLimit(2);c.setRuleVersion(1);c.setStatus(1);coupons.insertSelective(c);var p=new CouponPublicationEntity();p.setMerchantId(merchant);p.setCouponTemplateId(c.getId());p.setChannel("PLATFORM");p.setAuditStatus(2);p.setPublishStatus(state);p.setPublishedVersion(1);p.setPublishedAt(new Date());publications.insertSelective(p);return c;}
}
