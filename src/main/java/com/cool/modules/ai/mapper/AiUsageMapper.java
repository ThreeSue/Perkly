package com.cool.modules.ai.mapper;

import com.cool.modules.ai.entity.AiUsageEvent;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 持久化并查询 AI 用量事件
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 23:19:04
 */
@Mapper
public interface AiUsageMapper extends BaseMapper<AiUsageEvent> {
}
