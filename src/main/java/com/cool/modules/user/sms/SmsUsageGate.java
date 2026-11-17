package com.cool.modules.user.sms;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存手机号跨入口发送限额与冷却状态
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
@Getter
@Setter
@Table("sms_usage_gate")
@org.dromara.autotable.annotation.Ignore
public class SmsUsageGate extends BaseEntity<SmsUsageGate> {
    private String phoneKey;
    private String dayKey;
    private Integer attempts;
    private Long nextSendAt;
}
