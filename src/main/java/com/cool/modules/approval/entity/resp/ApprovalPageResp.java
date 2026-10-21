package com.cool.modules.approval.entity.resp;

import lombok.Data;
import java.util.List;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 返回审批列表与分页
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
@Data
public class ApprovalPageResp {
    private List<ApprovalItemResp> list;
    private ApprovalPaginationResp pagination;
}
