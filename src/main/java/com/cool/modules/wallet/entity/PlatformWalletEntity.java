package com.cool.modules.wallet.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;
import java.math.BigDecimal;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 平台储值账户余额
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:45:33
 */
@Getter @Setter @Table("platform_wallet")
@org.dromara.autotable.annotation.TableIndex(name="uk_platform_wallet_customer",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"customerId"})
public class PlatformWalletEntity extends BaseEntity<PlatformWalletEntity>{
    @ColumnDefine(notNull=true,comment="顾客账号") private Long customerId;
    @ColumnDefine(notNull=true,comment="余额，整数分") private Long balance;
}
