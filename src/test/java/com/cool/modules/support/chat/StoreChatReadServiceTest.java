package com.cool.modules.support.chat;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.*;
import com.cool.modules.support.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证聊天已读游标拒绝越权跨会话及旧请求回退
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 11:20:05
 */
class StoreChatReadServiceTest {
 final StoreChatAccess access=mock(StoreChatAccess.class);
 final SupportAccess support=mock(SupportAccess.class);
 final StoreChatMapper chats=mock(StoreChatMapper.class);
 final StoreChatMessageMapper messages=mock(StoreChatMessageMapper.class);
 final StoreChatReadMapper reads=mock(StoreChatReadMapper.class);
 final StoreChatReadService service=new StoreChatReadService(access,support,chats,messages,reads);
 StoreChatMessageEntity message(long id,long chat){var m=new StoreChatMessageEntity();m.setId(id);m.setChatId(chat);return m;}
 @Test void unauthorizedCannotReadOrWriteCursor(){
  when(access.own("APP",1L,9L,"chat:view",true)).thenThrow(new CoolException("denied",403));
  assertThrows(CoolException.class,()->service.markRead("APP",1L,new StoreChatReadService.ReadInput(9L,30L)));
  verifyNoInteractions(messages,reads);
 }
 @Test void rejectsForeignMessageAndMissingCursor(){
  when(messages.selectOneById(30L)).thenReturn(message(30,8));
  assertThrows(CoolException.class,()->service.markRead("APP",1L,new StoreChatReadService.ReadInput(9L,30L)));
  assertThrows(CoolException.class,()->service.markRead("APP",1L,new StoreChatReadService.ReadInput(9L,null)));
  verifyNoInteractions(reads);
 }
 @Test void olderDeviceCannotMoveReadCursorBackwards(){
  when(messages.selectOneById(30L)).thenReturn(message(30,9));
  var r=new StoreChatReadEntity();r.setLastReadMessageId(50L);
  when(reads.selectOneByQuery(any(QueryWrapper.class))).thenReturn(r);
  assertEquals(50,service.markRead("APP",1L,new StoreChatReadService.ReadInput(9L,30L)).lastReadMessageId());
  verify(reads,never()).update(any(StoreChatReadEntity.class));
 }
 @Test void firstReadStoresIdentityAndDoesNotReadFutureMessages(){
  when(messages.selectOneById(30L)).thenReturn(message(30,9));
  when(messages.selectCountByQuery(any())).thenReturn(2L);
  var result=service.markRead("APP",1L,new StoreChatReadService.ReadInput(9L,30L));
  assertEquals(30,result.lastReadMessageId());assertEquals(2,result.unreadCount());
  var row=ArgumentCaptor.forClass(StoreChatReadEntity.class);verify(reads).insert(row.capture());
  assertEquals("APP",row.getValue().getReaderKind());assertEquals(1L,row.getValue().getReaderId());
  verify(access).own("APP",1L,9L,"chat:view",true);
 }
 @Test void repeatReadIsIdempotent(){
  when(messages.selectOneById(30L)).thenReturn(message(30,9));
  var r=new StoreChatReadEntity();r.setLastReadMessageId(30L);
  when(reads.selectOneByQuery(any(QueryWrapper.class))).thenReturn(r);
  service.markRead("WORKSPACE",2L,new StoreChatReadService.ReadInput(9L,30L));
  verify(reads,never()).update(any(StoreChatReadEntity.class));
 }
 @Test void stateChecksOwnershipBeforeReading(){
  when(access.own("APP",1L,9L,"chat:view",false)).thenThrow(new CoolException("denied",403));
  assertThrows(CoolException.class,()->service.state("APP",1L,9L));verifyNoInteractions(reads,messages);
 }
 @Test void unknownIdentityCannotEnumerateUnread(){
  assertThrows(CoolException.class,()->service.unread("OTHER",1L));verifyNoInteractions(chats,reads,messages);
 }
}
