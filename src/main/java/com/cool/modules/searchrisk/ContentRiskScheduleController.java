package com.cool.modules.searchrisk;
import com.cool.core.request.R;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 可审计的内容风险词库与增量巡检
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 16:24:42
 */

@RestController @RequestMapping("/admin/content-risk/schedule") @lombok.RequiredArgsConstructor
public class ContentRiskScheduleController {
 private final ContentRiskScheduleState state;
 @GetMapping public R<?> info(){return R.ok(state.view());}
 @PostMapping("/save") public R<?> save(@RequestBody ContentRiskScheduleState.Config config){return R.ok(state.configure(config));}
}

