package com.cool.modules.merchant.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.service.MerchantStoreService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import static com.cool.modules.merchant.entity.table.MerchantStoreEntityTableDef.MERCHANT_STORE_ENTITY;

@Tag(name = "门店管理", description = "商家门店资料管理")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"})
public class AdminMerchantStoreController extends BaseController<MerchantStoreService, MerchantStoreEntity> {
    @Override
    protected void init(HttpServletRequest request, JSONObject requestParams) {
        setPageOption(createOp().fieldEq(MERCHANT_STORE_ENTITY.MERCHANT_ID,
                MERCHANT_STORE_ENTITY.CATEGORY_ID, MERCHANT_STORE_ENTITY.STATUS)
                .keyWordLikeFields(MERCHANT_STORE_ENTITY.NAME, MERCHANT_STORE_ENTITY.PHONE,
                        MERCHANT_STORE_ENTITY.ADDRESS));
        setListOption(createOp().fieldEq(MERCHANT_STORE_ENTITY.MERCHANT_ID,
                MERCHANT_STORE_ENTITY.CATEGORY_ID, MERCHANT_STORE_ENTITY.STATUS));
    }
}
