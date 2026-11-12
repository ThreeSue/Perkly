package com.cool.modules.searchrisk;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.support.mapper.*;
import com.cool.modules.support.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.workspace.mapper.WorkspaceNoticeMapper;
import com.cool.modules.workspace.entity.WorkspaceNoticeEntity;
import com.cool.modules.searchrisk.mapper.SearchRiskMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class ContentRiskSources {
 public static final Set<String> SOURCES=Set.of("PROFILE","FEEDBACK","CHAT","MERCHANT","STORE","COUPON","NOTICE","RISKS");
 private final UserInfoMapper users;private final UserFeedbackMapper feedback;private final StoreChatMessageMapper chats;
 private final MerchantInfoMapper merchants;private final MerchantStoreMapper stores;private final CouponTemplateMapper coupons;
 private final WorkspaceNoticeMapper notices;private final SearchRiskMapper risks;
 public record Item(Long id,String scene,String businessType,Long businessId,String text,SearchRiskService.Subject subject){}
 private SearchRiskService.Subject who(String type,Long id){return new SearchRiskService.Subject(type,id);}
 private String joined(String... text){return String.join("\n",Arrays.stream(text).filter(Objects::nonNull).toList());}
 public List<Item> next(String source,long cursor,int count){
  if(!SOURCES.contains(source)||count<1||count>10)throw new IllegalArgumentException("Invalid scan scope");
  return switch(source){
   case "PROFILE" -> users.selectListByQuery(QueryWrapper.create().gt(UserInfoEntity::getId,cursor).orderBy(UserInfoEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),"PROFILE","USER_NICKNAME",u.getId(),u.getNickName(),who("APP",u.getId()))).toList();
   case "FEEDBACK" -> feedback.selectListByQuery(QueryWrapper.create().gt(UserFeedbackEntity::getId,cursor).orderBy(UserFeedbackEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),"FEEDBACK","FEEDBACK",u.getId(),u.getContent(),who("APP",u.getCustomerId()))).toList();
   case "CHAT" -> chats.selectListByQuery(QueryWrapper.create().gt(StoreChatMessageEntity::getId,cursor).orderBy(StoreChatMessageEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),Set.of("AI","SYSTEM").contains(u.getSenderKind())?"AI_OUTPUT":"CHAT","CHAT_MESSAGE",u.getId(),u.getContent(),who("APP".equals(u.getSenderKind())?"APP":"WORKSPACE".equals(u.getSenderKind())?"MERCHANT":"SYSTEM",Set.of("APP","WORKSPACE").contains(u.getSenderKind())?u.getSenderId():null))).toList();
   case "MERCHANT" -> merchants.selectListByQuery(QueryWrapper.create().gt(MerchantInfoEntity::getId,cursor).orderBy(MerchantInfoEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),"MERCHANT","MERCHANT",u.getId(),joined(u.getName(),u.getIntroduction(),u.getAddress()),who("UNKNOWN",null))).toList();
   case "STORE" -> stores.selectListByQuery(QueryWrapper.create().gt(MerchantStoreEntity::getId,cursor).orderBy(MerchantStoreEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),"STORE","STORE",u.getId(),joined(u.getName(),u.getBusinessHours(),u.getAddress()),who("UNKNOWN",null))).toList();
   case "COUPON" -> coupons.selectListByQuery(QueryWrapper.create().gt(CouponTemplateEntity::getId,cursor).orderBy(CouponTemplateEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),"COUPON","COUPON",u.getId(),joined(u.getName(),u.getDescription()),who("UNKNOWN",null))).toList();
   case "NOTICE" -> notices.selectListByQuery(QueryWrapper.create().gt(WorkspaceNoticeEntity::getId,cursor).orderBy(WorkspaceNoticeEntity::getId,true).limit(count)).stream().map(u->new Item(u.getId(),"NOTICE","NOTICE",u.getId(),joined(u.getTitle(),u.getContent()),who("UNKNOWN",null))).toList();
   default -> risks.selectListByQuery(QueryWrapper.create().gt(SearchRisk::getId,cursor).eq(SearchRisk::getStatus,"PENDING").isNull(SearchRisk::getJobId).orderBy(SearchRisk::getId,true).limit(count)).stream().map(u->new Item(u.getId(),Objects.toString(u.getScene(),"SEARCH"),Objects.toString(u.getBusinessType(),"LEGACY_RISK"),u.getBusinessId(),u.getContent(),who(Objects.toString(u.getSubjectType(),u.getUserId()==null?"GUEST":"APP"),u.getSubjectId()==null?u.getUserId():u.getSubjectId()))).toList();
  };
 }
}

