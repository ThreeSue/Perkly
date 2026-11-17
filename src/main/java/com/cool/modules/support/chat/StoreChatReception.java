package com.cool.modules.support.chat;
import static com.cool.modules.support.chat.StoreChatModels.*;
import com.cool.core.exception.CoolException;import com.cool.modules.support.entity.*;import com.cool.modules.support.mapper.*;
import com.cool.modules.support.service.SupportAccess;import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 原子处理客服转人工接管结束及可见接待状态
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:28:00
 */
@Service @RequiredArgsConstructor
public class StoreChatReception {
 private final StoreChatAccess access;private final StoreChatSettingsService settings;private final StoreChatMapper chats;
 private final StoreChatMessageMapper messages;private final BaseSysUserMapper users;private final MerchantStoreMapper stores;
 @org.springframework.beans.factory.annotation.Autowired private StoreChatPresenceService presence;
 @org.springframework.beans.factory.annotation.Autowired private StoreChatAiAvailability availability;
 @org.springframework.beans.factory.annotation.Autowired private StoreChatAiJobMapper jobs;
 public boolean aiAvailable(Long store){return availability.reason(store)==null;}
 public String receptionText(StoreChatEntity c){return switch(c.getState()){
  case "AI" -> Boolean.TRUE.equals(c.getHumanRequested())?"AI 先协助，正在等待人工":"AI 正在接待";
  case "HUMAN" -> "门店工作人员正在接待";
  case "CLOSED" -> "本轮接待已结束";
  default -> "正在等待人工，可以继续留言";
 };}
 public Detail detail(String kind,Long actor,Long id){return view(kind,actor,access.own(kind,actor,id,"chat:view",false));}
 public Detail view(String kind,Long actor,StoreChatEntity c){
  StoreChatAccess.normalize(c);var store=stores.selectOneById(c.getStoreId());var u=c.getAssigneeId()==null?null:users.selectOneById(c.getAssigneeId());
  boolean reply="WORKSPACE".equals(kind) && access.permitted(actor,"chat:reply");
  boolean mine="HUMAN".equals(c.getState()) && actor.equals(c.getAssigneeId());
  String reason=availability.reason(c.getStoreId());
  return new Detail(c.getId(),c.getStoreId(),store==null?"历史门店":store.getName(),c.getState(),c.getRound(),c.getVersion(),c.getAssigneeId(),u==null?null:u.getName(),
   settings.read(c.getStoreId(),false).aiEnabled(),reply && Set.of("AI","WAITING").contains(c.getState()),reply && !"CLOSED".equals(c.getState()) && (!"HUMAN".equals(c.getState()) || mine),reply && mine,reply && mine,c.getSummary(),Boolean.TRUE.equals(c.getHumanRequested()),c.getAssigneeId()==null?null:presence.online(c.getAssigneeId(),c.getStoreId()),reason==null,reason,receptionText(c),reply && mine && reason==null,"WORKSPACE".equals(kind)&&access.permitted(actor,"chat:manage"),aiReplyStatus(c));
 }
 public String aiReplyStatus(StoreChatEntity chat){
  if(!"AI".equals(chat.getState()) || chat.getLatestQuestionId()==null || jobs==null)return "IDLE";
  var job=jobs.selectOneByQuery(QueryWrapper.create().eq(StoreChatAiJobEntity::getChatId,chat.getId())
   .eq(StoreChatAiJobEntity::getMessageId,chat.getLatestQuestionId()).eq(StoreChatAiJobEntity::getChatVersion,chat.getVersion())
   .eq(StoreChatAiJobEntity::getRound,chat.getRound()));
  if(job==null)return "IDLE";
  if("RUNNING".equals(job.getState()))return job.getLeaseUntil()!=null && job.getLeaseUntil()<System.currentTimeMillis()?"TIMEOUT":"RUNNING";
  if("PENDING".equals(job.getState()))return job.getCreateTime()!=null && System.currentTimeMillis()-job.getCreateTime().getTime()>120000?"TIMEOUT":"PENDING";
  return "IDLE";
 }
 @Transactional(rollbackFor=Exception.class)
 public Detail transition(String kind,Long actor,String command,Transition p){
  if(p==null || p.version()==null)throw new CoolException("缺少会话版本",400);SupportAccess.request(p.requestId());
  if("HANDOFF".equals(command)?!"APP".equals(kind):!"WORKSPACE".equals(kind))throw new CoolException("此身份不可执行接待操作",403);
  var chat=access.own(kind,actor,p.chatId(),"chat:reply",true);
  String request=cn.hutool.crypto.SecureUtil.sha256(kind+":"+p.requestId());
  var previous=messages.selectOneByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getSenderKind,"SYSTEM")
   .eq(StoreChatMessageEntity::getSenderId,actor).eq(StoreChatMessageEntity::getRequestId,request));
  if(previous!=null){if(!chat.getId().equals(previous.getChatId()) || !command.equals(previous.getCommand()) || !p.version().equals(previous.getCommandVersion()))
   throw new CoolException("请求标识已用于其他接待操作",409);return view(kind,actor,chat);}
  if(!p.version().equals(chat.getVersion()))throw new CoolException("接待状态已变化，请刷新后重试",409);
  String text;
  switch(command){
   case "HANDOFF" -> {
    chat.setHumanRequested(true);
    if(!"HUMAN".equals(chat.getState())){
     if("CLOSED".equals(chat.getState()))chat.setRound(chat.getRound()+1);
     chat.setState(aiAvailable(chat.getStoreId())?"AI":"WAITING");chat.setAssigneeId(null);
    }
    text=receptionText(chat);
   }
   case "TAKEOVER" -> {
    if("HUMAN".equals(chat.getState()) && !actor.equals(chat.getAssigneeId()))throw new CoolException("会话已由其他工作人员接管",409);
    if("CLOSED".equals(chat.getState()))throw new CoolException("本轮已结束，等待顾客新消息",409);
    if(!presence.online(actor,chat.getStoreId()))presence.heartbeat(actor,new StoreChatPresenceService.Input(UUID.randomUUID().toString(),true));
    chat.setState("HUMAN");chat.setHumanRequested(false);chat.setAssigneeId(actor);chat.setSummary(summary(chat));text="门店工作人员已接管本轮会话。";
   }
   case "RETURN_TO_AI" -> {
    if(!"HUMAN".equals(chat.getState()) || !actor.equals(chat.getAssigneeId()))throw new CoolException("仅当前人工接待者可交回AI",409);
    String reason=availability.reason(chat.getStoreId());if(reason!=null)throw new CoolException("当前无法交回AI："+reason,409);
    chat.setState("AI");chat.setAssigneeId(null);chat.setHumanRequested(false);text="工作人员已将本轮会话交回AI接待。";
   }
   case "CLOSE" -> {
    if("HUMAN".equals(chat.getState()) && !actor.equals(chat.getAssigneeId()))throw new CoolException("仅当前接待员工可结束会话",409);
    chat.setState("CLOSED");chat.setAssigneeId(null);chat.setHumanRequested(false);text="本轮接待已结束。发送新问题后将按门店配置重新接待。";
   }
   default -> throw new CoolException("接待操作无效",400);
  }
  chat.setVersion(chat.getVersion()+1);chat.setLatestQuestionId(null);resume(chat);save(chat);
  var event=system(chat,actor,request,text);event.setCommand(command);event.setCommandVersion(p.version());messages.insert(event);
  return view(kind,actor,chat);
 }
 public void resume(StoreChatEntity chat){
  if(!"AI".equals(chat.getState()))return;
  var question=messages.selectOneByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chat.getId()).eq(StoreChatMessageEntity::getRound,chat.getRound()).eq(StoreChatMessageEntity::getSenderKind,"APP").orderBy(StoreChatMessageEntity::getId,false).limit(1));
  if(question==null)return;
  if(messages.selectCountByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chat.getId()).eq(StoreChatMessageEntity::getRound,chat.getRound()).in(StoreChatMessageEntity::getSenderKind,List.of("AI","WORKSPACE")).gt(StoreChatMessageEntity::getId,question.getId()))>0)return;
  var job=jobs.selectOneByQuery(QueryWrapper.create().eq(StoreChatAiJobEntity::getMessageId,question.getId()));
  if(job!=null && "RUNNING".equals(job.getState()))return;
  if(job==null){job=new StoreChatAiJobEntity();job.setChatId(chat.getId());job.setMessageId(question.getId());}
  job.setRound(chat.getRound());job.setChatVersion(chat.getVersion());job.setState("PENDING");job.setLeaseUntil(null);
  if(job.getId()==null)jobs.insert(job);else jobs.update(job,false);
  chat.setLatestQuestionId(question.getId());
 }
 @Transactional(rollbackFor=Exception.class)
 public void expire(Long id){
  var chat=chats.selectOneByQuery(QueryWrapper.create().eq(StoreChatEntity::getId,id).forUpdate());
  if(chat==null || !"HUMAN".equals(chat.getState()) || presence.online(chat.getAssigneeId(),chat.getStoreId()))return;
  chat.setState(settings.read(chat.getStoreId(),false).autoTakeoverEnabled()&&aiAvailable(chat.getStoreId())?"AI":"WAITING");
  chat.setAssigneeId(null);chat.setHumanRequested(true);chat.setVersion(chat.getVersion()+1);chat.setLatestQuestionId(null);resume(chat);save(chat);
  messages.insert(system(chat,0L,"offline-"+chat.getId()+"-"+chat.getVersion(),"人工接待暂时离线。"+receptionText(chat)));
 }
 public void save(StoreChatEntity chat){
  chats.update(chat);
  // Flex's ordinary update ignores nulls; clear stale assignment and task pointers explicitly.
  if(chat.getAssigneeId()==null)com.mybatisflex.core.update.UpdateChain.of(StoreChatEntity.class).set(StoreChatEntity::getAssigneeId,null).eq(StoreChatEntity::getId,chat.getId()).update();
  if(chat.getLatestQuestionId()==null)com.mybatisflex.core.update.UpdateChain.of(StoreChatEntity.class).set(StoreChatEntity::getLatestQuestionId,null).eq(StoreChatEntity::getId,chat.getId()).update();
 }
 public StoreChatMessageEntity system(StoreChatEntity chat,Long actor,String request,String text){
  var row=new StoreChatMessageEntity();row.setChatId(chat.getId());row.setSenderKind("SYSTEM");row.setSenderId(actor);row.setSenderName("接待提示");
  row.setRequestId(request);row.setContent(text);row.setImages(List.of());row.setRound(chat.getRound());return row;
 }
 private String summary(StoreChatEntity chat){
  var rows=messages.selectListByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chat.getId()).eq(StoreChatMessageEntity::getRound,chat.getRound())
   .eq(StoreChatMessageEntity::getSenderKind,"APP").orderBy(StoreChatMessageEntity::getId,false).limit(5));
  if(rows.isEmpty())return "请查看会话历史记录。";
  var copy=new ArrayList<>(rows);Collections.reverse(copy);
  return "顾客最近留言摘录（非处理结论）：\n"+copy.stream().map(r->{String text=Objects.toString(r.getContent(),"");
   return text.substring(0,Math.min(300,text.length()))+(r.getCardType()!=null?"［附卡片，请查看原消息］":"");}).collect(java.util.stream.Collectors.joining("\n"));
 }
}
