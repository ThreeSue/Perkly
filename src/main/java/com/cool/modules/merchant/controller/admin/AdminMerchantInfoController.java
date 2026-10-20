package com.cool.modules.merchant.controller.admin;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.core.request.CrudOption;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.service.MerchantInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import com.mybatisflex.core.query.QueryWrapper;

import static com.cool.modules.merchant.entity.table.MerchantInfoEntityTableDef.MERCHANT_INFO_ENTITY;

@Tag(name = "商家管理", description = "平台运营端商家资料管理")
@CoolRestController(api = {"add", "update", "page", "list", "info"})
public class AdminMerchantInfoController extends BaseController<MerchantInfoService, MerchantInfoEntity> {
    @Override
    protected void init(HttpServletRequest request, JSONObject requestParams) {
        setPageOption(queryOption(requestParams));
        setListOption(queryOption(requestParams));
    }

    private CrudOption<MerchantInfoEntity> queryOption(JSONObject params) {
        CrudOption<MerchantInfoEntity> option = createOp()
                .keyWordLikeFields(MERCHANT_INFO_ENTITY.NAME, MERCHANT_INFO_ENTITY.CONTACT_PHONE);
        if (params.get("categoryId") instanceof JSONArray ids) {
            // The framework's array converter nests the list; build this module's IN filter directly.
            option.fieldEq(MERCHANT_INFO_ENTITY.STATUS);
            if (!ids.isEmpty()) {
                option.queryWrapper(QueryWrapper.create()
                        .where(MERCHANT_INFO_ENTITY.CATEGORY_ID.in(ids.toList(Long.class))));
            }
        } else {
            option.fieldEq(MERCHANT_INFO_ENTITY.CATEGORY_ID, MERCHANT_INFO_ENTITY.STATUS);
        }
        return option;
    }
}
