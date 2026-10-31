package com.cool.modules.home.entity;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mybatisflex.annotation.Table;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存用户首页轮播广告的图片跳转排序及展示时间
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:14:03
 */
@Getter
@Setter
@Table(value = "home_banner", comment = "首页轮播广告")
public class HomeBannerEntity extends BaseEntity<HomeBannerEntity> {
    @ColumnDefine(comment = "广告名称", length = 100, notNull = true)
    private String title;
    @ColumnDefine(comment = "图片地址", length = 1000, notNull = true)
    private String imageUrl;
    @ColumnDefine(comment = "跳转类型NONE STORE COUPON WEB", length = 20, notNull = true)
    private String jumpType;
    @ColumnDefine(comment = "目标ID或网页地址", length = 1000)
    private String jumpTarget;
    @ColumnDefine(comment = "排序，越小越靠前", notNull = true, defaultValue = "0")
    private Integer sort;
    @ColumnDefine(comment = "0停用1启用", notNull = true, defaultValue = "0")
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    @ColumnDefine(comment = "展示开始时间，空表示不限")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    @ColumnDefine(comment = "展示结束时间，空表示不限")
    private Date endTime;
}
