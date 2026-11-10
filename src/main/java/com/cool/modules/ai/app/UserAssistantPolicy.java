package com.cool.modules.ai.app;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为现有AI控制台提供用户助手开关及原子额度消费接入口
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
public interface UserAssistantPolicy {
    /** 关闭时抛503，额度耗尽抛429；默认不限次应直接放行，不能关闭功能。 */
    void checkAndConsume(Long customerId);
}
