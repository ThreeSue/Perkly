package com.cool.modules.profile;

import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.ai.service.AiSettingsService;
import com.cool.modules.support.service.SupportAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 授权运营查看顾客画像与提交异步重算任务
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:35:00
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/profile")
public class AdminProfileController {
    private final ProfileService profiles;
    private final SupportAccess access;
    private final AiSettingsService audit;
    private final com.cool.modules.recommendation.RecommendationService recommendations;
    @org.springframework.beans.factory.annotation.Value("${perkly.profile.worker-enabled:false}")
    private boolean enabled;

    @GetMapping("/info")
    public R<?> info(@RequestParam Long customerId) {
        access.platform("profile:read");
        return R.ok(profiles.view(customerId));
    }

    @PostMapping("/refresh")
    public R<?> refresh(@RequestBody Refresh request) {
        access.platform("profile:update");
        if(!enabled)throw new com.cool.core.exception.CoolException("画像更新服务尚未启用，请完成数据库迁移并开启后台任务",503);
        if(!Boolean.TRUE.equals(audit.current().getEnabled()))throw new com.cool.core.exception.CoolException("AI分析已暂停，请先在AI控制台开启",503);
        profiles.request(request.customerId(), true);
        audit.audit(CoolSecurityUtil.getCurrentUserId(), null, null, "PROFILE_REQUESTED",
            "customerId=" + request.customerId());
        return R.ok(profiles.view(request.customerId()));
    }

    public record Refresh(Long customerId) {}
    public record Correction(Long customerId,java.util.List<String> excludedLabels) {}
    @PostMapping("/correction")
    public R<?> correction(@RequestBody Correction input) {
        access.platform("profile:update");profiles.correct(input.customerId(),input.excludedLabels());
        audit.audit(CoolSecurityUtil.getCurrentUserId(),null,null,"PROFILE_CORRECTED","customerId="+input.customerId());
        return R.ok(profiles.view(input.customerId()));
    }

    @PostMapping("/preview")
    public R<?> preview(@RequestParam Long customerId,@RequestBody com.cool.modules.recommendation.RecommendationService.Input input) {
        access.platform("profile:read");
        return R.ok(recommendations.recommend(customerId,input));
    }
}
