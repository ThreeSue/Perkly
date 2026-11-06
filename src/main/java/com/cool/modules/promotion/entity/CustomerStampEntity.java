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
 * @Description: 按顾客及商家唯一记录活动集章
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:33:47
 */
@Getter @Setter @Table("customer_stamp")
@org.dromara.autotable.annotation.TableIndex(name="uk_customer_stamp_0",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"activityId","customerId","merchantId"})
public class CustomerStampEntity extends BaseEntity<CustomerStampEntity> {
    private Long activityId;
    private Long customerId;
    private Long merchantId;
    private Long visitId;
}
