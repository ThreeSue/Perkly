package com.cool.modules.user.sms;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存按发送流水去重的脱敏阿里云短信回执
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:50:00
 */
@Getter @Setter
@Table("sms_receipt")
@org.dromara.autotable.annotation.Ignore
public class SmsReceipt extends BaseEntity<SmsReceipt> {
    private String bizId;
    private String phoneMask;
    private String result;
    private String providerCode;
    private LocalDateTime sentAt;
    private LocalDateTime reportedAt;
}
