package com.cool.modules.merchant.report;
import com.cool.core.request.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 门店核销报表独立业务组件
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 08:40:09
 */
@RestController @RequestMapping("/merchant/reports/redemptions") @RequiredArgsConstructor
public class MerchantReportController {
 private final MerchantReportService service;
 @GetMapping("/operators") public R<?> operators(@RequestAttribute("merchantUserId") Long actor){return R.ok(service.operators(actor));}
 @PostMapping("/query") public R<?> query(@RequestAttribute("merchantUserId") Long actor,@RequestBody MerchantReportModels.Filter input){return R.ok(service.query(actor,input));}
}
