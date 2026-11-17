package com.cool.modules.user.sms;

import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.user.mapper.SmsUsageEventMapper;
import com.cool.modules.user.mapper.SmsUsageGateMapper;
import com.cool.modules.user.mapper.SmsUsagePolicyMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 原子预占短信发送额度并写入脱敏统计流水
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
@Service
@RequiredArgsConstructor
public class SmsUsageService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final int DEFAULT_DAILY_LIMIT = 5;
    public enum Outcome { RESERVED, ACCEPTED, REJECTED, UNKNOWN, DAILY_LIMIT, COOLDOWN, VERIFIED, VERIFIED_REPEAT, INCORRECT, VERIFY_ERROR, UPDATED }

    private final SmsUsageEventMapper events;
    private final SmsUsageGateMapper gates;
    private final SmsUsagePolicyMapper policies;
    private final JwtTokenUtil jwt;
    @Value("${sms-auth.usage-hmac-key:}")
    private String usageKey;

    public record Admission(Long id, Outcome outcome, long retryAfterSeconds) {}
    public record Limits(int dailyLimit) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Admission reserve(String phone, SmsScene scene, long intervalSeconds, Long previousCooldown) {
        String phoneKey = fingerprint(phone);
        gates.ensure(phoneKey, day(Instant.now()));
        var gate = gates.selectOneByQuery(QueryWrapper.create().eq(SmsUsageGate::getPhoneKey,phoneKey).forUpdate());
        Instant now = Instant.now();
        String dayKey = day(now);
        int used = dayKey.equals(gate.getDayKey()) ? gate.getAttempts() : 0;
        if (used >= limits().dailyLimit()) {
            long reset = now.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
            return denied(phone,scene,dayKey,Outcome.DAILY_LIMIT,reset,now);
        }
        long next = Math.max(gate.getNextSendAt(), previousCooldown == null ? 0 : previousCooldown);
        if (next > now.toEpochMilli()) return denied(phone,scene,dayKey,Outcome.COOLDOWN,next,now);
        gate.setDayKey(dayKey);
        gate.setAttempts(used + 1);
        gate.setNextSendAt(now.toEpochMilli() + intervalSeconds * 1000);
        gates.update(gate);
        var event = event(dayKey,"SEND",scene,phone,Outcome.RESERVED);
        events.insert(event);
        return new Admission(event.getId(),Outcome.RESERVED,0);
    }

    private Admission denied(String phone, SmsScene scene, String dayKey, Outcome outcome, long until, Instant now) {
        var event = event(dayKey,"SEND",scene,phone,outcome);
        events.insert(event);
        return new Admission(event.getId(),outcome,Math.max(1,(until - now.toEpochMilli() + 999) / 1000));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long id, Outcome result, String bizId, String requestId, String providerCode) {
        if (id == null) return;
        var update = new SmsUsageEvent();
        update.setResult(result.name());
        update.setBizId(safe(bizId,128));
        update.setProviderRequestId(safe(requestId,128));
        update.setProviderCode(safe(providerCode,80));
        events.updateByQuery(update,QueryWrapper.create().eq(SmsUsageEvent::getId,id).eq(SmsUsageEvent::getResult,Outcome.RESERVED.name()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verification(Long sendId, String phone, SmsScene scene, Outcome result) {
        if (sendId != null && result == Outcome.VERIFIED) {
            // Lock the parent send so concurrent successful checks count one conversion only.
            events.selectOneByQuery(QueryWrapper.create().eq(SmsUsageEvent::getId, sendId).forUpdate());
            if (events.selectCountByQuery(QueryWrapper.create().eq(SmsUsageEvent::getSendId, sendId)
                .eq(SmsUsageEvent::getKind, "VERIFY").eq(SmsUsageEvent::getResult, "VERIFIED")) > 0)
                result = Outcome.VERIFIED_REPEAT;
        }
        var event = event(day(Instant.now()),"VERIFY",scene,phone,result);
        event.setSendId(sendId);
        events.insert(event);
        if (sendId != null && result == Outcome.VERIFIED && scene != SmsScene.UNCLASSIFIED) {
            var update = new SmsUsageEvent();
            update.setScene(scene.name());
            events.updateByQuery(update,QueryWrapper.create().eq(SmsUsageEvent::getId,sendId)
                .eq(SmsUsageEvent::getKind,"SEND").eq(SmsUsageEvent::getScene,SmsScene.UNCLASSIFIED.name()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linkAccount(Long sendId,Long accountId) {
        if(sendId == null || accountId == null) return;
        var update = new SmsUsageEvent();
        update.setVerifiedAccountId(accountId);
        events.updateByQuery(update,QueryWrapper.create().eq(SmsUsageEvent::getId,sendId)
            .eq(SmsUsageEvent::getKind,"SEND").isNull(SmsUsageEvent::getVerifiedAccountId));
    }

    public Limits limits() {
        var row = policies.selectOneById(1L);
        return new Limits(row == null ? DEFAULT_DAILY_LIMIT : row.getDailyLimit());
    }

    @Transactional
    public Limits saveLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > 30) throw new CoolException("每个号码每天允许 1 至 30 次",400);
        var row = policies.selectOneByQuery(QueryWrapper.create().eq(SmsUsagePolicy::getId,1L).forUpdate());
        if (row == null) throw new CoolException("短信配置尚未初始化，请执行数据库迁移",503);
        int previous = row.getDailyLimit();
        if (previous != limit) {
            var audit = event(day(Instant.now()),"POLICY",SmsScene.UNCLASSIFIED,null,Outcome.UPDATED);
            audit.setPreviousLimit(previous);
            audit.setNextLimit(limit);
            row.setDailyLimit(limit);
            policies.update(row);
            events.insert(audit);
        }
        return new Limits(limit);
    }

    private static SmsUsageEvent event(String dayKey,String kind,SmsScene scene,String phone,Outcome result) {
        var event = new SmsUsageEvent();
        event.setDayKey(dayKey);
        event.setKind(kind);
        event.setScene(scene.name());
        event.setResult(result.name());
        if(phone != null) event.setPhoneMask(phone.substring(0,3) + "****" + phone.substring(7));
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof JwtUser user && user.getUserId() != null) {
            event.setActorType(user.getUserTypeEnum().name());
            event.setActorId(user.getUserId());
        } else event.setActorType("GUEST");
        return event;
    }

    private String fingerprint(String phone) {
        String key = usageKey == null || usageKey.isBlank() ? jwt.getTokenSecret() : usageKey;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("sms-usage:v1:" + phone).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("短信额度指纹初始化失败",e);
        }
    }

    private static String safe(String value,int max) {
        return value == null || value.length() > max || !value.matches("[A-Za-z0-9._:^|/-]+") ? null : value;
    }

    public static String day(Instant time) { return time.atZone(ZONE).toLocalDate().toString(); }
}
