package com.cool.modules.ai.app;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义用户助手的只读意图会话条件及真实结果卡片
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
public final class UserAssistantModels {
    private UserAssistantModels() {}
    public enum Action { COUPONS, STORES, WALLET, ORDERS, RULES, CHAT, ASK, UNSUPPORTED }
    public enum Kind { COUPON, STORE, WALLET, ORDER }
    public enum PriceMode { ANY, FREE, PAID }
    public record Location(BigDecimal longitude, BigDecimal latitude) {}
    public record Target(Kind kind, Long id) {}
    public record Chat(String sessionId, String message, Location location, Target target) {}
    // 金额均为分；消费预算不会被解释成购券售价。
    public record Conditions(String area, String keyword, PriceMode priceMode, Long maxPriceCents,
                             Long spendBudgetCents, Boolean cheap, Boolean nearby) {
        public static Conditions empty() { return new Conditions(null,null,PriceMode.ANY,null,null,false,false); }
    }
    public record Plan(Action action, Boolean reset, Conditions conditions, List<String> clear,
                       String state, String question) {}
    public record Turn(String user, String assistant) {}
    public record Field(String label, String value) {}
    public record Card(Kind kind, Long id, String title, String merchantName, String cover,
                       String statusText, Long priceCents, boolean purchasable, String destination,
                       List<Field> facts) {}
    public record Result(List<Card> cards, boolean partial, String note) {}
    public record Reply(String sessionId, String message, List<Card> cards, List<String> suggestions,
                        Conditions conditions, boolean degraded, boolean partial) {}
}
