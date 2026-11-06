package com.cool.modules.ai.mapper;
import com.cool.modules.ai.entity.AiImageTaskEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 持久化单张AI生成编辑任务幂等标识父图和素材保存状态的数据访问
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:35
 */
@Mapper
public interface AiImageTaskMapper extends BaseMapper<AiImageTaskEntity> {}
