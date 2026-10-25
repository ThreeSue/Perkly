package com.cool.modules.approval.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mybatisflex.annotation.*;
import java.util.Date;
import lombok.Data;
import org.dromara.autotable.annotation.Ignore;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存游客入驻手机号归属及审批后一次性商家开通凭证
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:03:08
 */
@Data
@Ignore
@Table("merchant_activation")
public class MerchantActivationEntity {
    @Id(keyType=KeyType.Auto) private Long id;
    private Date createTime;
    private String applicationNo;
    private String phone;
    private String requestId;
    @JsonIgnore private String codeHash;
    @JsonIgnore private String codeCipher;
    private Date expiresAt;
    private Date activatedAt;
    private Long accountId;
}
