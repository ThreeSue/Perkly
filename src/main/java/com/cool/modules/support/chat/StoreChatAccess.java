package com.cool.modules.support.chat;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.StoreChatEntity;
import com.cool.modules.support.mapper.StoreChatMapper;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 独立核验客服查看回复配置权限和会话顾客门店边界
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:19:00
 */
@Service @RequiredArgsConstructor
public class StoreChatAccess {
 private final WorkspaceAuthorization auth;private final SupportAccess support;
 private final StoreChatMapper chats;private final MerchantInfoMapper merchants;
 public Long staff(Long actor,String permission){
  var user=auth.user(actor);auth.context(actor);
  if(!"STORE".equals(WorkspaceAuthorization.type(user)))throw new CoolException("请使用获授权的门店客服账号",403);
  auth.require(actor,permission);if("chat:reply".equals(permission))auth.require(actor,"chat:view");
  var store=auth.boundStore(user);var merchant=merchants.selectOneById(store.getMerchantId());
  if(store.getStatus()==null || !Set.of(1,2).contains(store.getStatus()) || merchant==null || !Integer.valueOf(1).equals(merchant.getStatus()))
   throw new CoolException("门店或商家已失效",403);
  return store.getId();
 }
 public StoreChatEntity own(String kind,Long actor,Long id,String permission,boolean lock){
  if(id==null)throw new CoolException("缺少会话",400);
  Long store=null;
  if("APP".equals(kind))support.customer(actor);
  else if("WORKSPACE".equals(kind))store=staff(actor,permission);
  else throw new CoolException("身份无效",403);
  var q=QueryWrapper.create().eq(StoreChatEntity::getId,id);if(lock)q.forUpdate();var chat=chats.selectOneByQuery(q);
  if(chat==null)throw new CoolException("会话不存在",404);
  if("APP".equals(kind)?!actor.equals(chat.getCustomerId()):!store.equals(chat.getStoreId()) || !auth.user(actor).getMerchantId().equals(chat.getMerchantId()))
   throw new CoolException("无权访问此会话",403);
  normalize(chat);return chat;
 }
 public boolean permitted(Long actor,String permission){try{staff(actor,permission);return true;}catch(CoolException denied){return false;}}
 public static void normalize(StoreChatEntity c){if(c.getState()==null)c.setState("WAITING");if(c.getVersion()==null)c.setVersion(0L);if(c.getRound()==null)c.setRound(1);}
}
