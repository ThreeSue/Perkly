package com.cool.modules.approval.service;
import com.cool.modules.approval.entity.req.MerchantRegistrationReq;
import com.cool.modules.approval.entity.resp.MerchantRegistrationResp;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义商家入驻业务接口
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
public interface MerchantRegistrationService {
    MerchantRegistrationResp submit(MerchantRegistrationReq input,boolean resubmit);
}
