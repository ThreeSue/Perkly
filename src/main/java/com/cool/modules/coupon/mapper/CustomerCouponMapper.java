package com.cool.modules.coupon.mapper;

import com.cool.modules.coupon.entity.CustomerCouponEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 类型安全读取既有持券归属
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 20:56:06
 */
@Mapper
public interface CustomerCouponMapper extends BaseMapper<CustomerCouponEntity> {}
