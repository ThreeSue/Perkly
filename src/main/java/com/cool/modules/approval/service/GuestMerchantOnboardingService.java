package com.cool.modules.approval.service;

import com.cool.modules.approval.entity.MerchantApplicationEntity;
import com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.*;
import com.cool.modules.approval.entity.resp.MerchantOnboardingResp;
import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义游客入驻审批发码及手机号验证开通的业务边界
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:04:31
 */
public interface GuestMerchantOnboardingService {
    String verify(String phone, String smsCode);
    MerchantOnboardingResp submit(Submit input, boolean resubmit);
    List<MerchantOnboardingResp> list(String token);
    MerchantOnboardingResp info(String token, String applicationNo);
    MerchantOnboardingResp renew(String token, String applicationNo);
    MerchantOnboardingResp activate(Activate input);
    void issue(MerchantApplicationEntity application);
}
