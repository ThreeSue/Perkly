package com.cool.modules.approval.entity.resp;

import lombok.Data;
import java.util.List;
import java.util.Map;
import com.cool.modules.approval.entity.AuditRecordEntity;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 返回审批详情与历史轨迹
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
@Data
public class ApprovalDetailResp {
    private ApprovalItemResp item;
    private Map<String, Object> snapshot;
    private List<AuditRecordEntity> history;
    private String nextStep;
    private boolean historical;
    private boolean canReview;
}
