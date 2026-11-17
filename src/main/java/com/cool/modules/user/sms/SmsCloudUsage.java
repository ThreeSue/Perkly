package com.cool.modules.user.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.QueryGateVerifyBillingPublicRequest;
import com.aliyun.dypnsapi20170525.models.QueryGateVerifyStatisticPublicRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import com.cool.core.exception.CoolException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 查询阿里云号码认证短信使用统计和月度账单
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:29:00
 */
@Service
@Slf4j
public class SmsCloudUsage {
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private static final String USAGE_ACTION = "dypns:QueryGateVerifyStatisticPublic";
    private static final String BILLING_ACTION = "dypns:QueryGateVerifyBillingPublic";
    private final Client client;

    public SmsCloudUsage(@Lazy @Qualifier("aliyunSmsAuthClient") Client client) {
        this.client = client;
    }

    public record Day(String date, Long success, Long failed, Long unknown) {}
    public record Usage(Long total, Long success, Long failed, Long unknown, List<Day> trend) {}
    public record BillLine(String item, String scene, String units, String unitPrice, String amount) {}
    public record Billing(String month, String amount, List<BillLine> items) {}

    public Usage usage(LocalDate from, LocalDate through) {
        var request = new QueryGateVerifyStatisticPublicRequest().setAuthenticationType(3)
            .setStartDate(from.format(DAY)).setEndDate(through.format(DAY));
        var data = query(USAGE_ACTION, () -> {
            var body = client.queryGateVerifyStatisticPublicWithOptions(request, options()).getBody();
            return checked(USAGE_ACTION, body.getCode(), body.getData());
        });
        if (data == null) return new Usage(null, null, null, null, List.of());
        var trend = data.getDayStatistic();
        return new Usage(data.getTotal(), data.getTotalSuccess(), data.getTotalFail(), data.getTotalUnknown(),
            trend == null ? List.of() : trend.stream().map(row -> new Day(row.getStatisticDateStr(),
                row.getTotalSuccess(), row.getTotalFail(), row.getTotalUnknown())).toList());
    }

    public Billing billing(YearMonth month) {
        var request = new QueryGateVerifyBillingPublicRequest().setAuthenticationType(4).setMonth(month.format(MONTH));
        var data = query(BILLING_ACTION, () -> {
            var body = client.queryGateVerifyBillingPublicWithOptions(request, options()).getBody();
            return checked(BILLING_ACTION, body.getCode(), body.getData());
        });
        if (data == null) return new Billing(month.toString(), null, List.of());
        var items = data.getSceneBillingList();
        return new Billing(month.toString(), data.getAmountSum(), items == null ? List.of() : items.stream()
            .map(row -> new BillLine(row.getItemName(), row.getSceneName(), row.getAdd(),
                row.getSinglePrice(), row.getAmount())).toList());
    }

    private static RuntimeOptions options() {
        return new RuntimeOptions().setAutoretry(false).setConnectTimeout(5000).setReadTimeout(10000);
    }

    private static <T> T query(String action, Callable<T> call) {
        try {
            return call.call();
        } catch (TeaException e) {
            throw failure(action, e.getCode());
        } catch (CoolException e) {
            throw e;
        } catch (Exception e) {
            log.warn("阿里云短信统计请求失败，操作={} 异常类型={}", action, e.getClass().getSimpleName());
            throw new CoolException("阿里云数据暂时无法获取，请稍后重试", 503);
        }
    }

    private static <T> T checked(String action, String code, T data) {
        // 号码认证实际响应使用 200，官方示例也列出了 OK。
        if ("200".equals(code) || "OK".equals(code)) return data;
        throw failure(action, code);
    }

    private static CoolException failure(String action, String code) {
        // SDK 原始错误正文可能带有签名或请求参数，仅保存格式受限的错误码。
        String providerCode = code != null && code.matches("[A-Za-z0-9._-]{1,80}") ? code : "UNKNOWN";
        log.warn("阿里云短信统计请求失败，操作={} 错误码={}", action, providerCode);
        if ("Forbidden.NoPermission".equals(code)) {
            return new CoolException("当前阿里云 AccessKey 缺少 " + action + " 权限，请在 RAM 授权后重试", 503);
        }
        return new CoolException("阿里云数据暂时无法获取，请检查号码认证服务后重试", 503);
    }
}
