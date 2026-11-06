package com.cool.modules.ai.controller;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为运营及商家表单提供AI提示词任务历史选用和仅素材保存重试接口
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:52:24
 */
@RestController @RequiredArgsConstructor
@RequestMapping({"/admin/ai/images","/merchant/governance/ai/images"})
public class AiImageController {
    private final AiAccessService access;
    private final AiTaskService tasks;
    private final AiTaskRunner runner;
    private final HttpServletRequest request;
    private boolean platform(){return request.getRequestURI().startsWith("/admin/");}
    private Long actor(){
        if(platform())return CoolSecurityUtil.getCurrentUserId();
        if(request.getAttribute("merchantUserId") instanceof Long id)return id;
        throw new CoolException("请先登录",401);
    }
    @io.swagger.v3.oas.annotations.media.Schema(name="AiImageAction")
    public record Action(Long merchantId,Long couponId,Long id) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiImagePrompt")
    public record Prompt(String prompt,String merchantName,java.util.List<String> storeNames) {}
    @PostMapping("/prompt") public R<Prompt> prompt(@RequestBody AiAccessService.Draft draft){
        var result=access.prepare(actor(),platform(),draft);return R.ok(new Prompt(result.suggestedPrompt(),result.merchantName(),result.storeNames()));
    }
    @GetMapping("/quota") public R<AiTaskService.Quota> quota(@RequestParam Long merchantId){return R.ok(tasks.quota(actor(),platform(),merchantId));}
    @PostMapping("/create") public R<AiTaskService.TaskView> create(@RequestBody AiTaskService.Create input){return R.ok(tasks.create(actor(),platform(),input));}
    @GetMapping("/info") public R<AiTaskService.TaskView> info(@RequestParam Long merchantId,@RequestParam Long id){return R.ok(tasks.info(actor(),platform(),merchantId,id));}
    @GetMapping("/page") public R<com.cool.modules.support.service.UserFeedbackService.PageResult<AiTaskService.TaskView>> page(@RequestParam Long merchantId,@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        return R.ok(tasks.page(actor(),platform(),merchantId,pageNumber,pageSize));
    }
    @PostMapping("/select") public R<AiTaskService.TaskView> select(@RequestBody Action action){return R.ok(tasks.select(actor(),platform(),action.merchantId(),action.couponId(),action.id()));}
    @GetMapping("/materials") public R<com.cool.modules.support.service.UserFeedbackService.PageResult<com.cool.modules.space.service.MerchantMaterialService.Material>> materials(@RequestParam Long merchantId,@RequestParam(defaultValue="1") int pageNumber,
        @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long categoryId,
        @RequestParam(required=false) String source){
        return R.ok(tasks.materials(actor(),platform(),merchantId,pageNumber,pageSize,keyword,categoryId,source));
    }
    @GetMapping("/categories") public R<java.util.List<com.cool.modules.space.service.MerchantMaterialService.Category>> categories(@RequestParam Long merchantId){return R.ok(tasks.categories(actor(),platform(),merchantId));}
    @PostMapping("/retry-storage") public R<AiTaskService.TaskView> retry(@RequestBody Action action){
        var task=tasks.info(actor(),platform(),action.merchantId(),action.id());
        access.context(actor(),platform(),action.merchantId(),action.couponId());
        if(!java.util.Set.of("GENERATED","STORAGE_FAILED","STORED").contains(task.status()))throw new CoolException("此任务不能重试保存，结果未知不会自动重新生成",409);
        runner.store(action.id());return R.ok(tasks.info(actor(),platform(),action.merchantId(),action.id()));
    }
}
