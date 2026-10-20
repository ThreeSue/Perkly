package com.cool.modules.merchant.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.core.enums.Apis;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.service.MerchantCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import static com.cool.modules.merchant.entity.table.MerchantCategoryEntityTableDef.MERCHANT_CATEGORY_ENTITY;

/**
 * 商家分类
 */
@Tag(name = "商家分类", description = "商家分类")
@CoolRestController(api = {Apis.ADD, Apis.DELETE, Apis.UPDATE, Apis.PAGE, Apis.LIST, Apis.INFO})
public class AdminMerchantCategoryController extends BaseController<MerchantCategoryService, MerchantCategoryEntity> {
    @Override
    protected void init(HttpServletRequest request, JSONObject requestParams) {
        setPageOption(createOp().fieldEq(MERCHANT_CATEGORY_ENTITY.STATUS,
                MERCHANT_CATEGORY_ENTITY.SHOW_ON_FRONT)
                .keyWordLikeFields(MERCHANT_CATEGORY_ENTITY.NAME));
        setListOption(createOp().fieldEq(MERCHANT_CATEGORY_ENTITY.STATUS,
                MERCHANT_CATEGORY_ENTITY.SHOW_ON_FRONT)
                .keyWordLikeFields(MERCHANT_CATEGORY_ENTITY.NAME));
    }
}
