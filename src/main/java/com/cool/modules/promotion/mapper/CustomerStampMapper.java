package com.cool.modules.promotion.mapper;
import com.cool.modules.promotion.entity.CustomerStampEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按顾客及商家唯一记录活动集章的数据访问
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:48
 */
@Mapper public interface CustomerStampMapper extends BaseMapper<CustomerStampEntity> {}
