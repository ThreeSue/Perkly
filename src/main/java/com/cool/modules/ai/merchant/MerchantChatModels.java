package com.cool.modules.ai.merchant;

import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 门店只读聊天的独立MerchantChatModels组件
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:29:18
 */

public final class MerchantChatModels {
 private MerchantChatModels() {}
 public enum Action { STATISTICS, COUPONS, ORDER, RECORDS, INSPECT, ASK, UNSUPPORTED }
 public record Conditions(String period,LocalDate startDate,LocalDate endDate,Long operatorId,Long couponId,Long orderId,String keyword) {
  public static Conditions empty(){return new Conditions("TODAY",null,null,null,null,null,null);}
 }
 public record Chat(String sessionId,String message,Long couponId,Long orderId,String code) {}
 public record Plan(Action action,Conditions conditions,String question) {}
 public record Reply(String sessionId,String message,Action action,Conditions conditions,JsonNode cards,List<String> suggestions,boolean degraded,String operation) {}
}
