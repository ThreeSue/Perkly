package com.cool.modules.user.mapper;

import com.cool.modules.user.sms.SmsUsageEvent;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 读写脱敏短信发送、核验和用量策略变更事件
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
@Mapper
public interface SmsUsageEventMapper extends BaseMapper<SmsUsageEvent> {
}
