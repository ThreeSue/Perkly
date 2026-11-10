package com.cool.modules.ai.management;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义管理端助手受控工具与请求结果契约
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
public final class ManagementAssistantModels {
    private ManagementAssistantModels() {}
    public enum Scope { GLOBAL, COUPON, CATEGORY, STORE_TYPE, MERCHANT, STORE, ORDER, CONSUMPTION, REDEMPTION, STAFF, APPROVAL, ACTIVITY, NOTICE }
    public enum Action { QUERY, CREATE, UPDATE, DELETE, DRAFT, COUPON, ASK, CANCEL }
    public record Actor(Long id, boolean platform) {}
    public record Request(String sessionId, String requestId, Scope scope, String text, Long selectedId, String pageContext) {
        public Request(String sessionId,String requestId,Scope scope,String text,Long selectedId) {
            this(sessionId,requestId,scope,text,selectedId,null);
        }
    }
    public record Step(Scope module, Action action, Long id, String keyword, JsonNode fields) {}
    public record Plan(String question, boolean reset, List<Step> steps) {}
    public record Fact(String label, Object value) {}
    public record Result(Scope module, String state, String note, Object data) {}
    public record Preview(String token, List<Result> steps) {}
    public record Reply(String sessionId, String text, List<Result> results, Preview preview,
                        boolean degraded, String handoffText) {}
    public record Confirm(String sessionId, String token) {}
    public record NoticeDraft(String title, String content) {}
}
