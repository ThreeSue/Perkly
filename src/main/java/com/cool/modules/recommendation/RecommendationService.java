package com.cool.modules.recommendation;

import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.cool.modules.coupon.entity.CustomerCouponEntity;
import com.cool.modules.coupon.mapper.CustomerCouponMapper;
import com.cool.modules.profile.ProfileService;
import com.cool.modules.support.service.SupportAccess;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 从全量公开候选实时校验资格后提供首页与导购共用推荐
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:40:00
 */
@Service @RequiredArgsConstructor
public class RecommendationService {
    @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.searchrisk.SearchRiskService searchRisk;
    @org.springframework.beans.factory.annotation.Value("${perkly.profile.worker-enabled:false}")
    private boolean enabled;
    private final AppDiscoveryService discovery;
    private final CustomerCouponMapper owned;
    private final ProfileService profiles;
    private final SupportAccess access;
    public record Input(String kind, Integer page, Integer size, String keyword, Long categoryId,
        Long merchantId, Long storeId, String type, Long maxPriceCents, Double latitude, Double longitude,
        Double radiusMeters, String sort) {}
    public record Result(List<Map<String,Object>> list, long total, int page, int size, String rankingDate,
        Long profileRevision, String locationMode) {}
    public Result recommend(Long customer, Input input) {
        access.customer(customer);
        if(searchRisk!=null)searchRisk.check(input.keyword(),"RECOMMENDATION");
        String kind=input.kind()==null?"COUPON":input.kind();
        if(!Set.of("COUPON","STORE").contains(kind))throw new CoolException("推荐类型无效");
        String sort=input.sort()==null?"PERSONALIZED":input.sort();
        if(!Set.of("PERSONALIZED","PRICE_ASC","DISTANCE").contains(sort))throw new CoolException("排序方式无效");
        if(kind.equals("STORE")&&(input.type()!=null||input.maxPriceCents()!=null||sort.equals("PRICE_ASC")))throw new CoolException("门店暂无可靠人均价格，不能按购券价格筛选门店");
        int page=input.page()==null?1:input.page(),size=input.size()==null?20:input.size();SupportAccess.page(page,size);
        if(input.maxPriceCents()!=null&&input.maxPriceCents()<0)throw new CoolException("金额不能为负数");
        boolean located=input.latitude()!=null&&input.longitude()!=null;
        if((input.latitude()==null)!=(input.longitude()==null)||located&&(!Double.isFinite(input.latitude())||!Double.isFinite(input.longitude())||Math.abs(input.latitude())>90||Math.abs(input.longitude())>180))throw new CoolException("位置参数无效");
        if(input.radiusMeters()!=null&&(!Double.isFinite(input.radiusMeters())||input.radiusMeters()<=0||input.radiusMeters()>100000))throw new CoolException("距离须为0至100公里");
        if(input.radiusMeters()!=null&&!located)throw new CoolException("限定距离需要当前位置");
        var req=new DiscoveryPageReq();req.setSize(100);req.setKeyword(input.keyword());req.setCategoryId(input.categoryId());
        req.setMerchantId(input.merchantId());req.setStoreId(input.storeId());req.setType(input.type());req.validate();
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int p=1;;p++) {req.setPage(p);var batch=kind.equals("COUPON")?discovery.coupons(req):discovery.stores(req);rows.addAll(batch.getList());
            if(batch.getList().size()<100||rows.size()>=batch.getPagination().getTotal())break;}
        var counts=new HashMap<Long,Long>();
        for(var coupon:owned.selectListByQuery(QueryWrapper.create().eq(CustomerCouponEntity::getCustomerId,customer)))counts.merge(coupon.getCouponTemplateId(),1L,Long::sum);
        List<RecommendationRanker.Candidate> candidates=new ArrayList<>();Map<Long,Map<String,Object>> cards=new HashMap<>();
        for(var row:rows) {
            Long id=number(row.get("id")),price=number(row.get("salePrice"));Double distance=null;
            if(kind.equals("COUPON")) {
                Long limit=number(row.get("perCustomerLimit"));
                if(!"AVAILABLE".equals(row.get("saleState"))||price==null||price<0||limit==null||limit<=0||counts.getOrDefault(id,0L)>=limit)continue;
                if(input.maxPriceCents()!=null&&price>input.maxPriceCents())continue;
                if(located) {
                    Object stores=discovery.coupon(id).get("stores");
                    if(stores instanceof List<?> list) for(Object store:list)if(store instanceof Map<?,?> m){var d=distance(input,m);if(d!=null&&(distance==null||d<distance))distance=d;}
                }
            }else {if(!Long.valueOf(1).equals(number(row.get("status"))))continue;
                if(input.storeId()!=null&&!input.storeId().equals(id))continue;
                distance=located?distance(input,row):null;}
            if(input.radiusMeters()!=null&&(distance==null||distance>input.radiusMeters()))continue;
            cards.put(id,row);candidates.add(new RecommendationRanker.Candidate(id,number(row.get("merchantId")),number(row.get("categoryId")),price,distance,Objects.requireNonNullElse(number(row.get("issuedCount")),0L),content(row),published(row)));
        }
        if(enabled)profiles.request(customer,false);
        var profile=enabled?profiles.view(customer):null;var facts=profile==null||profile.evidence()==null?List.<com.cool.modules.profile.ProfileFacts.Fact>of():profile.evidence().batch().facts();
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var ranked=RecommendationRanker.rank(customer,day,candidates,facts,sort,profile==null||profile.interpretation()==null?List.of():profile.interpretation().tags());
        var list=ranked.stream().skip((long)(page-1)*size).limit(size).map(r->{var card=new LinkedHashMap<>(cards.get(r.id()));card.put("recommendationReason",r.reason());return (Map<String,Object>)card;}).toList();
        return new Result(list,ranked.size(),page,size,day.toString(),profile==null?null:profile.revision(),located?"GCJ02":"NO_LOCATION");
    }
    public List<Map<String,Object>> rankForAssistant(Long customer,List<Map<String,Object>> rows,boolean cheap) {
        if(!enabled)return rows;
        profiles.request(customer,false);
        var profile=profiles.view(customer);
        var facts=profile.evidence()==null?List.<com.cool.modules.profile.ProfileFacts.Fact>of():profile.evidence().batch().facts();
        var candidates=rows.stream().map(r->new RecommendationRanker.Candidate(number(r.get("id")),number(r.get("merchantId")),number(r.get("categoryId")),number(r.get("salePrice")),null,Objects.requireNonNullElse(number(r.get("issuedCount")),0L),content(r),published(r))).toList();
        Map<Long,Map<String,Object>> indexed=new HashMap<>();rows.forEach(r->indexed.put(number(r.get("id")),r));
        return RecommendationRanker.rank(customer,LocalDate.now(ZoneId.of("Asia/Shanghai")),candidates,facts,cheap?"PRICE_ASC":"PERSONALIZED",profile.interpretation()==null?List.of():profile.interpretation().tags()).stream().map(r->indexed.get(r.id())).toList();
    }
    public static Double distance(Input i,Map<?,?> row) {
        if(row.get("latitude")==null||row.get("longitude")==null)return null;
        double lat=Double.parseDouble(row.get("latitude").toString()),lon=Double.parseDouble(row.get("longitude").toString());
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return null;
        double a=Math.pow(Math.sin(Math.toRadians(lat-i.latitude())/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(i.latitude()))*Math.pow(Math.sin(Math.toRadians(lon-i.longitude())/2),2);
        return 6371000*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,a))));
    }
    private static Long number(Object value){return value==null?null:new java.math.BigDecimal(value.toString()).longValueExact();}
    private static String content(Map<String,Object> row){return Objects.toString(row.get("name"),"")+" "+Objects.toString(row.get("merchantName"),"")+" "+Objects.toString(row.get("categoryName"),"")+" "+Objects.toString(row.get("description"),"");}
    private static Instant published(Map<String,Object> row){try{return LocalDateTime.parse(Objects.toString(row.get("publishedAt"),""),java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Shanghai")).toInstant();}catch(java.time.format.DateTimeParseException absent){return null;}}
}
