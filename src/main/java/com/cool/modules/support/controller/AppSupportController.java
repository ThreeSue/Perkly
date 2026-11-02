package com.cool.modules.support.controller;
import com.cool.core.request.R;
import com.cool.modules.coupon.controller.app.AppOrderController;
import com.cool.modules.support.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供顾客提交反馈查询结果及图片上传接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:41
 */
@RestController @RequiredArgsConstructor @RequestMapping("/app/support")
public class AppSupportController {
    private final UserFeedbackService feedback;
    private final SupportImageService images;
    private final SupportAccess access;
    @PostMapping("/feedback/submit") public R<?> submit(@RequestBody UserFeedbackService.Submit p){return R.ok(feedback.submit(AppOrderController.customer(),p));}
    @GetMapping("/feedback/page") public R<?> page(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(feedback.page(AppOrderController.customer(),null,pageNumber,pageSize));}
    @GetMapping("/feedback/detail") public R<?> detail(@RequestParam Long id){return R.ok(feedback.detail(AppOrderController.customer(),id));}
    @PostMapping(value="/images/upload",consumes="multipart/form-data") public R<?> upload(@RequestPart("file") MultipartFile file,HttpServletRequest request){
        Long id=AppOrderController.customer();access.customer(id);return R.ok(images.upload("APP",id,file,request));
    }
}
