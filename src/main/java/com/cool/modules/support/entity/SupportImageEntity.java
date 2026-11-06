package com.cool.modules.support.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录反馈与聊天图片的上传账号归属
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:34
 */
@Getter @Setter @Table("support_image")
public class SupportImageEntity extends BaseEntity<SupportImageEntity> {
    @ColumnDefine(notNull=true,comment="APP或WORKSPACE") private String ownerKind;
    @ColumnDefine(notNull=true,comment="上传者账号") private Long ownerId;
    @ColumnDefine(length=1000,notNull=true,comment="上传图片地址") private String url;
}
