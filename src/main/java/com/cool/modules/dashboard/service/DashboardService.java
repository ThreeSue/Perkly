package com.cool.modules.dashboard.service;

import com.cool.modules.dashboard.mapper.DashboardMapper;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.mapper.query.AppDiscoveryQueries;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供数据库真实运营概览并补齐近十四天无新增日期
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 23:40:24
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private final DashboardMapper mapper;
    private final CouponTemplateMapper coupons;

    public Map<String, Object> overview() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        Map<String, Object> counts = new LinkedHashMap<>(mapper.counts());
        // 复用用户端公开列表条件：商家、模板、门店、审核和发布版本均须有效。
        counts.put("publishedCoupons", coupons.selectCountByQuery(AppDiscoveryQueries.coupons(new DiscoveryPageReq())));
        LinkedHashMap<String, Map<String, Object>> days = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            for (String kind : List.of("users", "merchants", "stores", "coupons")) day.put(kind, 0L);
            days.put(date, day);
        }
        mapper.trends(today.minusDays(13).atStartOfDay(), today.plusDays(1).atStartOfDay()).forEach(row -> {
            Map<String, Object> day = days.get(row.get("date"));
            if (day != null) day.put((String)row.get("kind"), row.get("total"));
        });
        return Map.of("counts", counts, "couponTypes", mapper.couponTypes(),
                "trends", new ArrayList<>(days.values()), "updatedAt", OffsetDateTime.now(zone).toString());
    }
}
