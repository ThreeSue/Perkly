package com.cool.modules.support.chat;
import static com.cool.modules.support.chat.StoreChatModels.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.*;import com.cool.modules.support.mapper.*;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.wallet.mapper.WalletBillMapper;
import com.cool.modules.wallet.entity.WalletBillEntity;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;
import java.util.*;import java.math.BigDecimal;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 构建经归属核验的客服卡片快照与实时状态且绝不读取核销码
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:23:00
 */
@Service @RequiredArgsConstructor
public class StoreChatCards {
 private final SupportOwnedCouponMapper owned;private final StoreChatMessageMapper messages;
 private final AppDiscoveryService discovery;private final WalletBillMapper bills;private final ObjectMapper json;
 public Card build(StoreChatEntity chat,String sender,CardRef ref){
  if(ref==null)return null;
  if(ref.type()==null || ref.id()==null || ref.id()<1)throw new CoolException("卡片引用无效",400);
  CardData data;boolean applicable=true;
  switch(ref.type()){
   case STORE -> {
    if(!ref.id().equals(chat.getStoreId()))throw new CoolException("只能发送当前会话门店卡片",403);
    JsonNode row=json.valueToTree(discovery.store(ref.id()));data=publicData(row,"门店资料",false);
   }
   case COUPON_TEMPLATE -> {
    JsonNode row=json.valueToTree(discovery.coupon(ref.id()));
    boolean found=false;for(JsonNode s:row.path("stores"))if(s.path("id").asLong()==chat.getStoreId())found=true;
    if(!found)throw new CoolException("优惠券不适用当前会话门店或未正式发布",403);
    data=publicData(row,"正式优惠券",true);
   }
   case CUSTOMER_COUPON -> {
    var row=owned.selectOneByQuery(QueryWrapper.create().eq(SupportOwnedCouponEntity::getId,ref.id()).eq(SupportOwnedCouponEntity::getCustomerId,chat.getCustomerId()));
    if(row==null)throw new CoolException("持有券不存在或不属于会话顾客",403);
    shared(chat,sender,ref);var rule=parse(row.getRuleSnapshot());
    applicable=applicable(chat,row,rule);
    List<Fact> facts=new ArrayList<>();facts.add(new Fact("持有券状态",couponState(row)));
    if(applicable){facts.addAll(ruleFacts(rule,false));add(facts,"生效时间",row.getValidFrom());add(facts,"截止时间",row.getValidTo());}
    else facts.add(new Fact("适用范围","不适用当前门店；不能在本会话查看其他门店订单或资料"));
    data=new CardData(text(rule,"name","历史优惠券"),text(rule,"cover",null),couponState(row),List.copyOf(facts));
   }
   case ORDER -> {
    var row=bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getId,ref.id()).eq(WalletBillEntity::getCustomerId,chat.getCustomerId())
     .eq(WalletBillEntity::getStoreId,chat.getStoreId()).eq(WalletBillEntity::getMerchantId,chat.getMerchantId()));
    if(row==null)throw new CoolException("消费订单不属于会话顾客或当前门店",403);
    shared(chat,sender,ref);
    var facts=new ArrayList<Fact>();add(facts,"订单号",row.getBillNo());add(facts,"订单状态",row.getStatus());
    cents(facts,"消费金额",row.getAmount());cents(facts,"实付金额",row.getPaidAmount());
    data=new CardData("到店消费订单 "+row.getBillNo(),null,row.getStatus(),List.copyOf(facts));
   }
   default -> throw new CoolException("不支持的卡片类型",400);
  }
  return new Card(ref.type(),ref.id(),data.title(),data.image(),data,data,applicable,true,applicable?"以当前状态和正式规则为准":"不适用当前门店");
 }
 private void shared(StoreChatEntity chat,String sender,CardRef ref){
  if(!"WORKSPACE".equals(sender))return;
  if(messages.selectCountByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chat.getId())
    .eq(StoreChatMessageEntity::getSenderKind,"APP").eq(StoreChatMessageEntity::getCardType,ref.type().name()).eq(StoreChatMessageEntity::getCardRefId,ref.id()))==0)
   throw new CoolException("员工只能引用顾客已在本会话分享的持有券或订单",403);
 }
 public Card refresh(StoreChatEntity chat,Card snapshot){
  if(snapshot==null)return null;
  try{var current=build(chat,"AI",new CardRef(snapshot.type(),snapshot.id()));
   return new Card(snapshot.type(),snapshot.id(),snapshot.title(),snapshot.image(),snapshot.snapshot(),current.current(),current.applicable(),true,current.note());
  }catch(CoolException e){return new Card(snapshot.type(),snapshot.id(),snapshot.title(),snapshot.image(),snapshot.snapshot(),null,false,false,"当前资料已失效或不可查看；以下仅为发送时快照");}
 }
 public Card decode(String data){if(data==null || data.isBlank())return null;try{return json.readValue(data,Card.class);}catch(Exception e){return null;}}
 public String encode(Card card){if(card==null)return null;try{return json.writeValueAsString(card);}catch(Exception e){throw new CoolException("卡片无法保存",400);}}
 public Options coupons(StoreChatEntity chat,String sender,String keyword,boolean all,int page,int size){
  com.cool.modules.support.service.SupportAccess.page(page,size);validateKeyword(keyword);
  List<Card> list=new ArrayList<>();long matched=0;Long before=null;long offset=(long)(page-1)*size;
  while(true){
   var q=QueryWrapper.create().eq(SupportOwnedCouponEntity::getCustomerId,chat.getCustomerId());
   if(!all)q.eq(SupportOwnedCouponEntity::getMerchantId,chat.getMerchantId());if(before!=null)q.lt(SupportOwnedCouponEntity::getId,before);
   var rows=owned.selectListByQuery(q.orderBy(SupportOwnedCouponEntity::getId,false).limit(200));if(rows.isEmpty())break;
   for(var row:rows){
    var rule=parse(row.getRuleSnapshot());if(!all && !applicable(chat,row,rule))continue;
    if(keyword!=null && !text(rule,"name","").toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))continue;
    if("WORKSPACE".equals(sender)){try{shared(chat,sender,new CardRef(CardType.CUSTOMER_COUPON,row.getId()));}catch(CoolException denied){continue;}}
    if(matched>=offset && list.size()<size)list.add(build(chat,sender,new CardRef(CardType.CUSTOMER_COUPON,row.getId())));matched++;
   }before=rows.get(rows.size()-1).getId();
  }
  return new Options(list,matched,page,size);
 }
 public Options templates(StoreChatEntity chat,String keyword){
  validateKeyword(keyword);var req=new DiscoveryPageReq();req.setStoreId(chat.getStoreId());req.setKeyword(keyword);req.setPage(1);req.setSize(20);
  var page=discovery.coupons(req);var list=page.getList().stream().map(row->build(chat,"AI",new CardRef(CardType.COUPON_TEMPLATE,((Number)row.get("id")).longValue()))).toList();
  return new Options(list,page.getPagination().getTotal(),1,20);
 }
 public List<Card> orders(StoreChatEntity chat){
  return bills.selectListByQuery(QueryWrapper.create().eq(WalletBillEntity::getCustomerId,chat.getCustomerId()).eq(WalletBillEntity::getStoreId,chat.getStoreId())
   .eq(WalletBillEntity::getMerchantId,chat.getMerchantId()).orderBy(WalletBillEntity::getId,false).limit(10)).stream()
   .map(r->build(chat,"AI",new CardRef(CardType.ORDER,r.getId()))).toList();
 }
 private boolean applicable(StoreChatEntity chat,SupportOwnedCouponEntity row,JsonNode rule){
  if(!Objects.equals(row.getMerchantId(),chat.getMerchantId()))return false;
  if(rule.path("scopeType").asInt(-1)==0)return true;
  for(JsonNode id:rule.path("storeIds"))if(id.asLong()==chat.getStoreId())return true;return false;
 }
 private String couponState(SupportOwnedCouponEntity row){
  if(Integer.valueOf(1).equals(row.getStatus()))return "已核销";if(Integer.valueOf(2).equals(row.getStatus()))return "已作废";
  if(!Integer.valueOf(0).equals(row.getStatus()) || row.getValidTo()==null || row.getValidFrom()==null)return "状态待核对";
  Date now=new Date();return !row.getValidTo().after(now)?"已过期":row.getValidFrom().after(now)?"未生效":"待使用";
 }
 private CardData publicData(JsonNode row,String fallback,boolean coupon){
  var facts=new ArrayList<Fact>();for(String key:List.of("address","businessHours","statusText"))if(row.hasNonNull(key))facts.add(new Fact(key,row.get(key).asText()));
  if(coupon)facts.addAll(ruleFacts(row,true));return new CardData(text(row,"name",fallback),text(row,"cover",text(row,"logo",null)),text(row,"saleStateText",text(row,"statusText",fallback)),facts);
 }
 private List<Fact> ruleFacts(JsonNode rule,boolean amountInCents){
  var result=new ArrayList<Fact>();
  for(String[] f:new String[][]{{"description","使用说明"},{"type","券类型"},{"discountRate","折扣"},{"allowCombine","能否叠加"},{"validityType","有效期类型"},{"validDays","领取后有效天数"},{"validFrom","有效期开始"},{"validTo","有效期结束"},{"storeNames","适用门店"}})
   if(rule.hasNonNull(f[0]))result.add(new Fact(f[1],rule.get(f[0]).isValueNode()?rule.get(f[0]).asText():rule.get(f[0]).toString()));
  for(String[] f:new String[][]{{"salePrice","售价"},{"minSpend","消费门槛"},{"discountAmount","满减金额"},{"deductAmount","抵扣金额"},{"maxDiscount","最高优惠"}})
   if(rule.hasNonNull(f[0])){try{BigDecimal v=new BigDecimal(rule.get(f[0]).asText());result.add(new Fact(f[1],(amountInCents?v.movePointLeft(2):v).toPlainString()+"元"));}catch(NumberFormatException ignored){}}
  for(String key:List.of("contentItems","contents"))for(JsonNode item:rule.path(key))if(item.hasNonNull("name"))result.add(new Fact("包含内容",item.path("name").asText()+" × "+item.path("quantity").asText("1")));
  return List.copyOf(result);
 }
 private JsonNode parse(String raw){try{return raw==null?json.createObjectNode():json.readTree(raw);}catch(Exception e){return json.createObjectNode();}}
 private String text(JsonNode row,String key,String fallback){return row.hasNonNull(key)?row.get(key).asText():fallback;}
 private void add(List<Fact> facts,String label,Object value){if(value!=null)facts.add(new Fact(label,value.toString()));}
 private void cents(List<Fact> facts,String label,Long value){if(value!=null)facts.add(new Fact(label,BigDecimal.valueOf(value,2).toPlainString()+"元"));}
 private void validateKeyword(String keyword){if(keyword!=null && keyword.length()>100)throw new CoolException("搜索关键词最多100字",400);}
}
