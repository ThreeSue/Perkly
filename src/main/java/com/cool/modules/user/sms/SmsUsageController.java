package com.cool.modules.user.sms;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.user.mapper.SmsUsageEventMapper;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import static com.mybatisflex.core.query.QueryMethods.count;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营端短信使用量、事件明细与上限配置
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:29:00
 */
@RestController
@RequestMapping("/admin/sms/usage")
@RequiredArgsConstructor
public class SmsUsageController {
    private static final String VIEW = "sms:usage:view";
    private static final String MANAGE = "sms:usage:manage";
    private static final Set<String> KINDS = Set.of("SEND", "VERIFY", "POLICY");
    private final SupportAccess access;
    private final SmsUsageService usage;
    private final SmsUsageEventMapper events;
    private final BaseSysUserMapper workspaceUsers;
    private final UserInfoMapper appUsers;
    private final SmsCloudUsage cloud;
    private final com.cool.modules.user.mapper.SmsReceiptMapper receipts;
    private final SmsReceiptWorker receiptWorker;
    @Value("${sms-auth.daily-warning-threshold:0}")
    private long dailyWarning;
    @Value("${sms-auth.monthly-warning-threshold:0}")
    private long monthlyWarning;
    @Value("${sms-auth.interval:60}")
    private long sendInterval;

    @Getter @Setter
    public static class Group {
        private String dayKey;
        private String scene;
        private String kind;
        private String result;
        private long total;
    }

    public record Overview(String from, String today, List<Group> groups, long monthAttempts,
                           long dailyWarningThreshold, long monthlyWarningThreshold,
                           int dailyLimit, long intervalSeconds, String recordingSince) {}
    public record Detail(String id, LocalDateTime time, String phoneMask, String scene, String kind, String result,
                         String actorType, String actorName, String verifiedAccount, String providerCode,
                         String providerRequestId, Integer previousLimit, Integer nextLimit,
                         String deliveryResult, LocalDateTime reportedAt) {}
    public record Details(List<Detail> list, long total) {}
    public record LimitChange(Integer dailyLimit) {}

    public record DeliveryOverview(long delivered, long failed, long pending, String recordingSince,
                                   SmsReceiptWorker.Status sync) {}

    @GetMapping("/receipts")
    public R<DeliveryOverview> receipts(@RequestParam(defaultValue = "7") int days) {
        access.platform(VIEW);
        LocalDate today = LocalDate.now(SmsUsageService.ZONE);
        String start = from(today, days).toString();
        String end = today.toString();
        // Attribute delayed receipts to the original send cohort. Unrelated account messages are excluded.
        long delivered = receipts.selectCountByQuery(QueryWrapper.create().eq(SmsReceipt::getResult, "DELIVERED")
            .in(SmsReceipt::getBizId, sends(start, end).select(SmsUsageEvent::getBizId)));
        long failed = receipts.selectCountByQuery(QueryWrapper.create().eq(SmsReceipt::getResult, "DELIVERY_FAILED")
            .in(SmsReceipt::getBizId, sends(start, end).select(SmsUsageEvent::getBizId)));
        long pending = events.selectCountByQuery(sends(start, end)
            .notIn(SmsUsageEvent::getBizId, QueryWrapper.create().from(SmsReceipt.class).select(SmsReceipt::getBizId)));
        var first = events.selectOneByQuery(QueryWrapper.create().eq(SmsUsageEvent::getKind, "SEND")
            .orderBy(SmsUsageEvent::getId, true).limit(1));
        return R.ok(new DeliveryOverview(delivered, failed, pending, first == null ? null : first.getDayKey(), receiptWorker.status()));
    }

    private static QueryWrapper sends(String start, String end) {
        return days(start, end).from(SmsUsageEvent.class).eq(SmsUsageEvent::getKind, "SEND")
            .eq(SmsUsageEvent::getResult, "ACCEPTED").isNotNull(SmsUsageEvent::getBizId);
    }

    @GetMapping("/overview")
    public R<Overview> overview(@RequestParam(defaultValue = "7") int days) {
        access.platform(VIEW);
        LocalDate today = LocalDate.now(SmsUsageService.ZONE);
        String from = from(today, days).toString();
        String end = today.toString();
        var groups = days(from, end).in(SmsUsageEvent::getKind, List.of("SEND", "VERIFY"))
            .select(SmsUsageEvent::getDayKey, SmsUsageEvent::getScene, SmsUsageEvent::getKind, SmsUsageEvent::getResult)
            .select(count(SmsUsageEvent::getId).as("total"))
            .groupBy(SmsUsageEvent::getDayKey, SmsUsageEvent::getScene, SmsUsageEvent::getKind, SmsUsageEvent::getResult);
        long monthAttempts = events.selectCountByQuery(days(today.withDayOfMonth(1).toString(), end)
            .eq(SmsUsageEvent::getKind, "SEND").in(SmsUsageEvent::getResult, List.of("RESERVED", "ACCEPTED", "REJECTED", "UNKNOWN")));
        var first = events.selectOneByQuery(QueryWrapper.create().eq(SmsUsageEvent::getKind, "SEND")
            .orderBy(SmsUsageEvent::getId, true).limit(1));
        return R.ok(new Overview(from, end, events.selectListByQueryAs(groups, Group.class), monthAttempts,
            dailyWarning, monthlyWarning, usage.limits().dailyLimit(), Math.max(60, sendInterval),
            first == null ? null : first.getDayKey()));
    }

    @GetMapping("/events")
    public R<Details> events(@RequestParam(defaultValue = "7") int days,
        @RequestParam(required = false) SmsScene scene, @RequestParam(required = false) String kind,
        @RequestParam(required = false) SmsUsageService.Outcome result, @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size) {
        access.platform(VIEW);
        SupportAccess.page(page, size);
        if (kind != null && !KINDS.contains(kind)) throw new CoolException("事件类型无效", 400);
        LocalDate today = LocalDate.now(SmsUsageService.ZONE);
        var query = days(from(today, days).toString(), today.toString());
        if (scene != null) query.eq(SmsUsageEvent::getScene, scene.name());
        if (kind != null) query.eq(SmsUsageEvent::getKind, kind);
        if (result != null) query.eq(SmsUsageEvent::getResult, result.name());
        var rows = events.paginate(page, size, query.orderBy(SmsUsageEvent::getId, false));
        var records = rows.getRecords();
        var bizIds = records.stream().map(SmsUsageEvent::getBizId).filter(Objects::nonNull).distinct().toList();
        var deliveries = new java.util.HashMap<String, SmsReceipt>();
        if (!bizIds.isEmpty()) for (var receipt : receipts.selectListByQuery(QueryWrapper.create().in(SmsReceipt::getBizId, bizIds)))
            deliveries.put(receipt.getBizId(), receipt);
        var workspaceIds = records.stream().filter(row -> Set.of("ADMIN", "MERCHANT").contains(row.getActorType()))
            .map(SmsUsageEvent::getActorId).filter(Objects::nonNull).distinct().toList();
        var appIds = records.stream().flatMap(row -> java.util.stream.Stream.of(row.getVerifiedAccountId(),
            "APP".equals(row.getActorType()) ? row.getActorId() : null)).filter(Objects::nonNull).distinct().toList();
        var workspaceNames = new java.util.HashMap<Long, String>();
        var appNames = new java.util.HashMap<Long, String>();
        if (!workspaceIds.isEmpty()) for (var user : workspaceUsers.selectListByIds(workspaceIds))
            workspaceNames.put(user.getId(), label(user.getName(), "后台账号"));
        if (!appIds.isEmpty()) for (var user : appUsers.selectListByIds(appIds))
            appNames.put(user.getId(), label(user.getNickName(), "用户账号"));
        return R.ok(new Details(records.stream().map(row -> {
            var time = LocalDateTime.ofInstant(row.getCreateTime().toInstant(), SmsUsageService.ZONE);
            String actor = switch (row.getActorType()) {
                case "ADMIN" -> workspaceNames.getOrDefault(row.getActorId(), "后台账号");
                case "MERCHANT" -> workspaceNames.getOrDefault(row.getActorId(), "商家账号");
                case "APP" -> appNames.getOrDefault(row.getActorId(), "用户账号");
                case "GUEST" -> "未登录";
                default -> "已登录账号";
            };
            String verifiedAccount = row.getVerifiedAccountId() == null ? null :
                appNames.getOrDefault(row.getVerifiedAccountId(), "用户账号");
            var receipt = deliveries.get(row.getBizId());
            return new Detail(row.getId().toString(), time, row.getPhoneMask(), row.getScene(), row.getKind(),
                row.getResult(), row.getActorType(), actor, verifiedAccount, row.getProviderCode(),
                row.getProviderRequestId(), row.getPreviousLimit(), row.getNextLimit(),
                receipt == null ? ("ACCEPTED".equals(row.getResult()) ? "PENDING" : null) : receipt.getResult(),
                receipt == null ? null : receipt.getReportedAt());
        }).toList(), rows.getTotalRow()));
    }

    @GetMapping("/limits")
    public R<SmsUsageService.Limits> limits() {
        access.platform(VIEW);
        return R.ok(usage.limits());
    }

    @PostMapping("/limits")
    public R<SmsUsageService.Limits> save(@RequestBody LimitChange change) {
        access.platform(VIEW);
        access.platform(MANAGE);
        return R.ok(usage.saveLimit(change.dailyLimit()));
    }

    @GetMapping("/cloud/usage")
    public R<SmsCloudUsage.Usage> cloudUsage(@RequestParam(defaultValue = "7") int days) {
        access.platform(VIEW);
        LocalDate today = LocalDate.now(SmsUsageService.ZONE);
        return R.ok(cloud.usage(from(today, days), today));
    }

    @GetMapping("/cloud/billing")
    public R<SmsCloudUsage.Billing> cloudBilling(@RequestParam String month) {
        access.platform(VIEW);
        YearMonth period;
        try { period = YearMonth.parse(month); }
        catch (java.time.format.DateTimeParseException e) { throw new CoolException("账单月份格式为 YYYY-MM", 400); }
        if (period.isAfter(YearMonth.now(SmsUsageService.ZONE))) throw new CoolException("账单月份不能晚于本月", 400);
        return R.ok(cloud.billing(period));
    }

    private static String label(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback;
        return name.replaceAll("(?<![0-9])(1[3-9][0-9])[0-9]{4}([0-9]{4})(?![0-9])", "$1****$2");
    }

    private static QueryWrapper days(String from, String through) {
        return QueryWrapper.create().ge(SmsUsageEvent::getDayKey, from).le(SmsUsageEvent::getDayKey, through);
    }

    private static LocalDate from(LocalDate today, int days) {
        if (days != 1 && days != 7 && days != 30) throw new CoolException("可查询今天、最近 7 天或最近 30 天", 400);
        return today.minusDays(days - 1L);
    }
}
