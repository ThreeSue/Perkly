package com.cool.modules.coupon.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.util.Date;
import lombok.Data;
import org.dromara.autotable.annotation.Ignore;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 映射既有渠道发布记录以便审批中心用实体查询
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@Data
@Ignore
@Table("coupon_publication")
public class CouponPublicationEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private Long merchantId;
    private Long couponTemplateId;
    private String channel;
    private Integer auditStatus;
    private Integer publishStatus;
    private Integer submittedVersion;
    private Integer publishedVersion;
    private String submittedSnapshot;
    private String publishedSnapshot;
    private Date submittedAt;
    private Date publishedAt;
}
