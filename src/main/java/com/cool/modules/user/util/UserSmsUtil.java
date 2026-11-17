package com.cool.modules.user.util;

import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolPreconditions;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.service.AliyunSmsAuthService;
import com.cool.modules.user.sms.SmsScene;
import com.cool.modules.user.sms.SmsUsageService;
import com.cool.modules.user.sms.SmsUsageService.Outcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UserSmsUtil - 用户短信工具类
 * 使用阿里云短信认证 SDK，保存本地限流和验证码使用状态，不保存验证码。
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class UserSmsUtil {

    public static void validatePhone(String phone) {
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) {
            throw new CoolException("请输入正确的中国大陆手机号");
        }
    }

    /**
     * 短信发送场景枚举
     */
    public enum SendSceneEnum {
        ALL,
    }

    private final AliyunSmsAuthService smsAuthService;

    private final CoolCache coolCache;
    private final SmsUsageService usage;

    /**
     * 发送短信验证码
     *
     * @param phone
     */
    public void sendVerifyCode(String phone, SendSceneEnum sendSceneEnum, SmsScene scene) {
        validatePhone(phone);
        String key = sendSceneEnum.name() + "_sms:" + phone;
        Long nextSend = coolCache.get(key + ":nextSend", Long.class);
        long ttl = smsAuthService.getValidTime();
        long interval = smsAuthService.getInterval();
        SmsUsageService.Admission admission;
        try {
            admission = usage.reserve(phone,scene,Math.max(60,interval),nextSend);
        } catch (RuntimeException e) {
            log.error("短信发送额度预占失败，异常类型 {}",e.getClass().getSimpleName());
            throw new CoolException("短信服务暂不可用，请稍后重试",503);
        }
        if (admission.outcome() != Outcome.RESERVED) {
            boolean daily=admission.outcome()==Outcome.DAILY_LIMIT;
            var denied=new CoolException(daily?"该手机号今日获取验证码次数已达上限，请明天再试":
                "请"+admission.retryAfterSeconds()+"秒后再获取验证码",429);
            denied.setData(java.util.Map.of("reason",admission.outcome().name(),"retryAfterSeconds",admission.retryAfterSeconds()));
            throw denied;
        }
        AliyunSmsAuthService.Accepted receipt;
        try {
            receipt=smsAuthService.send(phone,"SMS-"+admission.id());
        } catch (AliyunSmsAuthService.SendFailure e) {
            observe(()->usage.finish(admission.id(),e.rejected()?Outcome.REJECTED:Outcome.UNKNOWN,null,e.requestId(),e.providerCode()));
            throw e;
        } catch (RuntimeException e) {
            observe(()->usage.finish(admission.id(),Outcome.UNKNOWN,null,null,null));
            throw new CoolException("短信发送结果暂时无法确认，请稍后查看",503);
        }
        observe(()->usage.finish(admission.id(),Outcome.ACCEPTED,receipt.bizId(),receipt.requestId(),"OK"));
        // Caffeine 模式忽略单项 ttl，另存绝对截止时间确保过期。
        coolCache.set(key + ":expires", System.currentTimeMillis() + ttl * 1000, ttl);
        coolCache.set(key + ":nextSend", System.currentTimeMillis() + interval * 1000, interval);
        coolCache.set(key + ":usageId",admission.id(),ttl);
        // 仅存期限及次数，不保存短信验证码。
        coolCache.del(key);
        coolCache.del(key + ":attempts");
    }

    /**
     * 检查验证码
     * @param phone
     * @param code
     * @return
     */
    public synchronized Long checkVerifyCode(String phone, String code, SendSceneEnum sendSceneEnum, SmsScene scene) {
        validatePhone(phone);
        String key = sendSceneEnum.name() + "_sms:" + phone;
        Long expires = coolCache.get(key + ":expires", Long.class);
        if (expires == null || System.currentTimeMillis() >= expires) {
            coolCache.del(key, key + ":expires", key + ":attempts", key + ":usageId");
            throw new CoolException("验证码已过期，请重新获取");
        }
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new CoolException("请输入6位数字短信验证码");
        }
        Long sendId=coolCache.get(key + ":usageId", Long.class);
        boolean flag;
        try { flag = smsAuthService.check(phone, code); }
        catch (RuntimeException e) {
            observe(()->usage.verification(sendId,phone,scene,Outcome.VERIFY_ERROR));
            throw e;
        }
        observe(()->usage.verification(sendId,phone,scene,flag?Outcome.VERIFIED:Outcome.INCORRECT));
        if (flag) {
            // 删除验证码
            coolCache.del(key, key + ":expires", key + ":attempts", key + ":usageId");
        } else {
            Integer attempts = coolCache.get(key + ":attempts", Integer.class);
            int count = attempts == null ? 1 : attempts + 1;
            if (count >= 5) {
                coolCache.del(key, key + ":expires", key + ":attempts", key + ":usageId");
                throw new CoolException("验证码错误次数过多，请重新获取");
            }
            coolCache.set(key + ":attempts", count, 300);
        }
        CoolPreconditions.check(!flag, "验证码错误");
        return sendId;
    }

    public void linkAccount(Long sendId,Long accountId) {
        if(sendId==null || accountId==null)return;
        Runnable link=()->observe(()->usage.linkAccount(sendId,accountId));
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { link.run(); }
                });
        } else link.run();
    }

    private void observe(Runnable action) {
        try { action.run(); }
        catch(RuntimeException e) { log.warn("短信统计写入失败，异常类型 {}",e.getClass().getSimpleName()); }
    }
}
