package com.cool.modules.coupon.controller.admin;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.coupon.service.ConsumptionOrderService;
import com.cool.modules.coupon.service.ConsumptionOrderService.PageInput;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供受运营权限保护的消费账单分页及用券详情查询
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 10:35:36
 */
@CoolRestController(api={"page","info"}, cname="consumptionOrder") @RequiredArgsConstructor
public class AdminConsumptionOrderController {
    private final ConsumptionOrderService orders;
    private void access(String action) {
        if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN) throw new CoolException("请使用运营账号",403);
        if(!CoolSecurityUtil.isSuperAdmin() && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .noneMatch(a->("coupon:consumptionOrder:"+action).equals(a.getAuthority()))) throw new CoolException("没有消费账单查询权限",403);
    }
    @PostMapping("/page") public R<?> page(@RequestBody PageInput p) {access("page");return R.ok(orders.adminPage(p));}
    @GetMapping("/info") public R<?> info(@RequestParam Long id) {access("info");return R.ok(orders.adminInfo(id));}
}
