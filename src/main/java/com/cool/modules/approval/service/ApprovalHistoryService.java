package com.cool.modules.approval.service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义审批历史业务接口
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
public interface ApprovalHistoryService {
    void record(String type,Long id,Long merchantId,int version,String action,String reason,String snapshot);
    void append(String type,Long id,Long merchantId,int round,int version,String action,String reason,String snapshot,String applicant);
}
