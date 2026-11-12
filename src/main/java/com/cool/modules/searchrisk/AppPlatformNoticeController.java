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

@RestController @RequestMapping("/app/platform/notices") @lombok.RequiredArgsConstructor
public class AppPlatformNoticeController {
 private final PlatformWarningService service;
 public record Id(Long id){}
 @GetMapping("/page") public R<?> page(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(service.page(pageNumber,pageSize,false));}
 @GetMapping("/pending") public R<?> pending(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(service.page(pageNumber,pageSize,true));}
 @GetMapping("/detail") public R<?> detail(@RequestParam Long id){return R.ok(service.detail(id));}
 @GetMapping("/unread") public R<?> unread(){return R.ok(service.unread());}
 @PostMapping("/read") public R<?> read(@RequestBody Id p){return R.ok(service.read(p.id(),false));}
 @PostMapping("/confirm") public R<?> confirm(@RequestBody Id p){return R.ok(service.read(p.id(),true));}
}

