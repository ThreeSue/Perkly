package com.cool.modules.merchant.controller.app;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.annotation.TokenIgnore;
import com.cool.core.request.PageResult;
import com.cool.core.request.R;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.cool.modules.merchant.location.MerchantLocationService;
import com.cool.modules.merchant.location.LocationModels.NearbyQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供用户端门店列表筛选及公开详情接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:04:27
 */
@RequiredArgsConstructor
@Tag(name = "用户端门店展示")
@CoolRestController(api = {"page", "info"})
public class AppMerchantStoreController {
    private final AppDiscoveryService discovery;
    private final MerchantLocationService locations;

    @TokenIgnore
    @Operation(summary = "按GCJ-02坐标查询附近营业门店，距离单位为米")
    @PostMapping("/nearby")
    public R<?> nearby(@RequestBody NearbyQuery input) {
        return R.ok(locations.nearby(input));
    }

    @TokenIgnore
    @Operation(summary = "公开门店分页查询")
    @PostMapping("/page")
    public R<PageResult<Map<String, Object>>> page(@RequestBody(required = false) DiscoveryPageReq req) {
        return R.ok(discovery.stores(req == null ? new DiscoveryPageReq() : req));
    }

    @TokenIgnore
    @Operation(summary = "公开门店详情")
    @GetMapping("/info")
    public R<Map<String, Object>> info(@RequestParam Long id) {
        return R.ok(discovery.store(id));
    }
}
