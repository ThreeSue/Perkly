package com.cool.modules.wallet.entity;

import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存商家活动退出与门店排除
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:19:35
 */
@Getter @Setter @Table("wallet_activity_exclusion")
@org.dromara.autotable.annotation.TableIndex(name="uk_activity_exclusion", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"activityId","merchantId","storeId"})
public class WalletActivityExclusionEntity extends BaseEntity<WalletActivityExclusionEntity> {
    private Long activityId;
    private Long merchantId;
    private Long storeId;
}
