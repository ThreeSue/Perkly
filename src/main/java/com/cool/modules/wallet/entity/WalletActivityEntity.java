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
 * @Description: 储值卡支付优惠活动申请及发布快照
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:45:37
 */
@Getter @Setter @Table("wallet_activity")

public class WalletActivityEntity extends BaseEntity<WalletActivityEntity>{
    @ColumnDefine(comment="商家申请归属，平台全局活动为空") private Long merchantId;
    @ColumnDefine(notNull=true,comment="创建人") private Long creatorId;
    @ColumnDefine(length=100,notNull=true,comment="活动标题") private String title;
    @ColumnDefine(type="text",comment="纯文字活动说明") private String description;
    @ColumnDefine(notNull=true,comment="PENDING或PUBLISHED或REJECTED或OFFLINE") private String status;
    @ColumnDefine(comment="运营设定折扣，9表示九折") private BigDecimal discountRate;
    @ColumnDefine(notNull=true,comment="生效时间") private Date startAt;
    @ColumnDefine(notNull=true,comment="截止时间，不含") private Date endAt;
    @ColumnDefine(type="json",comment="参与门店，平台空列表表示全部正常门店")
    @Column(typeHandler=com.cool.core.mybatis.handler.Fastjson2TypeHandler.class) private List<Long> storeIds;
    @ColumnDefine(comment="审核发布运营") private Long reviewedBy;
    @ColumnDefine(comment="审核时间") private Date reviewedAt;
    @ColumnDefine(length=500,comment="审核备注") private String reviewNote;
}
