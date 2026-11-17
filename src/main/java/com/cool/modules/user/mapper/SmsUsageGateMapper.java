package com.cool.modules.user.mapper;

import com.cool.modules.user.sms.SmsUsageGate;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 读写手机号跨入口发送限额与冷却状态
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
@Mapper
public interface SmsUsageGateMapper extends BaseMapper<SmsUsageGate> {
    @org.apache.ibatis.annotations.Insert("INSERT INTO sms_usage_gate (phone_key, day_key, attempts, next_send_at) "
        + "VALUES (#{phoneKey}, #{dayKey}, 0, 0) ON DUPLICATE KEY UPDATE id=id")
    void ensure(@org.apache.ibatis.annotations.Param("phoneKey") String phoneKey, @org.apache.ibatis.annotations.Param("dayKey") String dayKey);
}
