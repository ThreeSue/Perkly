package com.cool.modules.promotion.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定时处理持久化集章进度和待发奖励
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:50:26
 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="perkly.stamp.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class StampScheduler {
    private final StampProgressService progress;
    @Scheduled(fixedDelayString="${perkly.stamp.scan-interval-ms:10000}",initialDelay=15000)
    public void tick(){progress.tick();}
}
