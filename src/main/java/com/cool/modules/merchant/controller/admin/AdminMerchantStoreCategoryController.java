package com.cool.modules.merchant.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.service.MerchantStoreCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import static com.cool.modules.merchant.entity.table.MerchantStoreCategoryEntityTableDef.MERCHANT_STORE_CATEGORY_ENTITY;

@Tag(name = "门店分类", description = "商家门店分类管理")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"}, cname = "storeCategory")
public class AdminMerchantStoreCategoryController
        extends BaseController<MerchantStoreCategoryService, MerchantStoreCategoryEntity> {
    @Override
    protected void init(HttpServletRequest request, JSONObject requestParams) {
        setPageOption(createOp().fieldEq(MERCHANT_STORE_CATEGORY_ENTITY.MERCHANT_ID,
                MERCHANT_STORE_CATEGORY_ENTITY.PARENT_ID, MERCHANT_STORE_CATEGORY_ENTITY.STATUS,
                MERCHANT_STORE_CATEGORY_ENTITY.SHOW_ON_FRONT)
                .keyWordLikeFields(MERCHANT_STORE_CATEGORY_ENTITY.NAME));
        setListOption(createOp().fieldEq(MERCHANT_STORE_CATEGORY_ENTITY.MERCHANT_ID,
                MERCHANT_STORE_CATEGORY_ENTITY.PARENT_ID, MERCHANT_STORE_CATEGORY_ENTITY.STATUS,
                MERCHANT_STORE_CATEGORY_ENTITY.SHOW_ON_FRONT)
                .keyWordLikeFields(MERCHANT_STORE_CATEGORY_ENTITY.NAME));
    }
}
