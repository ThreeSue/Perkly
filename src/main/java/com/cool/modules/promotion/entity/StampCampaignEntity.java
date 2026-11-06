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
 * @Description: 保存跨商家集章目标和冻结奖励规则
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:43
 */
@Getter @Setter @Table("stamp_campaign")
@org.dromara.autotable.annotation.TableIndex(name="uk_stamp_campaign_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"activityId"})
public class StampCampaignEntity extends BaseEntity<StampCampaignEntity> {
    private Long activityId;
    @ColumnDefine(type="mediumtext") private String goals;
    private Long rewardTemplateId;
    private Integer rewardRuleVersion;
    @ColumnDefine(type="mediumtext") private String rewardSnapshot;
    private Long rewardStock;
    private Long grantedCount;
}
