package com.cool.modules.home.controller.admin;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.home.entity.HomeBannerEntity;
import com.cool.modules.home.service.HomeBannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import static com.cool.modules.home.model.HomeBannerModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按运营菜单权限提供首页广告增删改查启停和跳转目标选择
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:15:34
 */
@RestController
@RequestMapping("/admin/home/banner")
@RequiredArgsConstructor
public class AdminHomeBannerController {
    private final HomeBannerService banners;
    private void permit(String action) {
        if (CoolSecurityUtil.isSuperAdmin()) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("home:banner:" + action)))
            throw new CoolException("没有首页广告的操作权限", 403);
    }
    @PostMapping("/page") public R<?> page(@RequestBody PageInput input) { permit("page"); return R.ok(banners.page(input)); }
    @GetMapping("/info") public R<?> info(@RequestParam Long id) { permit("info"); return R.ok(banners.info(id)); }
    @PostMapping("/add") public R<?> add(@RequestBody HomeBannerEntity input) { permit("add"); return R.ok(banners.add(input)); }
    @PostMapping("/update") public R<?> update(@RequestBody HomeBannerEntity input) { permit("update"); return R.ok(banners.update(input)); }
    @PostMapping("/status") public R<?> status(@RequestBody StatusInput input) { permit("status"); return R.ok(banners.status(input)); }
    @PostMapping("/delete") public R<?> delete(@RequestBody DeleteInput input) { permit("delete"); return R.ok(banners.delete(input.ids())); }
    @GetMapping("/targetOptions") public R<?> options(@RequestParam String type, @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer page) {
        permit("targetOptions"); return R.ok(banners.targetOptions(type, keyword, page));
    }
}
