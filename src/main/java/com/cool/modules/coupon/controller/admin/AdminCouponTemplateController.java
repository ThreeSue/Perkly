package com.cool.modules.coupon.controller.admin;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.service.CouponTemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.coupon.controller.admin.params.CouponPublicationParam;
import com.cool.modules.coupon.service.CouponPublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static com.cool.modules.coupon.entity.table.CouponTemplateEntityTableDef.COUPON_TEMPLATE_ENTITY;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营端优惠券模板增删改查与筛选接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:12:43
 */
@Tag(name = "优惠券模板管理", description = "运营人员代商家配置优惠券")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"})
@RequiredArgsConstructor
public class AdminCouponTemplateController extends BaseController<CouponTemplateService, CouponTemplateEntity> {
    private final CouponPublicationService publications;

    @PostMapping("/submitAudit")
    public R<?> submitAudit(@Valid @RequestBody CouponPublicationParam req) {
        requirePublicationPermission();
        return R.ok(publications.submitAudit(req.getId(), req.getVersion()));
    }

    @PostMapping("/withdrawAudit")
    public R<?> withdrawAudit(@Valid @RequestBody CouponPublicationParam req) {
        requirePublicationPermission();
        return R.ok(publications.withdrawAudit(req.getId(), req.getVersion()));
    }

    @PostMapping("/audit")
    public R<?> audit(@Valid @RequestBody CouponPublicationParam req) {
        requirePublicationPermission();
        return R.ok(publications.audit(req.getId(), req.getVersion(), req.getApproved()));
    }

    @PostMapping("/publish")
    public R<?> publish(@Valid @RequestBody CouponPublicationParam req) {
        requirePublicationPermission();
        return R.ok(publications.publish(req.getId(), req.getVersion()));
    }

    @PostMapping("/unpublish")
    public R<?> unpublish(@Valid @RequestBody CouponPublicationParam req) {
        requirePublicationPermission();
        return R.ok(publications.unpublish(req.getId(), req.getVersion()));
    }

    private void requirePublicationPermission() {
        // 复用模板编辑权限；显式校验防止尚未登记菜单的新接口被默认放行。
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.ADMIN) throw new CoolException("请使用运营账号操作", 403);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!CoolSecurityUtil.isSuperAdmin() && authentication.getAuthorities().stream()
                .noneMatch(authority -> "coupon:template:update".equals(authority.getAuthority()))) {
            throw new CoolException("没有优惠券管理权限", 403);
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/productOptions")
    @io.swagger.v3.oas.annotations.Operation(summary = "获取所选商家的可用商品服务项目")
    public com.cool.core.request.R<?> productOptions(@org.springframework.web.bind.annotation.RequestParam Long merchantId) {
        return com.cool.core.request.R.ok(service.productOptions(merchantId));
    }

    @Override
    protected void init(HttpServletRequest request, JSONObject params) {
        setPageOption(createOp().fieldEq(COUPON_TEMPLATE_ENTITY.MERCHANT_ID,
                COUPON_TEMPLATE_ENTITY.TYPE, COUPON_TEMPLATE_ENTITY.STATUS)
                .keyWordLikeFields(COUPON_TEMPLATE_ENTITY.NAME));
        setListOption(createOp().fieldEq(COUPON_TEMPLATE_ENTITY.MERCHANT_ID,
                COUPON_TEMPLATE_ENTITY.TYPE, COUPON_TEMPLATE_ENTITY.STATUS)
                .keyWordLikeFields(COUPON_TEMPLATE_ENTITY.NAME));
    }
}

