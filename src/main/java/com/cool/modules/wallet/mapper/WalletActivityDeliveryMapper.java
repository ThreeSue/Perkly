package com.cool.modules.wallet.mapper;

import com.cool.modules.wallet.entity.WalletActivityDeliveryEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按活动事件及收件账号持久化投递状态的数据访问
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:40
 */
@Mapper
public interface WalletActivityDeliveryMapper extends BaseMapper<WalletActivityDeliveryEntity> {}
