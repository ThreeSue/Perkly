package com.cool.modules.ai.entity;
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
 * @Description: 锁定商家每日AI调用额度并记录成功失败和待确认用量
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:33
 */
@Getter @Setter @Table("ai_daily_usage")
@org.dromara.autotable.annotation.TableIndex(name="uk_ai_daily_usage",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"merchantId","dayKey"})
public class AiDailyUsageEntity extends BaseEntity<AiDailyUsageEntity> {
    private Long merchantId;
    private String dayKey;
    private Integer reservedCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer uncertainCount;
}
