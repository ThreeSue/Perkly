package com.cool.modules.ai.merchant;

import static com.cool.modules.ai.merchant.MerchantChatModels.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponRedemptionEntity;
import com.cool.modules.coupon.mapper.CouponRedemptionMapper;
import com.cool.modules.coupon.service.*;
import com.cool.modules.merchant.service.MerchantWorkspaceService;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.paginate.Page;
import java.time.*;
import java.util.*;
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
public class MerchantChatQueries {
 private final MerchantAiAccess access;
 private final MerchantAiStatistics statistics;
 private final WorkspaceAuthorization authorization;
 private final MerchantWorkspaceService workspace;
 private final ConsumptionOrderService orders;
 private final CouponTradeService trade;
 private final CouponRedemptionMapper records;
 private final ObjectMapper json;
 // 只保留业务展示字段；券码、二维码、凭据、客户联系方式及备注永不送模型。
 private static final Set<String> FIELDS=Set.of("id","name","couponName","type","typeText","description","rule","minSpend","discountAmount",
  "deductAmount","discountRate","rate","contentItems","contents","quantity","itemName","validFrom","validTo","status","statusText",
  "storeName","merchantName","requiresConsumptionAmount","nextAction","list","pagination","total","page","size","scopeType");
 public JsonNode query(Long actor,Action action,Conditions c,String code){
  var scope=access.require(actor,c.operatorId());
  return switch(action){
   case STATISTICS -> json.valueToTree(statistics.query(actor,new MerchantAiStatistics.Input(c.period(),c.startDate(),c.endDate(),c.operatorId())));
   case COUPONS -> {authorization.require(actor,"coupon:view");yield safe(json.valueToTree(c.couponId()==null?workspace.couponPage(actor,1,20,c.keyword()):workspace.couponInfo(actor,c.couponId())));}
   case ORDER -> {
    if(c.orderId()==null)throw new CoolException("请先选择消费账单",400);
    var o=orders.info(actor,c.orderId());
    if(scope.operatorId()!=null && !scope.operatorId().equals(o.operatorId()))throw new CoolException("订单不在所选人员范围",403);
    var node=json.createObjectNode();node.put("id",o.id());node.put("statusText",o.statusText());node.put("couponName",redact(o.couponName()));
    node.set("consumptionAmountCents",json.valueToTree(o.consumptionAmount()));node.set("discountAmountCents",json.valueToTree(o.discountAmount()));yield node;
   }
   case RECORDS -> {
    var d=MerchantAiStatistics.dates(new MerchantAiStatistics.Input(c.period(),c.startDate(),c.endDate(),c.operatorId()),LocalDate.now(ZoneId.of("Asia/Shanghai")));
    var zone=ZoneId.of("Asia/Shanghai");
    var q=QueryWrapper.create().eq(CouponRedemptionEntity::getMerchantId,scope.merchantId()).eq(CouponRedemptionEntity::getStoreId,scope.storeId())
     .ge(CouponRedemptionEntity::getRedeemedAt,Date.from(d.startDate().atStartOfDay(zone).toInstant()))
     .lt(CouponRedemptionEntity::getRedeemedAt,Date.from(d.endDate().plusDays(1).atStartOfDay(zone).toInstant()));
    if(scope.operatorId()!=null)q.eq(CouponRedemptionEntity::getOperatorId,scope.operatorId()).eq(CouponRedemptionEntity::getOperatorType,"MERCHANT");
    q.orderBy(CouponRedemptionEntity::getRedeemedAt,false).orderBy(CouponRedemptionEntity::getId,false);
    var page=records.paginate(new Page<CouponRedemptionEntity>(1,20),q);var node=json.createObjectNode();node.put("total",page.getTotalRow());node.put("partial",page.getTotalRow()>20);
    var list=node.putArray("list");for(var r:page.getRecords()){var item=list.addObject();item.put("id",r.getId());item.set("orderId",json.valueToTree(r.getConsumptionOrderId()));item.set("consumptionAmountYuan",json.valueToTree(r.getConsumptionAmount()));item.set("redeemedAt",json.valueToTree(r.getRedeemedAt()));}yield node;
   }
   case INSPECT -> {
    authorization.require(actor,"redeem:use");if(code==null||code.isBlank())throw new CoolException("请通过扫码上下文提供券码",400);
    try{yield safe(json.valueToTree(trade.inspect(actor,scope.storeId(),code)));}
    catch(CoolException e){if(e.getCode()==401||e.getCode()==403)throw e;var n=json.createObjectNode();n.put("valid",false);n.put("reason",redact(e.getMsg().replace(code,"[券码已隐藏]")));yield n;}
   }
   default -> throw new CoolException("不支持的查询",400);
  };
 }
 static String redact(String s){return s==null?null:s.replaceAll("https?://[^\\s]+","[链接已隐藏]").replaceAll("[A-Za-z0-9_+/=]{6,}","[标识已隐藏]");}
 JsonNode safe(JsonNode node){
  if(node.isObject()){var out=json.createObjectNode();node.fields().forEachRemaining(e->{if(FIELDS.contains(e.getKey()))out.set(e.getKey(),safe(e.getValue()));});return out;}
  if(node.isArray()){var out=json.createArrayNode();node.forEach(n->out.add(safe(n)));return out;}
  return node.isTextual()?TextNode.valueOf(redact(node.asText())):node;
 }
}
