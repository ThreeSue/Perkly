package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.tea.TeaException;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.service.AliyunSmsAuthService;
import com.cool.modules.user.sms.SmsCloudUsage;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AliyunSmsAuthServiceTest {
    private AliyunSmsAuthService service(Client client) {
        var service = new AliyunSmsAuthService(client);
        ReflectionTestUtils.setField(service, "signName", "测试签名");
        ReflectionTestUtils.setField(service, "templateCode", "100001");
        ReflectionTestUtils.setField(service, "schemeName", "quanguanjia");
        ReflectionTestUtils.setField(service, "validTime", 300L);
        ReflectionTestUtils.setField(service, "interval", 60L);
        return service;
    }

    @Test void sendingUsesProviderGeneratedCodeAndHidesCodeInResponse() throws Exception {
        Client client = mock(Client.class);
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse().setBody(
            new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        service(client).send("13800000000", "SMS-1");
        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        var options = ArgumentCaptor.forClass(com.aliyun.teautil.models.RuntimeOptions.class);
        verify(client).sendSmsVerifyCodeWithOptions(request.capture(), options.capture());
        assertEquals("SMS-1", request.getValue().getOutId());
        assertFalse(options.getValue().getAutoretry());
        assertTrue(request.getValue().getTemplateParam().contains("##code##"));
        assertEquals(false, request.getValue().getReturnVerifyCode());
        assertEquals(300L, request.getValue().getValidTime());
        assertEquals(6L, request.getValue().getCodeLength());
        assertEquals("quanguanjia", request.getValue().getSchemeName());
    }

    @Test void apiSuccessAloneCannotAuthorizeLogin() throws Exception {
        Client client = mock(Client.class);
        var body = new CheckSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)
            .setModel(new CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel().setVerifyResult("UNKNOWN"));
        when(client.checkSmsVerifyCode(any())).thenReturn(new CheckSmsVerifyCodeResponse().setBody(body));
        assertFalse(service(client).check("13800000000", "123456"));
        body.getModel().setVerifyResult("PASS");
        assertTrue(service(client).check("13800000000", "123456"));
        var request = ArgumentCaptor.forClass(CheckSmsVerifyCodeRequest.class);
        verify(client, times(2)).checkSmsVerifyCode(request.capture());
        assertEquals("quanguanjia", request.getValue().getSchemeName());
    }

    @Test void failuresAndEmptyResponsesNeverAuthorizeLogin() throws Exception {
        Client client = mock(Client.class);
        when(client.checkSmsVerifyCode(any())).thenReturn(null);
        assertThrows(CoolException.class, () -> service(client).check("13800000000", "123456"));
        when(client.checkSmsVerifyCode(any())).thenThrow(new Exception("SDK内部信息"));
        var error = assertThrows(CoolException.class, () -> service(client).check("13800000000", "123456"));
        assertFalse(error.getMessage().contains("SDK内部信息"));
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse().setBody(
            new SendSmsVerifyCodeResponseBody().setCode("FAIL").setSuccess(false)));
        assertThrows(CoolException.class, () -> service(client).send("13800000000", "SMS-1"));
    }

    @Test void cloudReportsUseSmsAuthenticationAndKeepBillingAmountsExact() throws Exception {
        Client client = mock(Client.class);
        var report = new QueryGateVerifyStatisticPublicResponseBody.QueryGateVerifyStatisticPublicResponseBodyData()
            .setTotal(5L).setTotalSuccess(4L).setTotalFail(1L).setTotalUnknown(0L);
        when(client.queryGateVerifyStatisticPublicWithOptions(any(), any())).thenReturn(
            new QueryGateVerifyStatisticPublicResponse().setBody(
                new QueryGateVerifyStatisticPublicResponseBody().setCode("OK").setData(report)));
        var line = new QueryGateVerifyBillingPublicResponseBody.QueryGateVerifyBillingPublicResponseBodyDataSceneBillingList()
            .setItemName("短信认证").setSceneName("测试").setAdd("5").setSinglePrice("0.040").setAmount("0.200");
        var bill = new QueryGateVerifyBillingPublicResponseBody.QueryGateVerifyBillingPublicResponseBodyData()
            .setAmountSum("0.200").setSceneBillingList(List.of(line));
        when(client.queryGateVerifyBillingPublicWithOptions(any(), any())).thenReturn(
            new QueryGateVerifyBillingPublicResponse().setBody(
                new QueryGateVerifyBillingPublicResponseBody().setCode("OK").setData(bill)));
        var cloud = new SmsCloudUsage(client);
        assertEquals(5L, cloud.usage(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 17)).total());
        assertEquals("0.200", cloud.billing(YearMonth.of(2026, 11)).items().get(0).amount());
        var statistic = ArgumentCaptor.forClass(QueryGateVerifyStatisticPublicRequest.class);
        verify(client).queryGateVerifyStatisticPublicWithOptions(statistic.capture(), any());
        assertEquals(3, statistic.getValue().getAuthenticationType());
        assertEquals("20261111", statistic.getValue().getStartDate());
        assertEquals("20261117", statistic.getValue().getEndDate());
        var billing = ArgumentCaptor.forClass(QueryGateVerifyBillingPublicRequest.class);
        verify(client).queryGateVerifyBillingPublicWithOptions(billing.capture(), any());
        assertEquals(4, billing.getValue().getAuthenticationType());
        assertEquals("202611", billing.getValue().getMonth());
    }

    @Test void cloudReportsAcceptProductionSuccessCodeAndMissingMonthlyBill() throws Exception {
        Client client = mock(Client.class);
        var report = new QueryGateVerifyStatisticPublicResponseBody.QueryGateVerifyStatisticPublicResponseBodyData()
            .setTotal(8L).setTotalSuccess(8L);
        when(client.queryGateVerifyStatisticPublicWithOptions(any(), any())).thenReturn(
            new QueryGateVerifyStatisticPublicResponse().setBody(
                new QueryGateVerifyStatisticPublicResponseBody().setCode("200").setData(report)));
        when(client.queryGateVerifyBillingPublicWithOptions(any(), any())).thenReturn(
            new QueryGateVerifyBillingPublicResponse().setBody(
                new QueryGateVerifyBillingPublicResponseBody().setCode("200")));
        var cloud = new SmsCloudUsage(client);
        var day = LocalDate.of(2026, 11, 17);
        assertEquals(8L, cloud.usage(day.minusDays(29), day).total());
        var bill = cloud.billing(YearMonth.from(day));
        assertEquals("2026-11", bill.month());
        assertNull(bill.amount());
        assertTrue(bill.items().isEmpty());
    }

    @Test void cloudPermissionErrorsIdentifyTheMissingActionWithoutReturningProviderDetails() throws Exception {
        Client client = mock(Client.class);
        var denied = new TeaException();
        denied.setCode("Forbidden.NoPermission");
        denied.setMessage("SDK 原始请求详情");
        when(client.queryGateVerifyStatisticPublicWithOptions(any(), any())).thenThrow(denied);
        when(client.queryGateVerifyBillingPublicWithOptions(any(), any())).thenReturn(
            new QueryGateVerifyBillingPublicResponse().setBody(new QueryGateVerifyBillingPublicResponseBody()
                .setCode("Forbidden.NoPermission").setMessage("SDK 原始请求详情")));
        var cloud = new SmsCloudUsage(client);
        var day = LocalDate.of(2026, 11, 17);
        var usage = assertThrows(CoolException.class, () -> cloud.usage(day.minusDays(6), day));
        assertTrue(usage.getMessage().contains("dypns:QueryGateVerifyStatisticPublic"));
        assertFalse(usage.getMessage().contains("SDK 原始请求详情"));
        var billing = assertThrows(CoolException.class, () -> cloud.billing(YearMonth.from(day)));
        assertTrue(billing.getMessage().contains("dypns:QueryGateVerifyBillingPublic"));
        assertFalse(billing.getMessage().contains("SDK 原始请求详情"));
    }
}
