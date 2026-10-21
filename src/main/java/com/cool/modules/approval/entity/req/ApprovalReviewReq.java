package com.cool.modules.approval.entity.req;

import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收审批对象版本结果与意见
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
@Data
public class ApprovalReviewReq {
    private String type;
    private Long id;
    private Integer version;
    private Boolean approved;
    private String reason;
}
