package com.cool.modules.ai.merchant;

import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponRedemptionEntity;
import com.cool.modules.coupon.mapper.CouponRedemptionMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按上海自然日及可信门店范围统计成功核销并区分未知金额
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:18:58
 */
@Service @RequiredArgsConstructor
public class MerchantAiStatistics {
    private final com.cool.modules.merchant.report.MerchantReportService reports;
    public record Input(String period, LocalDate startDate, LocalDate endDate, Long operatorId) {}
    public record Dates(LocalDate startDate,LocalDate endDate) {}
    public record Result(Dates dates,Long storeId,Long operatorId,long successfulRedemptions,
                         Long failedAttempts,BigDecimal knownConsumptionYuan,long unknownAmountGroups,
                         String note) {}
    public static Dates dates(Input input,LocalDate today) {
        String period=input.period()==null?"TODAY":input.period();
        return switch(period) {
            case "TODAY" -> new Dates(today,today);
            case "LAST_7_DAYS" -> new Dates(today.minusDays(6),today);
            case "LAST_30_DAYS" -> new Dates(today.minusDays(29),today);
            case "CUSTOM" -> {
                if(input.startDate()==null || input.endDate()==null || input.endDate().isBefore(input.startDate())
                    || input.endDate().isAfter(today) || input.startDate().plusDays(365).isBefore(input.endDate()))
                    throw new CoolException("请提供有效日期范围（不超过366天且不晚于今天）",400);
                yield new Dates(input.startDate(),input.endDate());
            }
            default -> throw new CoolException("不支持的日期筛选",400);
        };
    }
    public Result query(Long actor,Input input) {
        if(input==null)throw new CoolException("缺少筛选条件",400);
        var r=reports.query(actor,new com.cool.modules.merchant.report.MerchantReportModels.Filter(input.period(),input.startDate(),input.endDate(),input.operatorId(),null,null,1,1));
        return new Result(new Dates(r.range().startDate(),r.range().endDate()),r.storeId(),r.operatorId(),r.summary().successfulRedemptions(),r.summary().failedAttempts(),r.summary().knownConsumptionYuan(),r.summary().unknownAmountGroups(),r.note());
    }
    static Result summarize(Dates dates,MerchantAiAccess.Scope scope,List<CouponRedemptionEntity> rows) {
        Map<String,List<CouponRedemptionEntity>> groups=new HashMap<>();
        for(var row:rows)groups.computeIfAbsent(row.getConsumptionOrderId()==null?"R"+row.getId():"O"+row.getConsumptionOrderId(),k->new ArrayList<>()).add(row);
        BigDecimal sum=BigDecimal.ZERO;long unknown=0;
        for(var group:groups.values()) {
            var amounts=group.stream().map(CouponRedemptionEntity::getConsumptionAmount).filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros).distinct().toList();
            if(amounts.size()!=1 || group.stream().anyMatch(r->r.getConsumptionAmount()==null))unknown++;
            else sum=sum.add(amounts.get(0));
        }
        return new Result(dates,scope.storeId(),scope.operatorId(),rows.size(),null,
            unknown==groups.size() && unknown>0?null:sum,unknown,
            "仅统计成功核销；失败尝试无可靠流水，返回未知。金额按消费账单去重，仅为已知消费金额，不是营业收入；缺失或冲突金额不按零元处理。");
    }
}
