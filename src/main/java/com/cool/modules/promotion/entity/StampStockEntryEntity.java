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
 * @Description: 记录运营补充集章奖励库存的幂等操作
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:51
 */
@Getter @Setter @Table("stamp_stock_entry")
@org.dromara.autotable.annotation.TableIndex(name="uk_stamp_stock_entry_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"activityId","requestId"})
public class StampStockEntryEntity extends BaseEntity<StampStockEntryEntity> {
    private Long activityId;
    private String requestId;
    private Long operatorId;
    private Long amount;
}
