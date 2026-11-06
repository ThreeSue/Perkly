package com.cool.modules.wallet.controller;

import com.cool.core.request.R;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.wallet.service.WalletActivityService;
import com.cool.modules.wallet.service.ActivityAudienceService;
import com.cool.modules.wallet.service.ActivityNoticeService;
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
@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/wallet/activities", "/merchant/governance/wallet/activities"})
public class WalletActivityController {
    private final WalletActivityService activities;
    private final HttpServletRequest request;
    private final ActivityAudienceService audience;
    private final ActivityNoticeService notices;

    public record Id(Long id) {
    }

    @GetMapping("/stores")
    public R<?> stores() {
        return R.ok(activities.storeOptions(actor(), platform()));
    }

    private boolean platform() {
        return request.getRequestURI().startsWith("/admin/");
    }

    private Long actor() {
        if (platform()) return CoolSecurityUtil.getCurrentUserId();
        if (request.getAttribute("merchantUserId") instanceof Long id) return id;
        throw new CoolException("请先登录", 401);
    }

    @PostMapping("/create")
    public R<?> create(@RequestBody WalletActivityService.Input p) {
        return R.ok(activities.create(actor(), platform(), p));
    }

    @PostMapping("/review")
    public R<?> review(@RequestBody WalletActivityService.Review p) {
        if (!platform()) throw new CoolException("仅运营可审批活动", 403);
        activities.review(actor(), p);
        return R.ok();
    }

    @PostMapping("/offline")
    public R<?> offline(@RequestBody Id p) {
        activities.offline(actor(), platform(), p.id());
        return R.ok();
    }
    @PostMapping("/schedule")
    public R<?> schedule(@RequestBody WalletActivityService.Schedule input) {
        if(!platform())throw new CoolException("仅运营可调整活动开关",403);
        activities.schedule(input);
        return R.ok();
    }

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") int pageNumber, @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(activities.page(actor(), platform(), pageNumber, pageSize));
    }
    @GetMapping("/detail")
    public R<?> detail(@RequestParam Long id) {
        if(platform())throw new CoolException("运营请通过活动列表查看",403);
        return R.ok(activities.detail(actor(),id));
    }
    @GetMapping("/participation")
    public R<?> participation(@RequestParam Long id) {
        if(platform())throw new CoolException("参与调整由商家操作",403);
        return R.ok(activities.participation(actor(),id));
    }
    @PostMapping("/participation")
    public R<?> participate(@RequestBody ActivityAudienceService.ParticipationInput input) {
        if(platform())throw new CoolException("参与调整由商家操作",403);
        return R.ok(activities.participate(actor(),input));
    }
    @GetMapping("/contacts/options")
    public R<?> contactOptions(@RequestParam(required=false) Long merchantId,@RequestParam(required=false) Long storeId) {
        return R.ok(audience.contactOptions(actor(),platform(),merchantId,storeId));
    }
    @GetMapping("/contacts/stores")
    public R<?> contactStores(@RequestParam(required=false) Long merchantId) {
        return R.ok(audience.contactStores(actor(),platform(),merchantId));
    }
    @GetMapping("/contacts")
    public R<?> contacts(@RequestParam(required=false) Long merchantId,@RequestParam(required=false) Long storeId) {
        return R.ok(audience.contactIds(actor(),platform(),merchantId,storeId));
    }
    @PostMapping("/contacts")
    public R<?> contacts(@RequestBody ActivityAudienceService.ContactInput input) {
        audience.saveContacts(actor(),platform(),input);
        return R.ok();
    }
    @GetMapping("/notice-events")
    public R<?> events(@RequestParam Long id) {
        if(!platform())throw new CoolException("仅运营可查看自动发送记录",403);
        return R.ok(notices.events(id));
    }
    @GetMapping("/notice-events/receipts")
    public R<?> eventReceipts(@RequestParam Long eventId) {
        if(!platform())throw new CoolException("仅运营可查看发送回执",403);
        return R.ok(notices.deliveries(eventId));
    }
    @PostMapping("/notice-events/retry")
    public R<?> retry(@RequestBody Id input) {
        if(!platform())throw new CoolException("仅运营可重试任务",403);
        notices.retry(input.id());
        return R.ok();
    }
}
