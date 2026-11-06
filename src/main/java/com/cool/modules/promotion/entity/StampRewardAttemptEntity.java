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
 * @Description: 保留集章奖励每次发放结果
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:50
 */
@Getter @Setter @Table("stamp_reward_attempt")

public class StampRewardAttemptEntity extends BaseEntity<StampRewardAttemptEntity> {
    private Long progressId;
    private String status;
    @ColumnDefine(length=500) private String message;
}
