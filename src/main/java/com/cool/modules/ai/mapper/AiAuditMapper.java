package com.cool.modules.ai.mapper;
import com.cool.modules.ai.entity.AiAuditEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录AI控制台额度策略及任务补偿操作审计的数据访问
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:37
 */
@Mapper
public interface AiAuditMapper extends BaseMapper<AiAuditEntity> {}
