package com.cool.modules.ai.management;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 暴露管理助手限定模块对话预览确认及取消接口
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@RestController @RequiredArgsConstructor
@RequestMapping({"/admin/ai/assistant/workbench","/merchant/governance/ai/assistant/workbench"})
public class ManagementAssistantController {
    private final ManagementAssistantService service;
    private final ManagementAssistantModel model;
    private final ManagementAssistantAccess access;
    private Actor actor(HttpServletRequest request) {
        if(request.getRequestURI().startsWith("/admin/")) {
            if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN)throw new CoolException("请使用运营账号",403);
            return new Actor(CoolSecurityUtil.getCurrentUserId(),true);
        }
        if(request.getAttribute("merchantUserId") instanceof Long id)return new Actor(id,false);
        throw new CoolException("请先登录",401);
    }
    @PostMapping("/chat") public R<Reply> chat(HttpServletRequest request,@RequestBody Request input) {
        return R.ok(service.chat(actor(request),request.getHeader("Authorization"),input));
    }
    @GetMapping("/capabilities") public R<?> capabilities(HttpServletRequest request) { return R.ok(service.capabilities(actor(request))); }
    @PostMapping("/confirm") public R<Reply> confirm(HttpServletRequest request,@RequestBody Confirm input) {
        return R.ok(service.confirm(actor(request),request.getHeader("Authorization"),input));
    }
    public record SessionInput(String sessionId) {}
    public record WritingInput(String instruction, String title, String content, NoticeDraft previous) {}
    @PostMapping("/notice-draft") public R<NoticeDraft> noticeDraft(HttpServletRequest request,@RequestBody WritingInput input) {
        access.require(actor(request),"notice:send","notice:send");
        if(input==null || input.instruction()==null || input.instruction().isBlank() || input.instruction().length()>1500
            || input.title()!=null && input.title().length()>100 || input.content()!=null && input.content().length()>5000
            || input.previous()!=null && (input.previous().title()==null || input.previous().content()==null
                || input.previous().title().length()>100 || input.previous().content().length()>5000))
            throw new CoolException("通知写作请求无效",400);
        return R.ok(model.notice(input));
    }
    @PostMapping("/cancel") public R<?> cancel(HttpServletRequest request,@RequestBody SessionInput input) {
        service.cancel(actor(request),request.getHeader("Authorization"),input.sessionId());return R.ok();
    }
}
