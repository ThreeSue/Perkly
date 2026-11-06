package com.cool.modules.promotion.mapper;
import com.cool.modules.promotion.entity.StampStockEntryEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录运营补充集章奖励库存的幂等操作的数据访问
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:52
 */
@Mapper public interface StampStockEntryMapper extends BaseMapper<StampStockEntryEntity> {}
