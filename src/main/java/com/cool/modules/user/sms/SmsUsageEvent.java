package com.cool.modules.user.sms;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存脱敏短信发送、核验和用量策略变更事件
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 14:23:00
 */
@Getter
@Setter
@Table("sms_usage_event")
@org.dromara.autotable.annotation.Ignore
public class SmsUsageEvent extends BaseEntity<SmsUsageEvent> {
    private String dayKey;
    private String kind;
    private String scene;
    private String result;
    private String phoneMask;
    private Long sendId;
    private String bizId;
    private String providerCode;
    private String providerRequestId;
    private String actorType;
    private Long actorId;
    private Long verifiedAccountId;
    private Integer previousLimit;
    private Integer nextLimit;
}
