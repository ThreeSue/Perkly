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
 * @Description: 记录集章进度及待发放奖励便于失败重试
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:48
 */
@Getter @Setter @Table("stamp_progress")
@org.dromara.autotable.annotation.TableIndex(name="uk_stamp_progress_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"activityId","customerId"})
public class StampProgressEntity extends BaseEntity<StampProgressEntity> {
    private Long activityId;
    private Long customerId;
    private Integer stampCount;
    private String rewardStatus;
    private Long rewardCouponId;
    private Integer attempts;
    private Date nextAttemptAt;
    private Date completedAt;
    @ColumnDefine(length=500) private String lastError;
}
