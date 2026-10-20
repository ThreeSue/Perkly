package com.cool.modules.coupon.service;

import com.cool.core.base.BaseService;
import com.cool.modules.coupon.entity.CouponTemplateEntity;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义运营端优惠券模板管理服务
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:12:39
 */
public interface CouponTemplateService extends BaseService<CouponTemplateEntity> {
    java.util.List<java.util.Map<String, Object>> productOptions(Long merchantId);
    CouponTemplateEntity validateForPublication(Long id, Integer version);
}

