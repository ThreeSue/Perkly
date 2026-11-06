package com.cool.modules.wallet.mapper;

import com.cool.modules.wallet.entity.WalletActivityEventEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 持久化活动事件与延时通知任务的数据访问
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:38
 */
@Mapper
public interface WalletActivityEventMapper extends BaseMapper<WalletActivityEventEntity> {}
