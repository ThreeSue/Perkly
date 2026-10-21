package com.cool.modules.approval.entity;

import com.mybatisflex.annotation.Id;

import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import lombok.Data;
import org.dromara.autotable.annotation.Ignore;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存每一轮商家入驻资料及审核结果
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@Data
@Ignore
@Table("merchant_application")
public class MerchantApplicationEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private Date createTime;
    private Long merchantId;
    private String applicationNo;
    private Integer submissionNo;
    private String username;
    @JsonIgnore private String passwordHash;
    private String merchantName;
    private String snapshot;
    private Integer status;
    private Integer currentFlag;
    private String submittedBy;
    private Date submittedAt;
}
