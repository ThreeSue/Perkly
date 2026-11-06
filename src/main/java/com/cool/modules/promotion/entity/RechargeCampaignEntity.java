package com.cool.modules.promotion.entity;
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
 * @Description: 记录充值赠送档位和每人参与限制
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:27:59
 */
@Getter @Setter @Table("recharge_campaign")
@org.dromara.autotable.annotation.TableIndex(name="uk_recharge_campaign_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"activityId"})
public class RechargeCampaignEntity extends BaseEntity<RechargeCampaignEntity> {
    private Long activityId;
    @ColumnDefine(type="mediumtext") private String tiers;
    private Integer perCustomerLimit;
}
