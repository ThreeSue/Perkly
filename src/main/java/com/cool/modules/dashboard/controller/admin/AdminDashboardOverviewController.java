package com.cool.modules.dashboard.controller.admin;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 仅向运营账号提供首页业务统计接口
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 23:40:24
 */
@RequiredArgsConstructor
@Tag(name = "运营首页看板")
@CoolRestController(api = {"info"})
public class AdminDashboardOverviewController {
    private final DashboardService dashboard;

    @GetMapping("/info")
    @Operation(summary = "读取运营业务概览和近十四天新增趋势")
    public R<?> info() {
        if (CoolSecurityUtil.getCurrentUserType() != UserTypeEnum.ADMIN) throw new CoolException("请使用运营账号查看平台看板", 403);
        return R.ok(dashboard.overview());
    }
}
