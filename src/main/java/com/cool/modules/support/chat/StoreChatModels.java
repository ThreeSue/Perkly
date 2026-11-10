package com.cool.modules.support.chat;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义客服接待状态结构化卡片和只读建议契约
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:16:00
 */
public final class StoreChatModels {
 private StoreChatModels(){}
 public enum State { AI, WAITING, HUMAN, CLOSED }
 public enum CardType { COUPON_TEMPLATE, CUSTOMER_COUPON, STORE, ORDER }
 public record CardRef(CardType type,Long id){}
 public record Fact(String label,String value){}
 public record CardData(String title,String image,String status,List<Fact> facts){}
 public record Card(CardType type,Long id,String title,String image,CardData snapshot,CardData current,boolean applicable,boolean available,String note){}
 public record Faq(String question,String answer){}
 public record SettingsInput(Integer version,Boolean aiEnabled,List<Faq> faqs){}
 public record Settings(Integer version,boolean aiEnabled,List<Faq> faqs){}
 public record Transition(Long chatId,String requestId,Long version){}
 public record Detail(Long id,Long storeId,String storeName,String state,Integer round,Long version,Long assigneeId,String assigneeName,
   boolean aiEnabled,boolean canTakeover,boolean canClose,boolean canReply,boolean canSuggest,String summary){}
 public record Message(Long id,Long chatId,String senderKind,Long senderId,String senderName,String requestId,String content,List<String> images,
   Date createTime,Date updateTime,Integer round,Card card,List<CardRef> references){}
 public record Options(List<Card> list,long total,int page,int size){}
 public record Suggest(Long chatId,String instruction,String previous){}
 public record Advice(String content,List<CardRef> references){}
 public record ModelReply(String content,List<CardRef> references,boolean handoff){}
 public record Transcript(String sender,String content,Card card){}
 public record Knowledge(Card store,List<Faq> faqs,List<Card> cards,List<Transcript> history,String question){}
}
