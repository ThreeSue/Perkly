package com.cool.modules.user.sms;

import com.aliyun.mns.client.CloudAccount;
import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.common.ServiceException;
import com.aliyun.mns.common.http.ClientConfiguration;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用云通信临时凭证安全消费华东一短信回执队列
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:53:00
 */
@Component
public class AliyunReceiptQueue implements SmsReceiptQueue {
    @Value("${sms-auth.access-key-id:}") private String accessId;
    @Value("${sms-auth.access-key-secret:}") private String accessSecret;
    @Value("${sms-auth.receipts.queue-name:}") private String queueName;
    @Value("${sms-auth.receipts.region:cn-hangzhou}") private String region;
    private final ObjectMapper json;
    private DefaultAcsClient auth;
    private MNSClient client;
    private CloudQueue queue;
    private Instant expires = Instant.EPOCH;

    public AliyunReceiptQueue(ObjectMapper json) { this.json = json; }

    private void connect() throws Exception {
        if (queue != null && expires.isAfter(Instant.now().plusSeconds(120))) return;
        if (accessId.isBlank() || accessSecret.isBlank() || queueName.isBlank())
            throw new IllegalStateException("MISSING_CONFIGURATION");
        // This deployment is configured for 华东1; do not guess other service-account endpoints.
        if (!"cn-hangzhou".equals(region)) throw new IllegalStateException("UNSUPPORTED_REGION");
        if (auth == null) auth = new DefaultAcsClient(DefaultProfile.getProfile(region, accessId, accessSecret));
        var request = new CommonRequest();
        request.setSysDomain("dybaseapi.aliyuncs.com");
        request.setSysVersion("2017-05-25");
        request.setSysAction("QueryTokenForMnsQueue");
        request.setSysProtocol(ProtocolType.HTTPS);
        request.setSysMethod(MethodType.POST);
        request.setSysConnectTimeout(5000);
        request.setSysReadTimeout(10000);
        request.putQueryParameter("MessageType", "DypnsSmsVerifyReport");
        request.putQueryParameter("QueueName", queueName);
        var response = json.readTree(auth.getCommonResponse(request).getData());
        if (!"OK".equals(response.path("Code").asText()))
            throw new IllegalStateException(safeCode(response.path("Code").asText()));
        var token = response.path("MessageTokenDTO");
        Instant nextExpiry = LocalDateTime.parse(token.path("ExpireTime").asText(),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(SmsUsageService.ZONE).toInstant();
        if (!nextExpiry.isAfter(Instant.now().plusSeconds(120))) throw new IllegalStateException("EXPIRED_TOKEN");
        for (String field : List.of("AccessKeyId", "AccessKeySecret", "SecurityToken"))
            if (token.path(field).asText().isBlank()) throw new IllegalStateException("INVALID_TOKEN");
        var config = new ClientConfiguration();
        config.setConnectionTimeout(5000);
        config.setSocketTimeout(15000);
        config.setMaxErrorRetry(2);
        var next = new CloudAccount(token.path("AccessKeyId").asText(), token.path("AccessKeySecret").asText(),
            "https://1943695596114318.mns.cn-hangzhou.aliyuncs.com/",
            token.path("SecurityToken").asText(), null, config).getMNSClient();
        if (client != null) client.close();
        client = next;
        queue = client.getQueueRef(queueName);
        expires = nextExpiry;
    }

    @Override public List<Message> receive() throws Exception {
        connect();
        try {
            var messages = queue.batchPopMessage(10, 5);
            return messages == null ? List.of() : messages.stream()
                .map(item -> new Message(item.getReceiptHandle(), item.getMessageBodyAsString())).toList();
        } catch (ServiceException error) {
            if ("MessageNotExist".equals(error.getErrorCode())) return List.of();
            expires = Instant.EPOCH;
            throw error;
        }
    }

    @Override public void acknowledge(String handle) { queue.deleteMessage(handle); }
    @Override public void close() {
        if (client != null) client.close();
        if (auth != null) auth.shutdown();
        client = null; queue = null; auth = null; expires = Instant.EPOCH;
    }
    public static String safeCode(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,80}") ? value : "PROVIDER_ERROR";
    }
}
