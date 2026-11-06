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
 * @Description: 保存AI图片开关模型白名单默认额度和单价估算
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:29
 */
@Getter @Setter @Table("ai_settings")

public class AiSettingsEntity extends BaseEntity<AiSettingsEntity> {
    private Boolean enabled;
    @ColumnDefine(type="text") private String allowedModels;
    private Integer defaultDailyLimit;
    @ColumnDefine(length=12,decimalLength=6) private java.math.BigDecimal estimatedUnitPrice;
}
