package com.cool.modules.promotion.controller;
import com.cool.core.request.R;
import com.cool.modules.coupon.controller.app.AppOrderController;
import com.cool.modules.promotion.service.StampProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 仅按当前登录顾客查询集章活动和本人进度
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:50:57
 */
@RestController @RequiredArgsConstructor @RequestMapping("/app/promotions/stamps")
public class AppStampController {
    private final StampProgressService progress;
    @GetMapping("/page") public R<com.cool.modules.support.service.UserFeedbackService.PageResult<StampProgressService.CustomerActivity>> page(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        return R.ok(progress.customerList(AppOrderController.customer(),pageNumber,pageSize));
    }
}
