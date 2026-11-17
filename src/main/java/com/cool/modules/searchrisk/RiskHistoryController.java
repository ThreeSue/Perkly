package com.cool.modules.searchrisk;

import com.cool.core.request.R;
import com.cool.modules.support.service.SupportAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供历史风险预览、应用与手动重扫接口
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:30:00
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/search-risk/history", "/admin/content-risk/history"})
public class RiskHistoryController {
    private final RiskHistoryService history;
    private final SupportAccess access;

    @PostMapping("/preview")
    public R<RiskHistoryService.Preview> preview(@RequestBody RiskHistoryService.Scope input) {
        access.platform("search:risk:view");
        return R.ok(history.preview(input));
    }

    @PostMapping("/apply")
    public R<RiskHistoryService.Applied> apply(@RequestBody RiskHistoryService.Apply input) {
        access.platform("search:risk:manage");
        return R.ok(history.apply(input));
    }

    @PostMapping("/rescan")
    public R<RiskHistoryService.Scanned> rescan(@RequestBody RiskHistoryService.Scan input) {
        access.platform("search:risk:manage");
        return R.ok(history.rescan(input));
    }
}
