package com.cool.modules.ai.app;

import com.cool.core.annotation.CoolRestController;
import com.cool.core.request.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为登录用户提供只读AI会话和当前会话清除接口
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
@CoolRestController(value="/app/ai/assistant") @RequiredArgsConstructor
public class AppUserAssistantController {
    private final UserAssistantService service;
    @PostMapping("/chat") public R<UserAssistantModels.Reply> chat(@RequestBody UserAssistantModels.Chat input) {
        return R.ok(service.chat(input));
    }
    @DeleteMapping("/session") public R<?> close(@RequestParam String sessionId) {service.close(sessionId);return R.ok();}
}
