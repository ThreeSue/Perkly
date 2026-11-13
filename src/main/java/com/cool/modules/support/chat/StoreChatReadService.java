package com.cool.modules.support.chat;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.*;
import com.cool.modules.support.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 校验会话归属并提供单调已读游标及未读统计
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 11:20:05
 */
@Service @RequiredArgsConstructor
public class StoreChatReadService {
    private final StoreChatAccess access;
    private final SupportAccess support;
    private final StoreChatMapper chats;
    private final StoreChatMessageMapper messages;
    private final StoreChatReadMapper reads;
    public record ReadInput(Long chatId, Long lastReadMessageId) {}
    public record State(Long chatId, long lastReadMessageId, long unreadCount) {}
    public record Summary(long unreadCount, long unreadChatCount) {}

    public State state(String kind, Long actor, Long chatId) {
        access.own(kind,actor,chatId,"chat:view",false);
        return authorizedState(kind,actor,chatId);
    }
    // Caller must already have checked the chat's customer/store boundary.
    public State authorizedState(String kind, Long actor, Long chatId) {
        var read=reads.selectOneByQuery(reader(kind,actor,chatId));
        long cursor=read==null?0L:read.getLastReadMessageId();
        return new State(chatId,cursor,count(kind,chatId,cursor));
    }
    @Transactional(rollbackFor=Exception.class)
    public State markRead(String kind, Long actor, ReadInput input) {
        if(input==null || input.lastReadMessageId()==null || input.lastReadMessageId()<=0)
            throw new CoolException("请提供已展示的最后一条消息",400);
        // Serialize first insert and concurrent devices using the existing chat row.
        access.own(kind,actor,input.chatId(),"chat:view",true);
        var message=messages.selectOneById(input.lastReadMessageId());
        if(message==null || !Objects.equals(message.getChatId(),input.chatId()))
            throw new CoolException("消息不属于当前会话",400);
        var read=reads.selectOneByQuery(reader(kind,actor,input.chatId()));
        if(read==null) {
            read=new StoreChatReadEntity();read.setChatId(input.chatId());
            read.setReaderKind(kind);read.setReaderId(actor);
            read.setLastReadMessageId(message.getId());reads.insert(read);
        } else if(message.getId()>read.getLastReadMessageId()) {
            read.setLastReadMessageId(message.getId());reads.update(read);
        }
        return new State(input.chatId(),read.getLastReadMessageId(),count(kind,input.chatId(),read.getLastReadMessageId()));
    }
    public Summary unread(String kind, Long actor) {
        var scope=QueryWrapper.create();
        if("APP".equals(kind)) {support.customer(actor);scope.eq(StoreChatEntity::getCustomerId,actor);}
        else if("WORKSPACE".equals(kind)) scope.eq(StoreChatEntity::getStoreId,access.staff(actor,"chat:view"));
        else throw new CoolException("身份无效",403);
        long total=0, chatCount=0;
        for(var chat:chats.selectListByQuery(scope)) {
            long n=authorizedState(kind,actor,chat.getId()).unreadCount();
            total+=n;if(n>0)chatCount++;
        }
        return new Summary(total,chatCount);
    }
    private QueryWrapper reader(String kind, Long actor, Long chatId) {
        return QueryWrapper.create().eq(StoreChatReadEntity::getChatId,chatId)
            .eq(StoreChatReadEntity::getReaderKind,kind).eq(StoreChatReadEntity::getReaderId,actor);
    }
    private long count(String kind, Long chatId, long cursor) {
        var q=QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chatId)
            .gt(StoreChatMessageEntity::getId,cursor);
        // SYSTEM is a lifecycle event, not an unread reply. Staff only receive customer messages.
        if("APP".equals(kind)) q.in(StoreChatMessageEntity::getSenderKind,List.of("WORKSPACE","AI"));
        else q.eq(StoreChatMessageEntity::getSenderKind,"APP");
        return messages.selectCountByQuery(q);
    }
}
