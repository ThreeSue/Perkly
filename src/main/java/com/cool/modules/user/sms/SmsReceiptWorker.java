package com.cool.modules.user.sms;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 独立消费短信回执并暴露同步状态，失败不确认且退避重试
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:53:00
 */
@Component @Slf4j
public class SmsReceiptWorker {
    private final SmsReceiptQueue queue;
    private final SmsReceiptStore store;
    @Value("${sms-auth.receipts.enabled:false}") private boolean enabled;
    private ScheduledExecutorService executor;
    private volatile Status status = new Status("DISABLED", null, null, 0, null);
    private Instant retryAt = Instant.EPOCH;
    public record Status(String state, Instant lastPoll, Instant lastReceipt, int failures, String errorCode) {}
    public SmsReceiptWorker(SmsReceiptQueue queue, SmsReceiptStore store) { this.queue = queue; this.store = store; }
    public Status status() { return status; }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!enabled || executor != null) return;
        status = new Status("CONNECTING", null, null, 0, null);
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "sms-receipts"); thread.setDaemon(true); return thread;
        });
        executor.scheduleWithFixedDelay(this::poll, 0, 5, TimeUnit.SECONDS);
    }

    public void poll() {
        if (Instant.now().isBefore(retryAt)) return;
        try {
            var messages = queue.receive();
            Instant received = status.lastReceipt();
            for (var message : messages) {
                store.accept(message.body());
                queue.acknowledge(message.handle());
                received = Instant.now();
                status = new Status("RUNNING", Instant.now(), received, 0, null);
            }
            status = new Status("RUNNING", Instant.now(), received, 0, null);
            retryAt = Instant.EPOCH;
        } catch (Exception error) {
            int failures = Math.min(100, status.failures() + 1);
            String code = error instanceof com.aliyuncs.exceptions.ClientException cloud ? cloud.getErrCode()
                : error instanceof com.aliyun.mns.common.ServiceException mns ? mns.getErrorCode()
                : error instanceof IllegalStateException ? error.getMessage() : error.getClass().getSimpleName();
            code = AliyunReceiptQueue.safeCode(code);
            status = new Status("ERROR", status.lastPoll(), status.lastReceipt(), failures, code);
            retryAt = Instant.now().plusSeconds(Math.min(300, 10L * (1L << Math.min(failures, 5))));
            // Never log provider bodies, phones, handles, or temporary credentials.
            log.warn("短信回执同步失败，错误码={}，连续失败次数={}", code, failures);
        }
    }

    @PreDestroy public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try { executor.awaitTermination(20, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }
        queue.close();
    }
}
