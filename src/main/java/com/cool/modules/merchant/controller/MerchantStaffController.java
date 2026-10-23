package com.cool.modules.merchant.controller;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.modules.merchant.service.MerchantStaffService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供店长和员工的当前商家门店及角色信息
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 10:28:14
 */
@RestController @RequestMapping("/merchant/staff") @RequiredArgsConstructor
public class MerchantStaffController {
    private final MerchantStaffService staff;
    @GetMapping("/me") public R<?> me(HttpServletRequest request) {
        Object id = request.getAttribute("merchantUserId");
        if (!(id instanceof Long)) throw new CoolException("请先登录",401);
        return R.ok(staff.me((Long)id));
    }
}
