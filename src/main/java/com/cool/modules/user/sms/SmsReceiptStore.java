package com.cool.modules.user.sms;

import com.cool.modules.user.mapper.SmsReceiptMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 校验并事务保存短信回执，拒绝在落库失败时确认消息
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:50:00
 */
@Service @RequiredArgsConstructor
public class SmsReceiptStore {
    private final SmsReceiptMapper receipts;
    private final ObjectMapper json;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(@JsonProperty("biz_id") String bizId,
                         @JsonProperty("phone_number") String phone,
                         Boolean success, @JsonProperty("err_code") String code,
                         @JsonProperty("send_time") String sent,
                         @JsonProperty("report_time") String reported) {}

    // Called through the Spring proxy: return only after commit; worker acknowledges afterwards.
    @Transactional(rollbackFor = Exception.class)
    public void accept(String body) throws Exception {
        var root = json.readTree(body);
        if (root == null || (!root.isObject() && !root.isArray()) || root.isArray() && root.isEmpty())
            throw new IllegalArgumentException("INVALID_RECEIPT");
        if (root.isArray()) {
            for (var item : root) save(json.treeToValue(item, Report.class));
        } else save(json.treeToValue(root, Report.class));
    }

    private void save(Report report) {
        SmsReceipt row = normalize(report);
        try { receipts.insert(row); }
        catch (DuplicateKeyException duplicate) {
            // Serialize concurrent redelivery and permit newer provider corrections only.
            var old = receipts.selectOneByQuery(QueryWrapper.create()
                .eq(SmsReceipt::getBizId, row.getBizId()).forUpdate());
            if (old == null) throw duplicate;
            if (row.getReportedAt().isAfter(old.getReportedAt())) {
                row.setId(old.getId());
                receipts.update(row);
            }
        }
    }

    public static SmsReceipt normalize(Report report) {
        if (report.bizId() == null || !report.bizId().matches("[A-Za-z0-9._:^|/-]{1,128}")
            || report.phone() == null || !report.phone().matches("(?:86)?1[0-9]{10}")
            || report.success() == null) throw new IllegalArgumentException("INVALID_RECEIPT");
        var row = new SmsReceipt();
        row.setBizId(report.bizId());
        String phone = report.phone().substring(report.phone().length() - 11);
        row.setPhoneMask(phone.substring(0, 3) + "****" + phone.substring(7));
        row.setResult(report.success() ? "DELIVERED" : "DELIVERY_FAILED");
        row.setProviderCode(report.code() != null && report.code().matches("[A-Za-z0-9._-]{1,80}") ? report.code() : "UNKNOWN");
        row.setSentAt(LocalDateTime.parse(report.sent(), TIME));
        row.setReportedAt(LocalDateTime.parse(report.reported(), TIME));
        return row;
    }
}
