package com.cool.modules.ai.controller;

import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.ai.service.CouponAssistantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收优惠券助手对话和明确的执行确认
 * @Version: v1.1
 * @Date: 2026-11-08
 * @Time: 23:18:14
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/ai/assistant/coupon", "/merchant/governance/ai/assistant/coupon"})
public class CouponAssistantController {
    private final CouponAssistantService service;

    @PostMapping("/chat")
    public R<CouponAssistantService.Reply> chat(HttpServletRequest request, @RequestBody CouponAssistantService.Chat input) {
        return R.ok(service.chat(actor(request), platform(request), input));
    }

    @PostMapping("/confirm")
    public R<?> confirm(HttpServletRequest request, @RequestBody Confirmation input) {
        try {
            String result = service.confirm(actor(request), platform(request), input.token());
            return result == null ? R.ok() : R.ok(java.util.Map.of("message", result));
        } catch (CoolException blocked) {
            return R.ok(java.util.Map.of("recovery", service.recoverConfirmation(actor(request), platform(request), input.token(), blocked)));
        }
    }

    @PostMapping("/detail")
    public R<CouponAssistantService.Reply> detail(HttpServletRequest request, @RequestBody CouponAssistantService.Chat input) {
        return R.ok(service.detail(actor(request), platform(request), input));
    }

    public record Confirmation(String token) {}

    private boolean platform(HttpServletRequest request) {
        return request.getServletPath().startsWith("/admin/") || request.getRequestURI().startsWith("/admin/");
    }

    private Long actor(HttpServletRequest request) {
        if (platform(request)) return CoolSecurityUtil.getCurrentUserId();
        if (request.getAttribute("merchantUserId") instanceof Long id) return id;
        throw new CoolException("请先登录", 401);
    }
}
