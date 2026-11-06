package com.cool.modules.wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用项目已有调度设施定期派发持久化活动通知
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:24:13
 */
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="perkly.activity-notifications.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class ActivityNoticeScheduler {
    private final ActivityNoticeService notices;
    @Scheduled(initialDelay=5000,fixedDelay=30000)
    public void dispatch() {
        try { notices.tick(); }
        catch(Exception failure) { log.error("活动通知调度异常，持久化任务保留等待下轮",failure); }
    }
}
