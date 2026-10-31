package com.cool.modules.coupon.controller.admin;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.coupon.service.PurchaseOrderReadService;
import com.cool.modules.coupon.controller.admin.req.PurchaseOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 仅允许运营授权账号查询购券订单分页和详情
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */

@CoolRestController(api={"page","info"},cname="purchaseOrder") @RequiredArgsConstructor
public class AdminPurchaseOrderController {
    private final PurchaseOrderReadService orders;
    private void access(String action){
        if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN)throw new CoolException("请使用运营账号",403);
        if(!CoolSecurityUtil.isSuperAdmin() && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().noneMatch(a->("coupon:purchaseOrder:"+action).equals(a.getAuthority())))throw new CoolException("没有购券订单查询权限",403);
    }
    @PostMapping("/page") public R<?> page(@RequestBody PurchaseOrderQuery input){access("page");return R.ok(orders.page(input));}
    @GetMapping("/info") public R<?> info(@RequestParam Long id){access("info");return R.ok(orders.info(id));}
}
