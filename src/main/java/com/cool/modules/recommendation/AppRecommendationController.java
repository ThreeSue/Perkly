package com.cool.modules.recommendation;
import com.cool.core.request.R;
import com.cool.modules.coupon.controller.app.AppOrderController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为当前登录顾客提供个性化门店和优惠券推荐
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:40:00
 */
@RestController @RequiredArgsConstructor @RequestMapping("/app/recommendation")
public class AppRecommendationController {
 private final RecommendationService recommendations;
 @PostMapping("/page") public R<?> page(@RequestBody RecommendationService.Input input) {
  return R.ok(recommendations.recommend(AppOrderController.customer(),input));
 }
}
