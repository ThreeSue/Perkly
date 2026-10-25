package com.cool.modules.merchant.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.service.MerchantStoreService;
import com.cool.modules.merchant.location.MerchantLocationService;
import com.cool.modules.merchant.location.LocationModels.LocationUpdate;
import com.cool.modules.merchant.location.LocationModels.MapQuery;
import com.cool.core.request.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static com.cool.modules.merchant.entity.table.MerchantStoreEntityTableDef.MERCHANT_STORE_ENTITY;

@Tag(name = "门店管理", description = "商家门店资料管理")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"})
@RequiredArgsConstructor
public class AdminMerchantStoreController extends BaseController<MerchantStoreService, MerchantStoreEntity> {
    private final MerchantLocationService locations;

    @PostMapping("/map")
    public R<?> map(@RequestBody(required = false) MapQuery input) {
        return R.ok(locations.adminMap(input == null ? new MapQuery() : input));
    }

    @PostMapping("/location/update")
    public R<?> location(@RequestBody LocationUpdate input) {
        return R.ok(locations.updateAdmin(input));
    }

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
