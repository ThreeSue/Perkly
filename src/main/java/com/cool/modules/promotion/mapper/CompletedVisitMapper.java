package com.cool.modules.promotion.mapper;
import com.cool.modules.promotion.entity.CompletedVisitEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存消费完成的可信用户归属及来源的数据访问
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:45
 */
@Mapper public interface CompletedVisitMapper extends BaseMapper<CompletedVisitEntity> {}
