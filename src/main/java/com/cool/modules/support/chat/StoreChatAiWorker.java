package com.cool.modules.support.chat;
import static com.cool.modules.support.chat.StoreChatModels.*;
import com.cool.core.exception.CoolException;import com.cool.modules.support.entity.*;import com.cool.modules.support.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;import org.springframework.transaction.PlatformTransactionManager;
import jakarta.annotation.PreDestroy;import java.util.*;import java.util.concurrent.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 后台生成客服AI回复并在提交时核对接待版本轮次和AI开关
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:33:00
 */
@Service
public class StoreChatAiWorker {
 @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.searchrisk.SearchRiskService risk;
 private final StoreChatAiJobMapper jobs;private final StoreChatMapper chats;private final StoreChatMessageMapper messages;
 private final StoreChatSettingsService settings;private final StoreChatKnowledge knowledge;private final StoreChatAiModel model;
 private final StoreChatReception reception;private final StoreChatCards cards;private final ObjectMapper json;private final TransactionTemplate tx;
 private final StoreChatAccess access;
 private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"store-chat-ai");t.setDaemon(true);return t;});
 @org.springframework.beans.factory.annotation.Value("${perkly.support.ai-dispatch-enabled:true}") private boolean dispatchEnabled;
 public StoreChatAiWorker(StoreChatAiJobMapper jobs,StoreChatMapper chats,StoreChatMessageMapper messages,StoreChatSettingsService settings,
  StoreChatKnowledge knowledge,StoreChatAiModel model,StoreChatReception reception,StoreChatCards cards,ObjectMapper json,PlatformTransactionManager manager,StoreChatAccess access){
  this.jobs=jobs;this.chats=chats;this.messages=messages;this.settings=settings;this.knowledge=knowledge;this.model=model;this.reception=reception;this.cards=cards;this.json=json;this.tx=new TransactionTemplate(manager);
  this.access=access;
 }
 @Scheduled(fixedDelay=2000,initialDelay=5000)
 public void dispatch(){
  if(!dispatchEnabled)return;
  var stale=jobs.selectListByQuery(QueryWrapper.create().eq(StoreChatAiJobEntity::getState,"RUNNING").lt(StoreChatAiJobEntity::getLeaseUntil,System.currentTimeMillis()).limit(10));
  for(var row:stale)finish(row.getId(),null,null);
  if(pool.getActiveCount()>=2)return;
  var rows=jobs.selectListByQuery(QueryWrapper.create().eq(StoreChatAiJobEntity::getState,"PENDING").orderBy(StoreChatAiJobEntity::getId,true).limit(2));
  for(var row:rows){try{pool.execute(()->process(row.getId()));}catch(RejectedExecutionException ignored){break;}}
 }
 public void process(Long id){
  var job=tx.execute(status->{var r=jobs.selectOneByQuery(QueryWrapper.create().eq(StoreChatAiJobEntity::getId,id).forUpdate());
   if(r==null || !"PENDING".equals(r.getState()))return null;r.setState("RUNNING");r.setLeaseUntil(System.currentTimeMillis()+120000);jobs.update(r);return r;});
  if(job==null)return;
  ModelReply reply=null;Integer settingVersion=null;Knowledge source=null;
  try{
   var chat=chats.selectOneById(job.getChatId());if(!matches(chat,job)){finish(id,null,null);return;}
   var setting=settings.read(chat.getStoreId(),false);settingVersion=setting.version();if(!setting.aiEnabled()){finish(id,null,settingVersion);return;}
   var question=messages.selectOneById(job.getMessageId());
   source=knowledge.collect(chat,question==null?"":question.getContent(),false);reply=model.reply(source,false);
  }catch(Exception unavailable){ /* A real fallback is persisted under the same version fence. */ }
  finish(id,reply,settingVersion,source);
 }
 public void finish(Long id,ModelReply reply,Integer settingVersion){
  finish(id,reply,settingVersion,null);
 }
 public void finish(Long id,ModelReply reply,Integer settingVersion,Knowledge source){
  tx.executeWithoutResult(status->{
   var job=jobs.selectOneByQuery(QueryWrapper.create().eq(StoreChatAiJobEntity::getId,id).forUpdate());
   if(job==null || !"RUNNING".equals(job.getState()))return;
   var chat=chats.selectOneByQuery(QueryWrapper.create().eq(StoreChatEntity::getId,job.getChatId()).forUpdate());
   if(!matches(chat,job)){job.setState("CANCELLED");jobs.update(job);return;}
   try{
    access.own("APP",chat.getCustomerId(),chat.getId(),"chat:view",false);
    cards.build(chat,"AI",new CardRef(CardType.STORE,chat.getStoreId()));
   }catch(CoolException unavailable){job.setState("CANCELLED");jobs.update(job);return;}
   Settings setting;
   try{setting=settings.read(chat.getStoreId(),true);}
   catch(CoolException invalidConfiguration){setting=new Settings(null,false,List.of());}
   boolean reliable=reply!=null && source!=null && setting.aiEnabled() && Objects.equals(setting.version(),settingVersion);
   if(reliable){
    var expected=new HashMap<CardRef,CardData>();if(source.store()!=null)expected.put(new CardRef(source.store().type(),source.store().id()),source.store().current());
    source.cards().forEach(c->expected.put(new CardRef(c.type(),c.id()),c.current()));
    for(var ref:reply.references()){
     try{if(!expected.containsKey(ref) || !Objects.equals(expected.get(ref),cards.build(chat,"AI",ref).current())){reliable=false;break;}}
     catch(CoolException changed){reliable=false;break;}
    }
   }
   var row=new StoreChatMessageEntity();row.setChatId(chat.getId());row.setSenderId(chat.getStoreId());row.setSenderKind(reliable?"AI":"SYSTEM");
   row.setSenderName(reliable?"门店 AI 助手":"接待提示");row.setRequestId("ai-job-"+job.getId());row.setRound(chat.getRound());row.setImages(List.of());
   row.setContent(reliable?reply.content():"暂时无法自动回复，可以留言等待门店工作人员；目前尚未有工作人员接待。");
   if(reliable){
    try{row.setReferenceJson(json.writeValueAsString(reply.references()));}catch(Exception e){throw new CoolException("引用无法保存",500);}
    if(!reply.references().isEmpty()){
     try{var card=cards.build(chat,"AI",reply.references().get(0));row.setCardSnapshot(cards.encode(card));row.setCardType(card.type().name());row.setCardRefId(card.id());}
     catch(CoolException changed){row.setContent("关联资料已变化，暂时无法可靠回答，请留言等待门店工作人员。");reliable=false;row.setSenderKind("SYSTEM");row.setSenderName("接待提示");row.setReferenceJson("[]");}
    }
   }
   try{risk.checkContent(row.getContent(),"AI_OUTPUT","CHAT",chat.getId());}
   catch(CoolException blocked){if(blocked.getCode()!=422)throw blocked;row.setContent("自动回复暂不可用，请等待门店工作人员处理。");row.setSenderKind("SYSTEM");row.setReferenceJson("[]");row.setCardSnapshot(null);reliable=false;}
   messages.insert(row);
   if(!reliable || reply.handoff())chat.setState("WAITING");
   chat.setVersion(chat.getVersion()+1);chat.setLatestQuestionId(null);reception.save(chat);job.setState("DONE");jobs.update(job);
  });
 }
 private boolean matches(StoreChatEntity c,StoreChatAiJobEntity j){return c!=null && "AI".equals(c.getState()) && Objects.equals(c.getVersion(),j.getChatVersion()) && Objects.equals(c.getRound(),j.getRound()) && Objects.equals(c.getLatestQuestionId(),j.getMessageId());}
 public Advice suggest(Long actor,Suggest p,StoreChatAccess access){
  if(p!=null)risk.checkContent(p.instruction(),"AI_INPUT","CHAT_SUGGEST",p.chatId());
  if(p==null || p.instruction()==null || p.instruction().isBlank() || p.instruction().length()>1000 || p.previous()!=null && p.previous().length()>2000)throw new CoolException("建议请求无效",400);
  var chat=access.own("WORKSPACE",actor,p.chatId(),"chat:reply",false);
  if(!"HUMAN".equals(chat.getState()) || !actor.equals(chat.getAssigneeId()))throw new CoolException("仅当前接待员工可生成建议",409);
  var source=knowledge.collect(chat,"员工要求："+p.instruction()+"\n上一候选草稿："+Objects.toString(p.previous(),""),true);
  var result=model.reply(source,true);
  var latest=access.own("WORKSPACE",actor,p.chatId(),"chat:reply",false);
  if(!"HUMAN".equals(latest.getState()) || !actor.equals(latest.getAssigneeId()) || !Objects.equals(chat.getVersion(),latest.getVersion()))throw new CoolException("会话已变化，请重新生成建议",409);
  var expected=new HashMap<CardRef,CardData>();expected.put(new CardRef(source.store().type(),source.store().id()),source.store().current());
  source.cards().forEach(c->expected.put(new CardRef(c.type(),c.id()),c.current()));
  for(var ref:result.references())if(!expected.containsKey(ref) || !Objects.equals(expected.get(ref),cards.build(latest,"AI",ref).current()))throw new CoolException("关联资料已变化，请重新生成建议",409);
  return new Advice(result.content(),result.references());
 }
 @PreDestroy public void shutdown(){pool.shutdownNow();}
}
