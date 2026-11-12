package com.cool.modules.profile;

import java.time.*;
import java.util.*;
import java.math.*;
import static com.cool.modules.profile.ProfileFacts.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 从去重事实计算消费窗口分类价位和间隔而不让模型计算账务
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:04:00
 */
public final class ProfileStatistics {
    private ProfileStatistics() {}
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public record Category(Long categoryId,long visits,long knownAmountVisits,long consumptionCents,
        Instant lastVisit,Double meanGapDays,List<String> evidence) {}
    public record PriceBand(String name,long visits) {}
    public record Window(int days,long visits,long freeClaims,long paidPurchases,long knownAmountVisits,
        long unknownAmountVisits,long consumptionCents,long purchaseSpendCents,List<Category> categories,List<PriceBand> priceBands,
        long knownDiscountVisits,long discountedVisits,long discountCents) {}
    public record Summary(Instant cutoff,List<Window> windows,String sufficiency,long approximateTimeVisits) {}
    public static Summary summarize(List<Fact> facts,Instant cutoff) {
        List<Window> windows=new ArrayList<>();
        for(int days:List.of(30,90,180)) {
            Instant start=cutoff.atZone(ZONE).toLocalDate().minusDays(days-1).atStartOfDay(ZONE).toInstant();
            var selected=facts.stream().filter(f->!f.occurredAt().isBefore(start)&&!f.occurredAt().isAfter(cutoff)).toList();
            var visits=selected.stream().filter(f->f.kind()==Kind.VISIT).toList();
            var known=visits.stream().filter(f->f.amountCents()!=null).toList();
            var categoryIds=visits.stream().map(Fact::categoryId).filter(Objects::nonNull).distinct().sorted().toList();
            List<Category> categories=new ArrayList<>();
            for(Long id:categoryIds) {
                var group=visits.stream().filter(f->id.equals(f.categoryId())).toList();
                var dates=group.stream().filter(f->!f.approximateTime()).map(f->f.occurredAt().atZone(ZONE).toLocalDate()).distinct().sorted().toList();
                Double gap=dates.size()<2?null:(double)java.time.temporal.ChronoUnit.DAYS.between(dates.get(0),dates.get(dates.size()-1))/(dates.size()-1);
                categories.add(new Category(id,group.size(),group.stream().filter(f->f.amountCents()!=null).count(),sum(group),
                    group.stream().map(Fact::occurredAt).max(Comparator.naturalOrder()).orElseThrow(),gap,group.stream().map(Fact::key).toList()));
            }
            List<PriceBand> prices=List.of(new PriceBand("0–50元",known.stream().filter(f->f.amountCents()<=5000).count()),
                new PriceBand("50–150元",known.stream().filter(f->f.amountCents()>5000&&f.amountCents()<=15000).count()),
                new PriceBand("150元以上",known.stream().filter(f->f.amountCents()>15000).count()));
            long purchase=0;for(var f:selected)if(f.kind()==Kind.PAID_PURCHASE&&f.amountCents()!=null)purchase=Math.addExact(purchase,f.amountCents());
            windows.add(new Window(days,visits.size(),selected.stream().filter(f->f.kind()==Kind.FREE_CLAIM).count(),
                selected.stream().filter(f->f.kind()==Kind.PAID_PURCHASE).count(),known.size(),visits.size()-known.size(),sum(known),purchase,List.copyOf(categories),prices,
                visits.stream().filter(f->f.discountCents()!=null&&f.discountCents()>=0).count(),
                visits.stream().filter(f->f.discountCents()!=null&&f.discountCents()>0).count(),
                visits.stream().filter(f->f.discountCents()!=null&&f.discountCents()>=0).mapToLong(Fact::discountCents).reduce(0,Math::addExact)));
        }
        long visits=windows.get(2).visits();
        return new Summary(cutoff,List.copyOf(windows),visits==0?"NO_CONSUMPTION":visits<3?"LIMITED":"SUPPORTED",
            facts.stream().filter(f->f.kind()==Kind.VISIT&&f.approximateTime()).count());
    }
    private static long sum(List<Fact> facts){long sum=0;for(var f:facts)if(f.amountCents()!=null)sum=Math.addExact(sum,f.amountCents());return sum;}
}
