package com.cool.modules.profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在业务交易事务之外有界处理画像刷新任务
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:12:00
 */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="perkly.profile.worker-enabled",havingValue="true")
public class ProfileWorker {
 private final ProfileService profiles;
 @Scheduled(fixedDelay=15000,initialDelay=15000) public void tick(){profiles.runOne();}
}
