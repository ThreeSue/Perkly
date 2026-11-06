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
 * @Description: 保存消费完成时命中的集章活动版本
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:46
 */
@Getter @Setter @Table("stamp_visit_match")
@org.dromara.autotable.annotation.TableIndex(name="uk_stamp_visit_match_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"visitId","activityId"})
public class StampVisitMatchEntity extends BaseEntity<StampVisitMatchEntity> {
    private Long visitId;
    private Long activityId;
    private Integer activityVersion;
    private String status;
    @ColumnDefine(length=500) private String lastError;
}
