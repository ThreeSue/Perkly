package com.cool.modules.promotion.controller;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.promotion.service.*;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营集章活动配置审核奖励和进度监管接口
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:50:56
 */
@RestController @RequiredArgsConstructor @RequestMapping("/admin/promotions/stamps")
public class AdminStampController {
    private final StampCampaignService campaigns;
    private final StampProgressService progress;
    public record Id(Long id) {}
    @GetMapping("/options") public R<?> options(){return R.ok(campaigns.options());}
    @GetMapping("/rewards") public R<?> rewards(){return R.ok(campaigns.rewards());}
    @PostMapping("/rewards/create") public R<?> make(@RequestBody CouponTemplateEntity input){return R.ok(campaigns.makeReward(input));}
    @PostMapping("/rewards/review") public R<?> review(@RequestBody StampCampaignService.RewardReview input){return R.ok(campaigns.reviewReward(input));}
    @GetMapping("/page") public R<com.cool.modules.support.service.UserFeedbackService.PageResult<StampCampaignService.View>> page(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        return R.ok(campaigns.page(pageNumber,pageSize));
    }
    @GetMapping("/info") public R<StampCampaignService.View> info(@RequestParam Long id){campaigns.permission();return R.ok(campaigns.view(id));}
    @PostMapping("/save") public R<?> save(@RequestBody StampCampaignService.Input input){return R.ok(campaigns.save(CoolSecurityUtil.getCurrentUserId(),input));}
    @PostMapping("/publish") public R<?> publish(@RequestBody StampCampaignService.Command input){campaigns.publish(CoolSecurityUtil.getCurrentUserId(),input);return R.ok();}
    @PostMapping("/schedule") public R<?> schedule(@RequestBody StampCampaignService.Schedule input){campaigns.schedule(input);return R.ok();}
    @PostMapping("/close") public R<?> close(@RequestBody StampCampaignService.Command input){campaigns.close(input);return R.ok();}
    @PostMapping("/stock") public R<?> stock(@RequestBody StampCampaignService.Stock input){campaigns.stock(CoolSecurityUtil.getCurrentUserId(),input);return R.ok();}
    @GetMapping("/progress") public R<com.cool.modules.support.service.UserFeedbackService.PageResult<StampProgressService.ProgressView>> progress(@RequestParam Long id,@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        return R.ok(progress.records(id,pageNumber,pageSize));
    }
    @GetMapping("/attempts") public R<?> attempts(@RequestParam Long id){return R.ok(progress.attempts(id));}
    @PostMapping("/retry") public R<?> retry(@RequestBody Id input){progress.retryGrant(input.id(),true);return R.ok();}
}
