package com.cool.modules.profile;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按明确业务关联去重消费事实并保留金额和时间质量
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:04:00
 */
public final class ProfileFacts {
    private ProfileFacts() {}
    public enum Kind { FREE_CLAIM, PAID_PURCHASE, VISIT }
    public record Source(String key,Long customerId,Long merchantId,Long storeId,Long categoryId,
        Instant occurredAt,Long amountCents,Long paidCents,Long discountCents,int priority,
        List<String> links,boolean approximateTime) {}
    public record Fact(String key,Kind kind,Long customerId,Long merchantId,Long storeId,Long categoryId,
        Instant occurredAt,Long amountCents,Long paidCents,Long discountCents,List<String> evidence,
        boolean approximateTime) {}
    public record Issue(String code,List<String> evidence) {}
    public record Batch(List<Fact> facts,List<Issue> issues) {}
    public static Long cents(BigDecimal yuan) {
        if(yuan==null)return null;
        try {long value=yuan.movePointRight(2).longValueExact();return value<0?null:value;}
        catch(ArithmeticException invalid){return null;}
    }
    public static Batch visits(Long customer,List<Source> rows,Instant cutoff) {
        Map<String,Source> byKey=new TreeMap<>();List<Issue> issues=new ArrayList<>();
        for(var row:rows)if(byKey.putIfAbsent(row.key(),row)!=null)issues.add(new Issue("DUPLICATE_SOURCE",List.of(row.key())));
        Map<String,String> parent=new HashMap<>();byKey.keySet().forEach(k->parent.put(k,k));
        for(var row:byKey.values())for(String linked:row.links())if(parent.containsKey(linked)) {
            String a=root(parent,row.key()),b=root(parent,linked);if(!a.equals(b))parent.put(b,a);
        }
        Map<String,List<Source>> groups=new TreeMap<>();
        byKey.values().forEach(r->groups.computeIfAbsent(root(parent,r.key()),k->new ArrayList<>()).add(r));
        List<Fact> result=new ArrayList<>();
        for(var group:groups.values()) {
            var evidence=group.stream().map(Source::key).sorted().toList();
            if(group.stream().anyMatch(r->!Objects.equals(customer,r.customerId()))) {
                issues.add(new Issue("CUSTOMER_CONFLICT",evidence));continue;
            }
            if(group.stream().map(Source::merchantId).distinct().count()>1 || group.stream().map(Source::storeId).distinct().count()>1) {
                issues.add(new Issue("LOCATION_CONFLICT",evidence));continue;
            }
            group.sort(Comparator.comparingInt(Source::priority).reversed().thenComparing(Source::key));
            Source chosen=group.get(0);
            Source timed=chosen.occurredAt()==null?group.stream().filter(r->r.occurredAt()!=null&&!r.occurredAt().isAfter(cutoff)).findFirst().orElse(chosen):chosen;
            if(timed.occurredAt()==null || timed.occurredAt().isAfter(cutoff)) {
                issues.add(new Issue("MISSING_OR_FUTURE_TIME",evidence));continue;
            }
            if(timed!=chosen)issues.add(new Issue("LINKED_TIME_FALLBACK",evidence));
            var known=group.stream().map(Source::amountCents).filter(Objects::nonNull).distinct().toList();
            Long amount=known.size()==1?known.get(0):null;
            if(known.size()>1)issues.add(new Issue("AMOUNT_CONFLICT",evidence));
            if(amount!=null && amount<0){amount=null;issues.add(new Issue("NEGATIVE_AMOUNT",evidence));}
            result.add(new Fact(chosen.key(),Kind.VISIT,customer,chosen.merchantId(),chosen.storeId(),chosen.categoryId(),
                timed.occurredAt(),amount,chosen.paidCents(),chosen.discountCents(),evidence,timed.approximateTime()));
        }
        result.sort(Comparator.comparing(Fact::occurredAt).thenComparing(Fact::key));
        return new Batch(List.copyOf(result),List.copyOf(issues));
    }
    private static String root(Map<String,String> parent,String key){
        String p=parent.get(key);if(p.equals(key))return key;String root=root(parent,p);parent.put(key,root);return root;
    }
}
