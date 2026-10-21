package com.cool.modules.merchant.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.modules.merchant.entity.MerchantStoreCategoryEntity;
import com.cool.modules.merchant.service.MerchantStoreCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import static com.cool.modules.merchant.entity.table.MerchantStoreCategoryEntityTableDef.MERCHANT_STORE_CATEGORY_ENTITY;

@Tag(name = "门店类型", description = "运营端维护平台公共门店类型")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"}, cname = "storeCategory")
public class AdminMerchantStoreCategoryController
        extends BaseController<MerchantStoreCategoryService, MerchantStoreCategoryEntity> {
    @Override
    protected void init(HttpServletRequest request, JSONObject requestParams) {
        setPageOption(createOp().fieldEq(MERCHANT_STORE_CATEGORY_ENTITY.STATUS)
                .keyWordLikeFields(MERCHANT_STORE_CATEGORY_ENTITY.NAME));
        setListOption(createOp().fieldEq(MERCHANT_STORE_CATEGORY_ENTITY.STATUS)
                .keyWordLikeFields(MERCHANT_STORE_CATEGORY_ENTITY.NAME));
    }
}
