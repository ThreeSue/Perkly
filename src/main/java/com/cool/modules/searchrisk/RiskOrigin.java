package com.cool.modules.searchrisk;
import java.util.Map;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以明确业务字段映射生成风险来源及版本幂等键
 * @Version: v1.0
 * @Date: 2026-11-14
 * @Time: 22:51:00
 */
public final class RiskOrigin {
 private RiskOrigin(){}
 private static final Map<String,String> FIELDS=Map.ofEntries(
  Map.entry("MERCHANT_NAME","MERCHANT.name"),Map.entry("MERCHANT_INTRODUCTION","MERCHANT.introduction"),Map.entry("MERCHANT_ADDRESS","MERCHANT.address"),
  Map.entry("STORE_NAME","STORE.name"),Map.entry("STORE_HOURS","STORE.businessHours"),Map.entry("STORE_ADDRESS","STORE.address"),
  Map.entry("COUPON_NAME","COUPON.name"),Map.entry("COUPON_DESCRIPTION","COUPON.description"),
  Map.entry("COUPON_ITEM_NAME","COUPON.itemName"),Map.entry("COUPON_ITEM_DESCRIPTION","COUPON.itemDescription"),
  Map.entry("MERCHANT_APPLICATION_NAME","MERCHANT_APPLICATION.name"),
  Map.entry("MERCHANT_APPLICATION_INTRODUCTION","MERCHANT_APPLICATION.introduction"),
  Map.entry("PLATFORM_WARNING_TITLE","PLATFORM_WARNING.title"),Map.entry("PLATFORM_WARNING_CONTENT","PLATFORM_WARNING.content"),
  Map.entry("USER_NICKNAME","PROFILE.nickName"),Map.entry("WORKSPACE_NICKNAME","PROFILE.workspaceNickName"),
  Map.entry("FEEDBACK","FEEDBACK.content"),Map.entry("FEEDBACK_REPLY","FEEDBACK.reply"),
  Map.entry("CHAT_MESSAGE","CHAT.content"),Map.entry("NOTICE_TITLE","NOTICE.title"),Map.entry("NOTICE_CONTENT","NOTICE.content"));
 public static void apply(SearchRisk row,String business,Long id,SearchRiskService.Subject actor){
  String base=baseField(business);
  String mapped=base==null?null:FIELDS.get(base);
  row.setContentVersion(version(row.getContent()));
  row.setOriginId(mapped==null?null:id);
  if(mapped!=null){
   String[] parts=mapped.split("\\.");
   row.setOriginType(parts[0]);
   row.setOriginField(parts[1]+business.substring(base.length()));
   boolean bound=id!=null && (!base.startsWith("COUPON_ITEM_") || !business.equals(base));
   row.setProvenanceStatus(bound?"VERIFIED":"UNVERIFIED");
  }
  else{
   boolean generated="AI_OUTPUT".equals(row.getScene())&&actor!=null&&"SYSTEM".equals(actor.type())&&row.getJobId()==null;
   boolean input="SEARCH".equals(row.getScene())||"AI_INPUT".equals(row.getScene());
   row.setOriginType(generated?"AI_GENERATED":input?row.getScene():"UNKNOWN");
   if("SEARCH".equals(row.getScene()))row.setOriginField("keyword");
   if("AI_INPUT".equals(row.getScene())&&business!=null&&business.indexOf(':')>=0)
    row.setOriginField(business.substring(business.indexOf(':')+1));
   row.setProvenanceStatus(generated||input?"VERIFIED":"UNVERIFIED");
  }
  if(actor!=null && ("SYSTEM".equals(actor.type()) || "GUEST".equals(actor.type())
      || actor.id()!=null && java.util.Set.of("APP","ADMIN","MERCHANT","WORKSPACE","STORE").contains(actor.type()))){
   row.setSubmittedByType(actor.type());row.setSubmittedById(actor.id());
  }
 }
 static String baseField(String field){
  if(field==null)return null;
  int suffix=field.indexOf(':');
  return suffix<0?field:field.substring(0,suffix);
 }
 static String version(String content){
  return content==null?null:cn.hutool.crypto.digest.DigestUtil.sha256Hex(SearchRiskService.normalize(content.strip()));
 }
 public static String key(SearchRisk row){
  if(!"VERIFIED".equals(row.getProvenanceStatus()) || row.getOriginId()==null || row.getOriginField()==null || row.getContentVersion()==null)return null;
  return cn.hutool.crypto.digest.DigestUtil.sha256Hex("origin-v1:"+row.getOriginType()+":"+row.getOriginId()+":"+row.getOriginField()+":"+row.getContentVersion());
 }
}
