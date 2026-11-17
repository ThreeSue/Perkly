package com.cool.modules.ai.usage;

import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.ai.entity.AiUsageEvent;
import com.cool.modules.ai.mapper.AiUsageMapper;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.user.mapper.UserInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.function.Supplier;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录真实模型调用及业务受理，并为后台任务保留发起身份
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 23:15:57
 */
@Service
@Slf4j
public class AiUsageMeter {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Actor SYSTEM = new Actor("SYSTEM", null);
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    private final AiUsageMapper events;
    private final BaseSysUserMapper staff;
    private final UserInfoMapper users;
    private final MerchantStoreMapper stores;
    private final TransactionTemplate transaction;

    public record Actor(String type, Long id) {}
    private record Scope(Actor actor, String feature) {}

    public AiUsageMeter(AiUsageMapper events, BaseSysUserMapper staff, UserInfoMapper users,
                        MerchantStoreMapper stores, PlatformTransactionManager manager) {
        this.events = events;
        this.staff = staff;
        this.users = users;
        this.stores = stores;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static <T> T as(Actor actor, Supplier<T> action) {
        return as(actor, null, action);
    }

    public static <T> T as(Actor actor, String feature, Supplier<T> action) {
        Scope previous = SCOPE.get();
        SCOPE.set(new Scope(actor, feature));
        try {
            return action.get();
        } finally {
            if (previous == null) SCOPE.remove();
            else SCOPE.set(previous);
        }
    }

    public static Actor currentActor() {
        Scope scope = SCOPE.get();
        if (scope != null) return scope.actor();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
            && attributes.getRequest().getAttribute("merchantUserId") instanceof Long merchantActor) {
            return new Actor("WORKSPACE", merchantActor);
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtUser user) {
            return switch (user.getUserTypeEnum()) {
                case APP -> new Actor("APP", user.getUserId());
                case ADMIN -> new Actor("PLATFORM", user.getUserId());
                case MERCHANT -> new Actor("WORKSPACE", user.getUserId());
                default -> SYSTEM;
            };
        }
        return SYSTEM;
    }

    public static ChatClient client(AiUsageMeter meter, ChatModel model, String feature) {
        if (meter == null) return ChatClient.create(model);
        var scope = SCOPE.get();
        String trackedFeature = scope != null && scope.feature() != null ? scope.feature() : feature;
        return ChatClient.builder(model).defaultAdvisors(meter.advisor(trackedFeature, model.getDefaultOptions().getModel())).build();
    }

    private CallAdvisor advisor(String feature, String model) {
        return new CallAdvisor() {
            @Override
            public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
                Actor actor = currentActor();
                Instant started = Instant.now();
                ChatResponse response = null;
                try {
                    var result = chain.nextCall(request);
                    response = result.chatResponse();
                    return result;
                } finally {
                    var metadata = response == null ? null : response.getMetadata();
                    var usage = metadata == null ? null : metadata.getUsage();
                    Integer input = usage == null ? null : usage.getPromptTokens();
                    Integer output = usage == null ? null : usage.getCompletionTokens();
                    // Some compatible providers map omitted usage to an empty, zero-valued object.
                    if (Integer.valueOf(0).equals(input) && Integer.valueOf(0).equals(output)) {
                        input = null;
                        output = null;
                    }
                    String actualModel = metadata != null && metadata.getModel() != null && !metadata.getModel().isBlank()
                        ? metadata.getModel() : model;
                    var event = event(feature, actor, started, actualModel, 0, 1, response == null ? 1 : 0, 0);
                    event.setInputTokens(input);
                    event.setOutputTokens(output);
                    event.setMissingUsage(input == null || output == null ? 1 : 0);
                    save(event);
                }
            }

            @Override
            public String getName() {
                return "AI usage meter";
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };
    }

    public void accepted(String feature, Actor actor, String model) {
        var entry = event(feature, actor, Instant.now(), model, 1, 0, 0, 0);
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    save(entry);
                }
            });
        } else {
            save(entry);
        }
    }

    public void imageCall(Actor actor, String model, Instant started, boolean generated) {
        save(event("image", actor, started, model, 0, 1, generated ? 0 : 1, generated ? 1 : 0));
    }

    private AiUsageEvent event(String feature, Actor actor, Instant time, String model, int requests,
                               int calls, int failures, int images) {
        var entry = new AiUsageEvent();
        entry.setOccurredAt(Date.from(time));
        entry.setDayKey(time.atZone(ZONE).toLocalDate().toString());
        entry.setFeature(feature);
        entry.setActorType(actor.type());
        entry.setActorId(actor.id());
        entry.setModelName(model);
        entry.setRequests(requests);
        entry.setCalls(calls);
        entry.setFailures(failures);
        entry.setMissingUsage(0);
        entry.setImages(images);
        return entry;
    }

    private void save(AiUsageEvent entry) {
        try {
            transaction.executeWithoutResult(status -> {
                identify(entry);
                events.insert(entry);
            });
        } catch (RuntimeException failure) {
            log.warn("AI usage could not be saved for {} ({})", entry.getFeature(), failure.getClass().getSimpleName());
        }
    }

    private void identify(AiUsageEvent entry) {
        Long id = entry.getActorId();
        if (id == null) {
            entry.setActorName("系统任务");
        } else if ("STORE".equals(entry.getActorType())) {
            var store = stores.selectOneById(id);
            entry.setActorName(store == null ? "历史门店" : store.getName());
        } else if ("APP".equals(entry.getActorType())) {
            var user = users.selectOneById(id);
            entry.setActorName(user == null ? "历史用户" : label(user.getNickName(), "未设置昵称的用户"));
        } else {
            var user = staff.selectOneById(id);
            entry.setActorName(user == null ? "历史账号" : label(user.getName(), label(user.getNickName(), user.getUsername())));
            if (user != null) entry.setAccountName(user.getUsername());
        }
    }

    private String label(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
