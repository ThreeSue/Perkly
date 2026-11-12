package com.cool.modules.recommendation;

import com.cool.modules.profile.ProfileFacts;
import java.time.*;
import java.util.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 用真实消费强弱信号和每日稳定探索排序合格候选
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:40:00
 */
public final class RecommendationRanker {
    private RecommendationRanker() {}
    public record Candidate(Long id, Long merchantId, Long categoryId, Long priceCents,
                            Double distanceMeters, long popularity, String content,Instant publishedAt) {
        public Candidate(Long id,Long merchantId,Long categoryId,Long priceCents,Double distanceMeters,long popularity){this(id,merchantId,categoryId,priceCents,distanceMeters,popularity,"",null);}
    }
    public record Ranked(Long id, double score, String reason) {}
    public static List<Ranked> rank(Long customer, LocalDate day, List<Candidate> candidates,
                                   List<ProfileFacts.Fact> facts, String sort) {
        return rank(customer,day,candidates,facts,sort,List.of());
    }
    public static List<Ranked> rank(Long customer, LocalDate day, List<Candidate> candidates,
                                   List<ProfileFacts.Fact> facts, String sort,List<com.cool.modules.profile.ProfileModel.Tag> tags) {
        Map<Long, Double> preferences = new HashMap<>();
        Map<Long, Double> weak = new HashMap<>();
        Set<Long> recentMerchants = new HashSet<>();
        Instant cutoff = day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        for (var fact : facts) {
            if (fact.occurredAt().isAfter(cutoff)) continue;
            long age = Math.max(0, Duration.between(fact.occurredAt(), cutoff).toDays());
            double weight = switch (fact.kind()) { case VISIT -> 4; case PAID_PURCHASE -> 1; case FREE_CLAIM -> .1; };
            if (fact.categoryId() != null) (fact.kind()==ProfileFacts.Kind.FREE_CLAIM?weak:preferences).merge(fact.categoryId(), weight * Math.exp(-age / 45.0), Double::sum);
            if (fact.kind() == ProfileFacts.Kind.VISIT && age < 2) recentMerchants.add(fact.merchantId());
        }
        weak.forEach((category,weight)->preferences.merge(category,Math.min(.5,weight),Double::sum));
        // 只用不同且精确的消费日估计节奏；领券与近似时间不参与。
        for(Long category:new ArrayList<>(preferences.keySet())) {
            var dates=facts.stream().filter(f->f.kind()==ProfileFacts.Kind.VISIT&&!f.approximateTime()&&category.equals(f.categoryId()))
                .map(f->f.occurredAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()).filter(d->!d.isAfter(day)).distinct().sorted().toList();
            if(dates.size()>1) {
                double gap=(double)java.time.temporal.ChronoUnit.DAYS.between(dates.get(0),dates.get(dates.size()-1))/(dates.size()-1);
                long elapsed=java.time.temporal.ChronoUnit.DAYS.between(dates.get(dates.size()-1),day);
                if(gap>0)preferences.computeIfPresent(category,(k,v)->v*(elapsed>=gap?1.15:elapsed<gap*.3?.75:1));
            }
        }
        Map<Long, Candidate> indexed = new HashMap<>();
        List<Ranked> ranked = new ArrayList<>();
        for (var c : candidates) {
            if (indexed.putIfAbsent(c.id(), c) != null) continue;
            double interest = Math.min(6, preferences.getOrDefault(c.categoryId(), 0.0));
            double contentInterest=0,recentContent=0;
            for(var tag:tags)if(c.content()!=null&&c.content().contains(tag.label())) {
                var evidence=facts.stream().filter(f->tag.factKeys().contains(f.key())).toList();
                contentInterest+=Math.min(2,evidence.stream().mapToDouble(f->f.kind()==ProfileFacts.Kind.VISIT?1:f.kind()==ProfileFacts.Kind.PAID_PURCHASE?.25:.025).sum());
                if(evidence.stream().anyMatch(f->f.kind()==ProfileFacts.Kind.VISIT&&Duration.between(f.occurredAt(),cutoff).toDays()<2))recentContent=2;
            }
            interest+=Math.min(2,contentInterest);
            double nearby = c.distanceMeters() == null ? 0 : 2 / (1 + c.distanceMeters() / 2000);
            double exploration = new Random(Objects.hash(customer, day.toEpochDay(), c.id())).nextDouble() * .8;
            double fresh=c.publishedAt()==null||c.publishedAt().isAfter(cutoff)?0:Math.max(0,.6*(1-Duration.between(c.publishedAt(),cutoff).toDays()/14.0));
            double score = interest + nearby + Math.min(1, Math.log1p(Math.max(0, c.popularity())) / 10) + exploration + fresh
                - (recentMerchants.contains(c.merchantId()) ? 2 : 0) - recentContent;
            ranked.add(new Ranked(c.id(), score, interest > 0 ? "参考近期业务偏好" : nearby > 0 ? "结合距离与每日探索" : "基础候选与每日探索"));
        }
        Comparator<Ranked> comparator = Comparator.comparingDouble(Ranked::score).reversed();
        if ("PRICE_ASC".equals(sort)) comparator = Comparator.comparing(r -> indexed.get(r.id()).priceCents(), Comparator.nullsLast(Long::compareTo));
        if ("DISTANCE".equals(sort)) comparator = Comparator.comparing(r -> indexed.get(r.id()).distanceMeters(), Comparator.nullsLast(Double::compareTo));
        ranked.sort(comparator.thenComparing(Ranked::id));
        return List.copyOf(ranked);
    }
}
