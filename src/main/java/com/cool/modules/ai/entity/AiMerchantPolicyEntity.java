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
 * @Description: 保存商家专属AI图片额度和启停策略
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:32:32
 */
@Getter @Setter @Table("ai_merchant_policy")
@org.dromara.autotable.annotation.TableIndex(name="uk_ai_merchant_policy",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"merchantId"})
public class AiMerchantPolicyEntity extends BaseEntity<AiMerchantPolicyEntity> {
    private Long merchantId;
    private Integer dailyLimit;
    private Boolean enabled;
}
