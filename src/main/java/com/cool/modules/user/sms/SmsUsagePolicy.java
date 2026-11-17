package com.cool.modules.user.sms;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存平台管理端配置的号码日发送上限
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
@Getter
@Setter
@Table("sms_usage_policy")
@org.dromara.autotable.annotation.Ignore
public class SmsUsagePolicy extends BaseEntity<SmsUsagePolicy> {
    private Integer dailyLimit;
}
