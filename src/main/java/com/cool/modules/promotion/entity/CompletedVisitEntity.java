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
 * @Description: 保存消费完成的可信用户归属及来源
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:45
 */
@Getter @Setter @Table("completed_visit")
@org.dromara.autotable.annotation.TableIndex(name="uk_completed_visit_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"sourceType","sourceId"})
public class CompletedVisitEntity extends BaseEntity<CompletedVisitEntity> {
    private String sourceType;
    private Long sourceId;
    private Long customerId;
    private Long merchantId;
    private Long storeId;
    private Date completedAt;
}
