package com.cool.modules.home.controller.app;

import com.cool.core.annotation.TokenIgnore;
import com.cool.core.request.R;
import com.cool.modules.home.service.HomeBannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为用户端预留无需登录的有效首页轮播广告查询接口
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:15:34
 */
@RestController
@TokenIgnore
@RequestMapping("/app/home/banner")
@RequiredArgsConstructor
public class AppHomeBannerController {
    private final HomeBannerService banners;
    @GetMapping("/list") public R<?> list() { return R.ok(banners.publicList()); }
}
