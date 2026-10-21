package com.cool.modules.approval.service;
import com.cool.modules.approval.entity.resp.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义审批中心业务接口
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
public interface ApprovalCenterService {
    ApprovalPageResp page(String type,Integer status,String keyword,int page,int size);
    ApprovalDetailResp info(String type,Long id);
    void review(String type,Long id,Integer version,Boolean approved,String reason);
}
