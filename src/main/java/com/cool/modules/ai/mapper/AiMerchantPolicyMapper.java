package com.cool.modules.ai.mapper;
import com.cool.modules.ai.entity.AiMerchantPolicyEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存商家专属AI图片额度和启停策略的数据访问
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:32
 */
@Mapper
public interface AiMerchantPolicyMapper extends BaseMapper<AiMerchantPolicyEntity> {}
