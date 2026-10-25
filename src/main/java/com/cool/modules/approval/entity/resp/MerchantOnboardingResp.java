package com.cool.modules.approval.entity.resp;

import java.util.Date;
import java.util.Map;
import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 返回本人入驻申请审批进度及开通状态并按需展示营业码
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:03:08
 */
@Data
public class MerchantOnboardingResp {
    private String applicationNo;
    private Integer submissionNo;
    private String merchantName;
    private Integer reviewStatus;
    private String state;
    private String stateText;
    private String reason;
    private Map<String,Object> snapshot;
    private String activationCode;
    private Date codeExpiresAt;
    private Date activatedAt;
    private Long merchantId;
    private Long accountId;
}
