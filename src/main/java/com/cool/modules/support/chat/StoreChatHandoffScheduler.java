package com.cool.modules.support.chat;
import com.cool.modules.support.mapper.StoreChatMapper;
import com.cool.modules.support.entity.StoreChatEntity;
import com.mybatisflex.core.query.QueryWrapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 分页巡检人工客服租约并通过事务服务交接
 * @Version: v1.0
 * @Date: 2026-11-13
 * @Time: 16:00:00
 */
@org.springframework.stereotype.Component @lombok.RequiredArgsConstructor
public class StoreChatHandoffScheduler {
 private final StoreChatMapper chats;private final StoreChatReception reception;
 @org.springframework.beans.factory.annotation.Value("${perkly.support.handoff-enabled:true}") private boolean enabled;
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=15000,initialDelay=30000)
 public void sweep(){
  if(!enabled)return;long cursor=0;
  while(true){var batch=chats.selectListByQuery(QueryWrapper.create().eq(StoreChatEntity::getState,"HUMAN").gt(StoreChatEntity::getId,cursor).orderBy(StoreChatEntity::getId,true).limit(100));
   if(batch.isEmpty())return;
   for(var c:batch){reception.expire(c.getId());cursor=c.getId();}
  }
 }
}
