package com.cool.modules.coupon.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.CouponTemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import static com.cool.modules.coupon.entity.table.CouponTemplateEntityTableDef.COUPON_TEMPLATE_ENTITY;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营端优惠券模板增删改查与筛选接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:12:43
 */
@Tag(name = "优惠券模板管理", description = "运营人员代商家配置优惠券")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"})
public class AdminCouponTemplateController extends BaseController<CouponTemplateService, CouponTemplateEntity> {
    @Override
    protected void init(HttpServletRequest request, JSONObject params) {
        setPageOption(createOp().fieldEq(COUPON_TEMPLATE_ENTITY.MERCHANT_ID,
                COUPON_TEMPLATE_ENTITY.TYPE, COUPON_TEMPLATE_ENTITY.STATUS)
                .keyWordLikeFields(COUPON_TEMPLATE_ENTITY.NAME));
        setListOption(createOp().fieldEq(COUPON_TEMPLATE_ENTITY.MERCHANT_ID,
                COUPON_TEMPLATE_ENTITY.TYPE, COUPON_TEMPLATE_ENTITY.STATUS)
                .keyWordLikeFields(COUPON_TEMPLATE_ENTITY.NAME));
    }
}

