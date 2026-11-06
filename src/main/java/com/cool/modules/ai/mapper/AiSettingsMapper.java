package com.cool.modules.ai.mapper;
import com.cool.modules.ai.entity.AiSettingsEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存AI图片开关模型白名单默认额度和单价估算的数据访问
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:31
 */
@Mapper
public interface AiSettingsMapper extends BaseMapper<AiSettingsEntity> {}
