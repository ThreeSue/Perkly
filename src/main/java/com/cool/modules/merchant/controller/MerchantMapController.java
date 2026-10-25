package com.cool.modules.merchant.controller;

import com.cool.core.request.R;
import com.cool.modules.merchant.location.TencentMapService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static com.cool.modules.merchant.location.LocationModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为已登录的运营商家和用户提供统一的腾讯地点搜索和地址解析入口
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:11:44
 */
@RestController
@RequestMapping({"/admin/merchant/map", "/merchant/workspace/map", "/app/merchant/map"})
@RequiredArgsConstructor
public class MerchantMapController {
    private final TencentMapService maps;

    @GetMapping("/suggest")
    public R<PlaceResult> suggest(@RequestParam String keyword, @RequestParam(required = false) String region) {
        return R.ok(maps.suggest(keyword, region));
    }

    @GetMapping("/geocode")
    public R<AddressResult> geocode(@RequestParam String address, @RequestParam(required = false) String region) {
        return R.ok(maps.geocode(address, region));
    }

    @GetMapping("/reverseGeocode")
    public R<AddressResult> reverse(@RequestParam BigDecimal longitude, @RequestParam BigDecimal latitude) {
        return R.ok(maps.reverse(longitude, latitude));
    }
}
