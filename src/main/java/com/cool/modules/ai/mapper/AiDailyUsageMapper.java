package com.cool.modules.ai.mapper;
import com.cool.modules.ai.entity.AiDailyUsageEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 锁定商家每日AI调用额度并记录成功失败和待确认用量的数据访问
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:34
 */
@Mapper
public interface AiDailyUsageMapper extends BaseMapper<AiDailyUsageEntity> {}
