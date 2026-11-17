package com.cool.modules.support.chat;
import com.cool.modules.support.entity.StoreChatPresenceEntity;
import com.cool.modules.support.mapper.StoreChatPresenceMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.core.exception.CoolException;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.*;
import java.util.UUID;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存并校验门店客服多设备在线租约
 * @Version: v1.0
 * @Date: 2026-11-13
 */
@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class StoreChatPresenceService {
 private final StoreChatAccess access;
 private final StoreChatPresenceMapper rows;
 private final BaseSysUserMapper users;
 public record Input(String sessionId,Boolean available){}
 public record Presence(String sessionId,boolean available,int heartbeatIntervalSeconds,int offlineTimeoutSeconds,String expiresAt){}
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public Presence heartbeat(Long actor,Input input){
  if(input==null||input.available()==null||input.sessionId()==null)throw new CoolException("缺少在线会话参数",400);
  try{if(!UUID.fromString(input.sessionId()).toString().equalsIgnoreCase(input.sessionId()))throw new IllegalArgumentException();}
  catch(IllegalArgumentException e){throw new CoolException("sessionId必须为UUID",400);}
  Long store=access.staff(actor,"chat:reply");
  // Serialize per-account insert/update, including two first heartbeats for one device.
  users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId,actor).forUpdate());
  String session=input.sessionId().toLowerCase(java.util.Locale.ROOT);
  var row=rows.selectOneByQuery(QueryWrapper.create().eq(StoreChatPresenceEntity::getActorId,actor).eq(StoreChatPresenceEntity::getSessionId,session));
  long expires=System.currentTimeMillis()+(input.available()?90000:0);
  if(row==null && input.available()){
   row=new StoreChatPresenceEntity();row.setActorId(actor);row.setStoreId(store);row.setSessionId(session);row.setExpiresAt(expires);rows.insert(row);
  }else if(row!=null){row.setStoreId(store);row.setExpiresAt(expires);rows.update(row);}
  return new Presence(session,input.available(),30,90,input.available()?Instant.ofEpochMilli(expires).atOffset(ZoneOffset.ofHours(8)).toString():null);
 }
 public boolean online(Long actor,Long store){
  if(actor==null)return false;
  try{if(!store.equals(access.staff(actor,"chat:reply")))return false;}catch(CoolException denied){return false;}
  return rows.selectCountByQuery(QueryWrapper.create().eq(StoreChatPresenceEntity::getActorId,actor).eq(StoreChatPresenceEntity::getStoreId,store).gt(StoreChatPresenceEntity::getExpiresAt,System.currentTimeMillis()))>0;
 }
}
