package com.cool.modules.approval.entity.resp;

import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 返回入驻申请编号轮次与状态
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
@Data
public class MerchantRegistrationResp {
    private String applicationNo;
    private Integer submissionNo;
    private String status;
}
