package com.cool.modules.searchrisk;
import com.cool.core.request.R;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

@RestController @RequestMapping("/admin/content-risk/jobs") @lombok.RequiredArgsConstructor
public class ContentRiskJobController {
 private final ContentRiskJobs jobs;
 public record Id(Long id){}
 @PostMapping("/start") public R<?> start(@RequestBody ContentRiskJobs.Start p){return R.ok(jobs.start(p));}
 @GetMapping("/status") public R<?> status(@RequestParam(required=false) Long id){return R.ok(jobs.status(id));}
 @PostMapping("/retry") public R<?> retry(@RequestBody Id p){return R.ok(jobs.retry(p.id()));}
}

