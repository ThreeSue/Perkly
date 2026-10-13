package com.cool.modules.user.util;

import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolPreconditions;
import com.cool.core.exception.CoolException;
import com.cool.modules.user.service.AliyunSmsAuthService;
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

    /**
     * 发送短信验证码
     *
     * @param phone
     */
    public synchronized void sendVerifyCode(String phone, SendSceneEnum sendSceneEnum) {
        validatePhone(phone);
        String key = sendSceneEnum.name() + "_sms:" + phone;
        long now = System.currentTimeMillis();
        Long nextSend = coolCache.get(key + ":nextSend", Long.class);
        if (nextSend != null && now < nextSend) throw new CoolException("请60秒后再获取验证码");
        smsAuthService.send(phone);
        long ttl = smsAuthService.getValidTime();
        long interval = smsAuthService.getInterval();
        // Caffeine 模式忽略单项 ttl，另存绝对截止时间确保过期。
        coolCache.set(key + ":expires", System.currentTimeMillis() + ttl * 1000, ttl);
        coolCache.set(key + ":nextSend", System.currentTimeMillis() + interval * 1000, interval);
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
    public synchronized void checkVerifyCode(String phone, String code, SendSceneEnum sendSceneEnum) {
        validatePhone(phone);
        String key = sendSceneEnum.name() + "_sms:" + phone;
        Long expires = coolCache.get(key + ":expires", Long.class);
        if (expires == null || System.currentTimeMillis() >= expires) {
            coolCache.del(key, key + ":expires", key + ":attempts");
            throw new CoolException("验证码已过期，请重新获取");
        }
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new CoolException("请输入6位数字短信验证码");
        }
        boolean flag = smsAuthService.check(phone, code);
        if (flag) {
            // 删除验证码
            coolCache.del(key, key + ":expires", key + ":attempts");
        } else {
            Integer attempts = coolCache.get(key + ":attempts", Integer.class);
            int count = attempts == null ? 1 : attempts + 1;
            if (count >= 5) {
                coolCache.del(key, key + ":expires", key + ":attempts");
                throw new CoolException("验证码错误次数过多，请重新获取");
            }
            coolCache.set(key + ":attempts", count, 300);
        }
        CoolPreconditions.check(!flag, "验证码错误");
    }

}
