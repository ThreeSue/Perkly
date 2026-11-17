package com.cool.modules.ai.merchant;

import static com.cool.modules.ai.merchant.MerchantChatModels.*;
import com.cool.core.exception.CoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 核销端只读聊天查询与会话隔离
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:29:18
 */

@Service @RequiredArgsConstructor
public class MerchantChatService {
 private final MerchantAiAccess access;
 private final MerchantChatPolicy policy;
 private final MerchantChatModel model;
 private final MerchantChatQueries queries;
 private final ObjectMapper json;
 private final Map<String,Session> sessions=new HashMap<>();
 private static class Session {
  final Long actor,store;final String login;Instant touched=Instant.now();Conditions conditions=Conditions.empty();
  Session(Long a,Long s,String l){actor=a;store=s;login=l;}
 }
 private String fingerprint(String token){
  if(token==null||token.isBlank())throw new CoolException("登录凭据无效",401);
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 private synchronized Session session(String id,Long actor,Long store,String token){
  var s=sessions.get(id);
  if(s==null||s.touched.plusSeconds(1800).isBefore(Instant.now())||!s.actor.equals(actor)||!s.store.equals(store)||!s.login.equals(fingerprint(token)))
   throw new CoolException("会话已失效，请重新开始",409);return s;
 }
 public Reply chat(Long actor,String token,Chat input){
  if(input==null||input.message()==null||input.message().isBlank()||input.message().length()>1000)throw new CoolException("请输入1至1000字的问题",400);
  if(input.orderSummary()&&(input.orderId()==null||input.orderId()<=0))throw new CoolException("请先选择消费账单",400);
  var scope=access.require(actor,null);String id=input.sessionId();Session s;
  synchronized(this){
   sessions.entrySet().removeIf(e->e.getValue().touched.plusSeconds(1800).isBefore(Instant.now()));
   if(id==null||id.isBlank()){
    if(sessions.size()>=2000)throw new CoolException("会话繁忙，请稍后再试",503);
    id=UUID.randomUUID().toString();s=new Session(actor,scope.storeId(),fingerprint(token));sessions.put(id,s);
   }else s=session(id,actor,scope.storeId(),token);
  }
  synchronized(s){
   policy.consume(actor);
   if(input.orderSummary()){
    var conditions=new Conditions("TODAY",null,null,null,null,input.orderId(),null);
    var facts=queries.query(actor,Action.ORDER,conditions,null);
    s.conditions=conditions;s.touched=Instant.now();
    return new Reply(id,summarizeOrder(facts),Action.ORDER,conditions,facts,List.of(),false,"READ_ONLY");
   }
   String message=MerchantChatQueries.redact(input.message());
   if(input.code()!=null&&!input.code().isBlank())message=message.replace(input.code(),"[券码已隐藏]");
   var prev=s.conditions;
   if(input.couponId()!=null||input.orderId()!=null)prev=new Conditions(prev.period(),prev.startDate(),prev.endDate(),prev.operatorId(),input.couponId()!=null?input.couponId():prev.couponId(),input.orderId()!=null?input.orderId():prev.orderId(),prev.keyword());
   Plan plan;
   try{plan=model.plan(message,prev,actor);if(plan==null||plan.action()==null||plan.conditions()==null)throw new IllegalStateException();}
   catch(Exception e){throw new CoolException("AI暂时无法理解请求，请重试",503);}
   var c=plan.conditions();access.require(actor,c.operatorId());
   if(plan.action()==Action.ASK||plan.action()==Action.UNSUPPORTED){
    String reply=plan.action()==Action.UNSUPPORTED?"我只能查询和解释，不能核销、支付、撤销或修改数据。":Objects.toString(plan.question(),"请说明要查询的券或账单。");
    return new Reply(id,reply,plan.action(),s.conditions,json.createArrayNode(),List.of("今天的核销统计","查本店优惠券"),false,"NONE");
   }
   var facts=queries.query(actor,plan.action(),c,input.code());String answer;boolean degraded=false;
   try{answer=model.answer(facts);}catch(Exception e){answer="查询已完成，但AI总结暂时不可用，请查看下方真实查询结果，可稍后重试。";degraded=true;}
   s.conditions=c;s.touched=Instant.now();
   return new Reply(id,answer,plan.action(),c,facts,List.of("改查近七天","改查今天"),degraded,"READ_ONLY");
  }
 }
 private String summarizeOrder(JsonNode facts){
  var status=facts.path("status").asText();
  var summary="这笔消费账单的状态是"+facts.path("statusText").asText("未知")+"。";
  if("CANCELLED".equals(status))return summary;
  var completed="COMPLETED".equals(status);
  var amount=facts.path(completed?"receivedAmountCents":"payableAmountCents");
  if(!amount.isIntegralNumber())return summary+"暂无补款金额记录。";
  return summary+(completed?"本次实收补款":"本次应补款")+" ¥"+BigDecimal.valueOf(amount.longValue(),2).toPlainString()+"。";
 }
 public synchronized void clear(Long actor,String token,String id){
  var scope=access.require(actor,null);session(id,actor,scope.storeId(),token);sessions.remove(id);
 }
}
