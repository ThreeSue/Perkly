package com.cool.modules.user.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.cool.core.exception.CoolException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 对接号码认证服务：由阿里云生成和核验短信验证码。 */
@Service
public class AliyunSmsAuthService {
    private final Client client;

    @Value("${sms-auth.sign-name:}")
    private String signName;
    @Value("${sms-auth.template-code:}")
    private String templateCode;
    @Value("${sms-auth.scheme-name:quanguanjia}")
    private String schemeName;
    @Value("${sms-auth.valid-time:300}")
    private long validTime;
    @Value("${sms-auth.interval:60}")
    private long interval;

    public AliyunSmsAuthService(@Qualifier("aliyunSmsAuthClient") Client client) {
        this.client = client;
    }

    public long getValidTime() { return validTime; }
    public long getInterval() { return interval; }

    public void send(String phone) {
        if (signName.isBlank() || templateCode.isBlank() || validTime < 60
                || validTime % 60 != 0 || interval < 60) {
            throw new CoolException("请检查短信认证的签名、模板、有效期和发送间隔配置");
        }
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone).setCountryCode("86")
                .setSignName(signName).setTemplateCode(templateCode)
                .setSchemeName(schemeName)
                .setTemplateParam("{\"code\":\"##code##\",\"min\":\"" + validTime / 60 + "\"}")
                .setCodeType(1L).setCodeLength(6L)
                .setValidTime(validTime).setInterval(interval)
                .setDuplicatePolicy(1L).setReturnVerifyCode(false);
        try {
            var response = client.sendSmsVerifyCode(request);
            var body = response == null ? null : response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                throw new CoolException("短信发送请求未成功，请检查阿里云配置和发送记录");
            }
        } catch (CoolException e) {
            throw e;
        } catch (Exception e) {
            // 不将 SDK 的原始异常返回前端，避免带出手机号或请求参数。
            throw new CoolException("短信服务调用失败，请检查权限、余额和网络连接");
        }
    }

    public boolean check(String phone, String code) {
        CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                .setPhoneNumber(phone).setCountryCode("86")
                .setSchemeName(schemeName).setVerifyCode(code);
        try {
            var response = client.checkSmsVerifyCode(request);
            var body = response == null ? null : response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                throw new CoolException("短信核验请求未成功，请稍后重试");
            }
            // 调用成功不等于验证码正确，必须检查 PASS。
            return body.getModel() != null && "PASS".equals(body.getModel().getVerifyResult());
        } catch (CoolException e) {
            throw e;
        } catch (Exception e) {
            throw new CoolException("短信核验服务暂不可用，请稍后重试");
        }
    }
}
