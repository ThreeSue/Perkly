package com.cool.modules.approval.entity.resp;

import lombok.Data;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 返回审批列表业务信息
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
@Data
public class ApprovalItemResp {
    private Long id;
    private Long templateId;
    private String title;
    private String merchantName;
    private String submittedBy;
    private Date submittedAt;
    private Integer status;
    private Integer version;
    private Integer publishStatus;
    private String activationState;
}
