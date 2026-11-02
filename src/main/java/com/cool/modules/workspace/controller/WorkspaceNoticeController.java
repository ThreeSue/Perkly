package com.cool.modules.workspace.controller;

import com.cool.core.request.R;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.workspace.service.WorkspaceNoticeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供统一网页与核销移动端共用的站内通知接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:24:11
 */
@RestController @RequiredArgsConstructor
@RequestMapping({"/admin/workspace/notices","/merchant/governance/notices"})
public class WorkspaceNoticeController {
    private final WorkspaceNoticeService service;
    private final HttpServletRequest request;
    public record IdInput(Long id) {}
    private Long actor(){
        if(request.getRequestURI().startsWith("/admin/")){
            if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN)throw new CoolException("请先登录运营账号",401);
            return CoolSecurityUtil.getCurrentUserId();
        }
        if(request.getAttribute("merchantUserId") instanceof Long id)return id;
        throw new CoolException("请先登录",401);
    }
    @GetMapping("/recipients") public R<?> recipients(@RequestParam(required=false) Long merchantId,@RequestParam(required=false) Long storeId,
        @RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(service.options(actor(),merchantId,storeId,pageNumber,pageSize));}
    @PostMapping("/send") public R<?> send(@RequestBody WorkspaceNoticeService.SendInput p){return R.ok(service.send(actor(),p));}
    @GetMapping("/page") public R<?> page(@RequestParam(defaultValue="false") boolean sent,@RequestParam(defaultValue="1") int pageNumber,
        @RequestParam(defaultValue="20") int pageSize){return R.ok(service.page(actor(),sent,pageNumber,pageSize));}
    @GetMapping("/detail") public R<?> detail(@RequestParam Long id){return R.ok(service.detail(actor(),id));}
    @GetMapping("/receipts") public R<?> receipts(@RequestParam Long id){return R.ok(service.receipts(actor(),id));}
    @PostMapping("/read") public R<?> read(@RequestBody IdInput p){if(p.id()==null)throw new CoolException("缺少通知ID");service.read(actor(),p.id());return R.ok();}
    @GetMapping("/unread") public R<?> unread(){return R.ok(service.unread(actor()));}
}
