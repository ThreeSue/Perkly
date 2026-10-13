package com.cool.modules.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.service.AliyunSmsAuthService;
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
        when(client.sendSmsVerifyCode(any())).thenReturn(new SendSmsVerifyCodeResponse().setBody(
            new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        service(client).send("13800000000");
        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        verify(client).sendSmsVerifyCode(request.capture());
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
        when(client.sendSmsVerifyCode(any())).thenReturn(new SendSmsVerifyCodeResponse().setBody(
            new SendSmsVerifyCodeResponseBody().setCode("FAIL").setSuccess(false)));
        assertThrows(CoolException.class, () -> service(client).send("13800000000"));
    }
}
