package com.cool.modules.support.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.*;
import com.cool.modules.support.mapper.*;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.support.chat.*;
import static com.cool.modules.support.chat.StoreChatModels.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 隔离顾客与门店共享会话并持久化员工回复和图片消息
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:43:33
 */
@Service @RequiredArgsConstructor
public class StoreChatService {
    private final StoreChatMapper chats;
    private final StoreChatMessageMapper messages;
    private final MerchantStoreMapper stores;
    private final MerchantInfoMapper merchants;
    private final UserInfoMapper customers;
    private final BaseSysUserMapper users;
    private final WorkspaceAuthorization auth;
    private final SupportAccess access;
    private final SupportImageService images;
    private final StoreChatAccess chatAccess;
    private final StoreChatSettingsService settings;
    private final StoreChatCards cards;
    private final StoreChatReception reception;
    private final StoreChatAiJobMapper jobs;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public record Send(Long chatId,String requestId,String content,List<String> images,CardRef card){
        public Send(Long chatId,String requestId,String content,List<String> images){this(chatId,requestId,content,images,null);}
    }
    public record ChatView(Long id,String customerName,String merchantName,String storeName,Date createTime,String state,Integer round,Long version,Long assigneeId){}
    private Long staffStore(Long id){
        return chatAccess.staff(id,"chat:view");
    }
    public Long requireStaff(Long id){return chatAccess.staff(id,"chat:reply");}
    private StoreChatEntity own(String kind,Long actor,Long id){
        if(!Set.of("APP","WORKSPACE").contains(kind))throw new CoolException("身份无效",403);
        var row=chats.selectOneById(id);
        if(row==null)throw new CoolException("会话不存在",404);
        if("APP".equals(kind)){access.customer(actor);if(!actor.equals(row.getCustomerId()))throw new CoolException("无权访问此会话",403);}
        else if(!staffStore(actor).equals(row.getStoreId()) || !Objects.equals(auth.user(actor).getMerchantId(),row.getMerchantId()))throw new CoolException("无权访问其他商家或门店会话",403);
        return row;
    }
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long open(Long customer,Long storeId){
        access.customer(customer);customers.selectOneByQuery(QueryWrapper.create().eq(UserInfoEntity::getId,customer).forUpdate());
        var s=stores.selectOneById(storeId);
        if(s==null || !Integer.valueOf(1).equals(s.getStatus()))throw new CoolException("门店不在营业",403);
        var m=merchants.selectOneById(s.getMerchantId());
        if(m==null || !Integer.valueOf(1).equals(m.getStatus()))throw new CoolException("商家已停用",403);
        var row=chats.selectOneByQuery(QueryWrapper.create().eq(StoreChatEntity::getCustomerId,customer).eq(StoreChatEntity::getStoreId,storeId));
        if(row!=null)return row.getId();
        row=new StoreChatEntity();row.setCustomerId(customer);row.setStoreId(storeId);row.setMerchantId(s.getMerchantId());
        row.setState(settings.read(storeId,false).aiEnabled()?"AI":"WAITING");row.setRound(1);row.setVersion(0L);chats.insert(row);return row.getId();
    }
    public UserFeedbackService.PageResult<ChatView> page(String kind,Long actor,int page,int size){
        SupportAccess.page(page,size);var q=QueryWrapper.create();
        if("APP".equals(kind)){access.customer(actor);q.eq(StoreChatEntity::getCustomerId,actor);}
        else q.eq(StoreChatEntity::getStoreId,staffStore(actor));
        var result=chats.paginate(page,size,q.orderBy(StoreChatEntity::getUpdateTime,false).orderBy(StoreChatEntity::getId,false));
        var list=result.getRecords().stream().map(c->{
            var customer=customers.selectOneById(c.getCustomerId());var store=stores.selectOneById(c.getStoreId());var merchant=merchants.selectOneById(c.getMerchantId());
            StoreChatAccess.normalize(c);
            return new ChatView(c.getId(),customer==null?"历史用户":Objects.toString(customer.getNickName(),"顾客"),merchant==null?"历史商家":merchant.getName(),store==null?"历史门店":store.getName(),c.getCreateTime(),c.getState(),c.getRound(),c.getVersion(),c.getAssigneeId());
        }).toList();
        return new UserFeedbackService.PageResult<>(list,result.getTotalRow(),page,size);
    }
    public List<StoreChatMessageEntity> messages(String kind,Long actor,Long chatId,Long afterId,Long beforeId,int size){
        own(kind,actor,chatId);SupportAccess.page(1,size);
        if(afterId!=null && beforeId!=null)throw new CoolException("不能同时指定前后游标");
        if((afterId!=null && afterId<0)||(beforeId!=null && beforeId<=0))throw new CoolException("游标无效");
        var q=QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chatId);
        if(afterId!=null)q.gt(StoreChatMessageEntity::getId,afterId);
        if(beforeId!=null)q.lt(StoreChatMessageEntity::getId,beforeId);
        var rows=new ArrayList<>(messages.selectListByQuery(q.orderBy(StoreChatMessageEntity::getId,afterId!=null).limit(size)));
        if(afterId==null)Collections.reverse(rows);return rows;
    }
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long send(String kind,Long actor,Send p){
        if(p==null || p.chatId()==null)throw new CoolException("缺少会话");
        var chat=chatAccess.own(kind,actor,p.chatId(),"chat:reply",true);SupportAccess.request(p.requestId());
        var store=stores.selectOneById(chat.getStoreId());
        var merchant=merchants.selectOneById(chat.getMerchantId());
        if(store==null || !Set.of(1,2).contains(store.getStatus()) || !Objects.equals(store.getMerchantId(),chat.getMerchantId())
            || merchant==null || !Integer.valueOf(1).equals(merchant.getStatus()))throw new CoolException("门店已失效，不能继续发送",403);
        if(p.content()!=null && p.content().length()>2000)throw new CoolException("消息最多2000字");
        var urls=images.validate(kind,actor,p.images());String content=Objects.toString(p.content(),"").trim();
        if(content.isEmpty() && urls.isEmpty() && p.card()==null)throw new CoolException("消息内容不能为空");
        String name;
        if("APP".equals(kind)){
            var c=customers.selectOneByQuery(QueryWrapper.create().eq(UserInfoEntity::getId,actor).forUpdate());
            name=Objects.toString(c.getNickName(),"顾客");
        }else{
            var u=users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId,actor).forUpdate());
            name=Objects.toString(u.getName(),u.getUsername());
        }
        var previous=messages.selectOneByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getSenderKind,kind).eq(StoreChatMessageEntity::getSenderId,actor).eq(StoreChatMessageEntity::getRequestId,p.requestId()));
        if(previous!=null){
            if(!previous.getChatId().equals(p.chatId()) || !previous.getContent().equals(content) || !Objects.equals(previous.getImages(),urls)
                || !Objects.equals(previous.getCardType(),p.card()==null?null:p.card().type()==null?null:p.card().type().name())
                || !Objects.equals(previous.getCardRefId(),p.card()==null?null:p.card().id()))throw new CoolException("请求标识已用于其他消息",409);
            return previous.getId();
        }
        if("WORKSPACE".equals(kind) && (!"HUMAN".equals(chat.getState()) || !actor.equals(chat.getAssigneeId())))
            throw new CoolException("请先接管会话；仅当前接待员工可以回复",409);
        if("APP".equals(kind)){
            if("CLOSED".equals(chat.getState())){chat.setRound(chat.getRound()+1);chat.setAssigneeId(null);chat.setState(settings.read(chat.getStoreId(),false).aiEnabled()?"AI":"WAITING");}
            if("AI".equals(chat.getState()) && !settings.read(chat.getStoreId(),false).aiEnabled())chat.setState("WAITING");
        }
        var card=cards.build(chat,kind,p.card());
        var row=new StoreChatMessageEntity();row.setChatId(chat.getId());row.setSenderKind(kind);row.setSenderId(actor);row.setSenderName(name);row.setRequestId(p.requestId());row.setContent(content);row.setImages(urls);
        row.setRound(chat.getRound());row.setCardSnapshot(cards.encode(card));row.setCardType(card==null?null:card.type().name());row.setCardRefId(card==null?null:card.id());messages.insert(row);
        chat.setVersion(chat.getVersion()+1);
        if("APP".equals(kind) && "AI".equals(chat.getState())){
            chat.setLatestQuestionId(row.getId());var job=new StoreChatAiJobEntity();job.setChatId(chat.getId());job.setMessageId(row.getId());job.setChatVersion(chat.getVersion());job.setRound(chat.getRound());job.setState("PENDING");jobs.insert(job);
        }
        reception.save(chat);return row.getId();
    }
    public List<Message> messageViews(String kind,Long actor,Long chatId,Long afterId,Long beforeId,int size){
        var chat=chatAccess.own(kind,actor,chatId,"chat:view",false);
        return messages(kind,actor,chatId,afterId,beforeId,size).stream().map(r->new Message(r.getId(),r.getChatId(),r.getSenderKind(),r.getSenderId(),r.getSenderName(),r.getRequestId(),r.getContent(),r.getImages(),r.getCreateTime(),r.getUpdateTime(),r.getRound()==null?1:r.getRound(),cards.refresh(chat,cards.decode(r.getCardSnapshot())),references(r.getReferenceJson()))).toList();
    }
    private List<CardRef> references(String raw){try{return raw==null?List.of():json.readValue(raw,json.getTypeFactory().constructCollectionType(List.class,CardRef.class));}catch(Exception e){return List.of();}}
}
