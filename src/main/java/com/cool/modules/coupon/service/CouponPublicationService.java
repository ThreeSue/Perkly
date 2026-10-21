package com.cool.modules.coupon.service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义优惠券发布业务接口
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:25:37
 */
public interface CouponPublicationService {
    Object submitAudit(Long id,Integer version);
    Object audit(Long id,Integer version,Boolean approved);
    Object audit(Long id,Integer version,Boolean approved,String reason);
    Object withdrawAudit(Long id,Integer version);
    Object publish(Long id,Integer version);
    Object unpublish(Long id,Integer version);
}
