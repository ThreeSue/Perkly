package com.cool.modules.support.controller;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.support.service.UserFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供平台反馈列表与单次处理回复接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:42
 */
@RestController @RequiredArgsConstructor @RequestMapping("/admin/support/feedback")
public class AdminFeedbackController {
    private final UserFeedbackService feedback;
    @GetMapping("/page") public R<?> page(@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(feedback.page(null,status,pageNumber,pageSize));}
    @GetMapping("/detail") public R<?> detail(@RequestParam Long id){return R.ok(feedback.detail(null,id));}
    @PostMapping("/reply") public R<?> reply(@RequestBody UserFeedbackService.Reply p){feedback.reply(CoolSecurityUtil.getCurrentUserId(),p);return R.ok();}
}
