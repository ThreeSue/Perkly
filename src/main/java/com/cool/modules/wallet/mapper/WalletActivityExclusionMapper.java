package com.cool.modules.wallet.mapper;

import com.cool.modules.wallet.entity.WalletActivityExclusionEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存商家活动退出与门店排除的数据访问
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:35
 */
@Mapper
public interface WalletActivityExclusionMapper extends BaseMapper<WalletActivityExclusionEntity> {}
