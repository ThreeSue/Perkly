package com.cool.modules.approval.entity;

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
 * @Description: 保存审批流转的真实操作人时间意见和版本快照
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@Data
@Ignore
@Table("audit_record")
public class AuditRecordEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private Long merchantId;
    private String businessType;
    private Long businessId;
    private Integer submissionNo;
    private Integer version;
    private String action;
    private Long reviewerId;
    private String actorName;
    private String actorType;
    private String reason;
    private String snapshot;
    private Date reviewedAt;
}
