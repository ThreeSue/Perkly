package com.cool.modules.support.chat;
import static com.cool.modules.support.chat.StoreChatModels.*;
import com.cool.modules.support.entity.*;import com.cool.modules.support.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为本轮接待组装经过门店和顾客过滤的只读模型资料
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:33:00
 */
@Service @RequiredArgsConstructor
public class StoreChatKnowledge {
 private final StoreChatMessageMapper messages;private final StoreChatCards cards;private final StoreChatSettingsService settings;
 public Knowledge collect(StoreChatEntity chat,String question,boolean staff){
  var rows=new ArrayList<>(messages.selectListByQuery(QueryWrapper.create().eq(StoreChatMessageEntity::getChatId,chat.getId())
   .orderBy(StoreChatMessageEntity::getId,false).limit(16)));Collections.reverse(rows);
  List<Transcript> history=new ArrayList<>();Map<CardRef,Card> known=new LinkedHashMap<>();
  for(var row:rows){var card=cards.refresh(chat,cards.decode(row.getCardSnapshot()));
   history.add(new Transcript(row.getSenderKind(),Objects.toString(row.getContent(),"")+(row.getImages()!=null && !row.getImages().isEmpty()?"［有图片，AI未读取图片内容］":""),card));
   if(card!=null)known.put(new CardRef(card.type(),card.id()),card);
  }
  for(var c:cards.templates(chat,null).list().stream().limit(8).toList())known.putIfAbsent(new CardRef(c.type(),c.id()),c);
  if(!staff){
   for(var c:cards.coupons(chat,"AI",null,false,1,8).list())known.putIfAbsent(new CardRef(c.type(),c.id()),c);
   for(var c:cards.orders(chat))known.putIfAbsent(new CardRef(c.type(),c.id()),c);
  }
  var store=cards.build(chat,"AI",new CardRef(CardType.STORE,chat.getStoreId()));
  return new Knowledge(store,settings.read(chat.getStoreId(),false).faqs(),List.copyOf(known.values()),history,question);
 }
}
