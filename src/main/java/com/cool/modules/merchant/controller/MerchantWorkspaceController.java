package com.cool.modules.merchant.controller;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.service.MerchantWorkspaceService;
import com.cool.modules.merchant.location.MerchantLocationService;
import com.cool.modules.merchant.location.LocationModels.MapQuery;
import com.cool.modules.merchant.location.LocationModels.LocationUpdate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 暴露独立商家工作区接口并从已认证请求取得账号身份
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 20:57:31
 */
@RestController
@RequestMapping("/merchant/workspace")
@RequiredArgsConstructor
public class MerchantWorkspaceController {
    private final MerchantWorkspaceService workspace;
    private final com.cool.modules.merchant.service.MerchantAccountService accounts;
    private final HttpServletRequest request;
    private final MerchantLocationService locations;
    private Long user() {
        Object id = request.getAttribute("merchantUserId");
        if (!(id instanceof Long)) throw new CoolException("请使用商家账号登录", 401);
        return (Long) id;
    }
    public record PageInput(Integer page, Integer size, String keyWord) {
        int pageNumber() { return page == null ? 1 : page; }
        int pageSize() { return size == null ? 20 : size; }
    }
    public record IdsInput(List<Long> ids) {}
    public record PublicationInput(Long id, Integer version, String action) {}

    @GetMapping("/me") public R<?> me() {
        if (request.getAttribute("merchantApplication") instanceof com.cool.modules.approval.entity.MerchantApplicationEntity application)
            return R.ok(accounts.applicantInfo(application));
        return R.ok(workspace.me(user()));
    }
    @GetMapping("/profile") public R<?> profile() { return R.ok(workspace.profile(user())); }
    @PostMapping("/store/page") public R<?> stores(@RequestBody PageInput p) { return R.ok(workspace.storePage(user(), p.pageNumber(), p.pageSize(), p.keyWord())); }
    @GetMapping("/store/info") public R<?> store(@RequestParam Long id) { return R.ok(workspace.storeInfo(user(), id)); }
    @GetMapping("/store/options") public R<?> storeOptions() { return R.ok(workspace.storeOptions(user())); }
    @PostMapping("/store/map") public R<?> storeMap(@RequestBody(required = false) MapQuery input) {
        return R.ok(locations.merchantMap(user(), input == null ? new MapQuery() : input));
    }
    @PostMapping("/store/location/update") public R<?> storeLocation(@RequestBody LocationUpdate input) {
        return R.ok(locations.updateMerchant(user(), input));
    }
    @PostMapping("/store/save") public R<?> saveStore(@RequestBody MerchantStoreEntity input) { return R.ok(workspace.storeSave(user(), input)); }
    @PostMapping("/store/delete") public R<?> deleteStore(@RequestBody IdsInput p) { return R.ok(workspace.storeDelete(user(), p.ids())); }
    @GetMapping("/category/options") public R<?> categories() { return R.ok(workspace.categoryOptions(user())); }
    @PostMapping("/coupon/page") public R<?> coupons(@RequestBody PageInput p) { return R.ok(workspace.couponPage(user(), p.pageNumber(), p.pageSize(), p.keyWord())); }
    @GetMapping("/coupon/info") public R<?> coupon(@RequestParam Long id) { return R.ok(workspace.couponInfo(user(), id)); }
    @PostMapping("/coupon/save") public R<?> saveCoupon(@RequestBody CouponTemplateEntity input) { return R.ok(workspace.couponSave(user(), input)); }
    @PostMapping("/coupon/delete") public R<?> deleteCoupon(@RequestBody IdsInput p) { return R.ok(workspace.couponDelete(user(), p.ids())); }
    @PostMapping("/coupon/publication") public R<?> publication(@RequestBody PublicationInput p) { return R.ok(workspace.publication(user(), p.id(), p.version(), p.action())); }
    @GetMapping("/product/options") public R<?> products() { return R.ok(workspace.products(user())); }
    @PostMapping("/order/page") public R<?> orders(@RequestBody PageInput p) { return R.ok(workspace.orderPage(user(), p.pageNumber(), p.pageSize())); }
    @GetMapping("/order/info") public R<?> order(@RequestParam Long id) { return R.ok(workspace.orderInfo(user(), id)); }
}
