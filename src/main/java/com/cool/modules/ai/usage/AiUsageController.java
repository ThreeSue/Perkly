package com.cool.modules.ai.usage;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.ai.entity.AiUsageEvent;
import com.cool.modules.ai.mapper.AiUsageMapper;
import com.cool.modules.support.service.SupportAccess;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.mybatisflex.core.query.QueryMethods.column;
import static com.mybatisflex.core.query.QueryMethods.sum;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为平台提供按时间、功能和使用者筛选的 AI 用量统计
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 23:20:05
 */
@RestController
@RequestMapping("/admin/ai/console/statistics")
@RequiredArgsConstructor
public class AiUsageController {
    private static final Set<String> FEATURES = Set.of("user", "merchant", "admin", "support", "supportDraft",
        "profile", "riskPreprocess", "riskScan", "image");
    private static final Set<String> ACTOR_TYPES = Set.of("APP", "WORKSPACE", "PLATFORM", "STORE", "SYSTEM");

    private final SupportAccess access;
    private final AiUsageMapper events;

    @Getter
    @Setter
    public static class Group {
        private String groupKey;
        private long requests;
        private long calls;
        private long failures;
        private long inputTokens;
        private long outputTokens;
        private long missingUsage;
        private long images;
    }

    public record Detail(String id, LocalDateTime time, String feature, String actorType, String actorId,
                         String actorName, String accountName, String model, String event, Integer inputTokens,
                         Integer outputTokens, Integer images) {}

    public record Stats(List<Group> trend, List<Group> features, List<Detail> list, long total) {}

    @GetMapping
    public R<Stats> statistics(@RequestParam(defaultValue = "7") int days,
                               @RequestParam(required = false) String feature,
                               @RequestParam(required = false) String actorKeyword,
                               @RequestParam(required = false) String actorType,
                               @RequestParam(required = false) Long actorId,
                               @RequestParam(defaultValue = "1") int pageNumber,
                               @RequestParam(defaultValue = "20") int pageSize) {
        access.platform("ai:manage");
        SupportAccess.page(pageNumber, pageSize);
        if (days != 7 && days != 30) throw new CoolException("统计范围可选最近 7 天或 30 天", 400);
        if (feature != null && !FEATURES.contains(feature)) throw new CoolException("未知 AI 功能", 400);
        if (actorType != null && !ACTOR_TYPES.contains(actorType)) throw new CoolException("未知使用者类型", 400);
        if (actorId != null && (actorType == null || actorId <= 0)) throw new CoolException("使用者筛选无效", 400);
        String keyword = actorKeyword == null ? "" : actorKeyword.strip();
        if (keyword.length() > 100) throw new CoolException("使用者关键词最多 100 字", 400);

        LocalDate today = LocalDate.now(AiUsageMeter.ZONE);
        var query = QueryWrapper.create().ge(AiUsageEvent::getDayKey, today.minusDays(days - 1).toString())
            .le(AiUsageEvent::getDayKey, today.toString());
        if (feature != null) query.eq(AiUsageEvent::getFeature, feature);
        if (actorType != null) query.eq(AiUsageEvent::getActorType, actorType);
        if (actorId != null) query.eq(AiUsageEvent::getActorId, actorId);
        if (!keyword.isEmpty()) {
            query.and(column(AiUsageEvent::getActorName).like(keyword)
                .or(column(AiUsageEvent::getAccountName).like(keyword)));
        }
        var daily = grouped(query.clone(), column(AiUsageEvent::getDayKey), true);
        var features = grouped(query.clone(), column(AiUsageEvent::getFeature), false);
        var page = events.paginate(pageNumber, pageSize, query.orderBy(AiUsageEvent::getId, false));
        return R.ok(new Stats(daily, features, page.getRecords().stream().map(this::detail).toList(), page.getTotalRow()));
    }

    private List<Group> grouped(QueryWrapper query, QueryColumn key, boolean chronological) {
        query.select(key.as("groupKey"), sum(AiUsageEvent::getRequests).as("requests"),
            sum(AiUsageEvent::getCalls).as("calls"), sum(AiUsageEvent::getFailures).as("failures"),
            sum(AiUsageEvent::getInputTokens).as("inputTokens"), sum(AiUsageEvent::getOutputTokens).as("outputTokens"),
            sum(AiUsageEvent::getMissingUsage).as("missingUsage"), sum(AiUsageEvent::getImages).as("images"))
            .groupBy(key);
        if (chronological) query.orderBy(key.asc());
        return events.selectListByQueryAs(query, Group.class);
    }

    private Detail detail(AiUsageEvent row) {
        String event = row.getRequests() > 0 ? "REQUEST" : row.getFailures() > 0 ? "FAILED" : "RETURNED";
        return new Detail(row.getId().toString(), LocalDateTime.ofInstant(row.getOccurredAt().toInstant(), AiUsageMeter.ZONE),
            row.getFeature(), row.getActorType(), row.getActorId() == null ? null : row.getActorId().toString(),
            row.getActorName(), row.getAccountName(), row.getModelName(), event, row.getInputTokens(),
            row.getOutputTokens(), row.getImages());
    }
}
