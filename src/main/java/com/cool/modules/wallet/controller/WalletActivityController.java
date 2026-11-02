package com.cool.modules.wallet.controller;
import com.cool.core.request.R;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.wallet.service.WalletActivityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营发布审批折扣及商家申请下架活动接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:47:40
 */
@RestController @RequiredArgsConstructor @RequestMapping({"/admin/wallet/activities","/merchant/governance/wallet/activities"})
public class WalletActivityController{
    private final WalletActivityService activities;
    private final HttpServletRequest request;
    public record Id(Long id){}
    @GetMapping("/stores") public R<?> stores(){return R.ok(activities.storeOptions(actor(),platform()));}
    private boolean platform(){return request.getRequestURI().startsWith("/admin/");}
    private Long actor(){if(platform())return CoolSecurityUtil.getCurrentUserId();if(request.getAttribute("merchantUserId") instanceof Long id)return id;throw new CoolException("请先登录",401);}
    @PostMapping("/create") public R<?> create(@RequestBody WalletActivityService.Input p){return R.ok(activities.create(actor(),platform(),p));}
    @PostMapping("/review") public R<?> review(@RequestBody WalletActivityService.Review p){if(!platform())throw new CoolException("仅运营可审批活动",403);activities.review(actor(),p);return R.ok();}
    @PostMapping("/offline") public R<?> offline(@RequestBody Id p){activities.offline(actor(),platform(),p.id());return R.ok();}
    @GetMapping("/page") public R<?> page(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(activities.page(actor(),platform(),pageNumber,pageSize));}
}
