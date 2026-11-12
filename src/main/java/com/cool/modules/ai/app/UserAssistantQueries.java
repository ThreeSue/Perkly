package com.cool.modules.ai.app;

import static com.cool.modules.ai.app.UserAssistantModels.*;
import com.cool.core.request.PageResult;
import com.cool.modules.coupon.entity.CustomerCouponEntity;
import com.cool.modules.coupon.mapper.CustomerCouponMapper;
import com.cool.modules.coupon.service.CouponTradeService;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.mybatisflex.core.query.QueryWrapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 复用公开展示与本人券订单查询生成不含券码的可信助手卡片
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 17:07:03
 */
@Service @RequiredArgsConstructor
public class UserAssistantQueries {
    private final AppDiscoveryService discovery;
    private final CouponTradeService trade;
    private final CustomerCouponMapper owned;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.recommendation.RecommendationService recommendations;

    public Result query(Long customer, Action action, Conditions c, Location location, Target target, String state) {
        if(target!=null && target.kind()==Kind.STORE && (action==Action.STORES || action==Action.RULES)) {
            var row=discovery.store(target.id());
            return new Result(List.of(card(Kind.STORE,row,false)),false,
                "仅查询当前选中的门店，未进行附近搜索或替换其他门店；营业时间为门店公开资料，不保证此刻营业。");
        }
        if(action==Action.RULES) {
            if(target==null || target.kind()!=Kind.COUPON && target.kind()!=Kind.WALLET)
                return new Result(List.of(),false,"请先选择一张优惠券或我的券；历史券请从我的券进入。");
            boolean wallet=target.kind()==Kind.WALLET;
            var row=wallet?trade.walletInfo(customer,target.id()):discovery.coupon(target.id());
            return new Result(List.of(card(wallet?Kind.WALLET:Kind.COUPON,row,false)),false,
                wallet?"按本人持券时的规则快照解释，不代表当前在售。":"仅解释当前公开规则；请进入详情核对实时领购资格。");
        }
        if(action==Action.ORDERS || action==Action.WALLET) {
            Kind kind=action==Action.ORDERS?Kind.ORDER:Kind.WALLET;
            if(target!=null && target.kind()==kind) {
                var row=kind==Kind.ORDER?trade.orderInfo(customer,target.id()):trade.walletInfo(customer,target.id());
                return new Result(List.of(card(kind,row,false)),false,"仅查询当前登录用户的记录，不执行交易。");
            }
            var page=kind==Kind.ORDER?trade.orders(customer,1,8,state):trade.wallet(customer,1,8,state);
            Long total=page.getPagination().getTotal();
            return new Result(page.getList().stream().map(r->card(kind,r,false)).toList(),
                total==null || total>page.getList().size(),"展示本人最近记录；更多记录请进入订单或我的券。"
                    +(total==null?"没有全量总数，不能按卡片数量报告总数。":"当前本人及状态筛选条件下的数据库全量总数为"+total+"条，本次展示"+page.getList().size()+"条；两者不能混淆。"));
        }
        if(action!=Action.COUPONS && action!=Action.STORES) return new Result(List.of(),false,"只支持查找门店优惠券、本人券包订单及规则解释。");
        List<Map<String,Object>> candidates=new ArrayList<>();
        boolean partial=scan(p->{var req=new DiscoveryPageReq();req.setPage(p);req.setSize(100);req.setKeyword(c.keyword());
            return action==Action.COUPONS?discovery.coupons(req):discovery.stores(req);},candidates);
        if(recommendations!=null)candidates=recommendations.rankForAssistant(customer,candidates,Boolean.TRUE.equals(c.cheap()));
        List<Card> cards=new ArrayList<>();
        for(var row:candidates) {
            if(action==Action.STORES) {
                if(Long.valueOf(1).equals(number(row.get("status")))&&matchesPlace(row,c,location)) cards.add(card(Kind.STORE,row,false));
                continue;
            }
            if(!eligible(customer,row,c)) continue;
            if(c.area()!=null || Boolean.TRUE.equals(c.nearby()) && location!=null) {
                var detail=discovery.coupon(number(row.get("id")));
                Object stores=detail.get("stores");
                if(!(stores instanceof List<?> list) || list.stream().noneMatch(s->s instanceof Map<?,?> m && matchesPlace(m,c,location))) continue;
            }
            cards.add(card(Kind.COUPON,row,true));
        }
        if(Boolean.TRUE.equals(c.cheap())) cards.sort(Comparator.comparing(Card::priceCents,Comparator.nullsLast(Long::compareTo)));
        String note=location==null?"未使用当前位置；有区域条件则按门店地址匹配，否则展示全平台候选，不称附近。":"附近筛选使用提供的GCJ-02位置，半径10公里；位置未设置的门店不参加附近筛选。";
        if(c.spendBudgetCents()!=null) note+="消费预算仅筛选起用门槛，不代表最终消费总额。";
        if(partial) note+="本次仅检查前2000条公开记录，结果非全量，不能宣称全平台最低价或全平台没有结果。";
        note+="卡片为查询时状态，实际领购以原业务接口实时校验为准。";
        return new Result(cards.stream().limit(8).toList(),partial || cards.size()>8,note);
    }

    private boolean scan(IntFunction<PageResult<Map<String,Object>>> query,List<Map<String,Object>> rows) {
        for(int p=1;;p++) {
            var page=query.apply(p);rows.addAll(page.getList());
            Long total=page.getPagination().getTotal();
            if(page.getList().size()<100 || total!=null && rows.size()>=total) return false;
        }
    }
    private boolean eligible(Long customer,Map<String,Object> row,Conditions c) {
        if(!"AVAILABLE".equals(row.get("saleState"))) return false;
        Long price=number(row.get("salePrice"));
        if(price==null || price<0 || c.priceMode()==PriceMode.FREE && price!=0 || c.priceMode()==PriceMode.PAID && price<=0
            || c.maxPriceCents()!=null && price>c.maxPriceCents()) return false;
        Long minimum=number(row.get("minSpend"));
        if(c.spendBudgetCents()!=null && minimum!=null && minimum>c.spendBudgetCents()) return false;
        Long limit=number(row.get("perCustomerLimit"));
        if(limit==null || limit<=0) return false;
        return owned.selectCountByQuery(QueryWrapper.create().eq(CustomerCouponEntity::getCustomerId,customer)
            .eq(CustomerCouponEntity::getCouponTemplateId,number(row.get("id"))))<limit;
    }
    private boolean matchesPlace(Map<?,?> row,Conditions c,Location location) {
        if(c.area()!=null && !Objects.toString(row.get("address"),"").contains(c.area())) return false;
        if(!Boolean.TRUE.equals(c.nearby()) || location==null) return true;
        if(row.get("latitude")==null || row.get("longitude")==null) return false;
        double lat=new BigDecimal(row.get("latitude").toString()).doubleValue();
        double lon=new BigDecimal(row.get("longitude").toString()).doubleValue();
        if(Math.abs(lat)>90 || Math.abs(lon)>180) return false;
        double from=Math.toRadians(location.latitude().doubleValue()),to=Math.toRadians(lat);
        double a=Math.pow(Math.sin((to-from)/2),2)+Math.cos(from)*Math.cos(to)*Math.pow(Math.sin(Math.toRadians(lon-location.longitude().doubleValue())/2),2);
        return 6371000*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,a))))<=10000;
    }
    private Card card(Kind kind,Map<String,Object> row,boolean purchasable) {
        String title=text(row,kind==Kind.ORDER?"orderNo":"name");
        String status=text(row,switch(kind){case COUPON->"saleStateText";case WALLET->"stateText";case ORDER->"orderStatusText";default->"statusText";});
        List<Field> facts=new ArrayList<>();
        add(facts,row,"address","地址");add(facts,row,"businessHours","营业时间");
        add(facts,row,"validFrom","有效期开始");add(facts,row,"validTo","有效期结束");
        add(facts,row,"saleStartTime","开售时间");add(facts,row,"saleEndTime","停售时间");
        add(facts,row,"createdAt","创建时间");add(facts,row,"paidAt","支付时间");
        rules(facts,row);
        if(row.get("rule") instanceof Map<?,?> rule) rules(facts,rule);
        // 严格白名单，不把券码、二维码、支付参数、手机号或内部快照整体交给模型。
        return new Card(kind,number(row.get("id")),title,text(row,"merchantName"),text(row,"cover"),status,
            number(row.get(kind==Kind.ORDER?"totalAmount":"salePrice")),purchasable,
            switch(kind){case COUPON->"COUPON_DETAIL";case WALLET->"WALLET_DETAIL";case STORE->"STORE_DETAIL";case ORDER->"ORDER_DETAIL";},List.copyOf(facts));
    }
    private void rules(List<Field> facts,Map<?,?> row) {
        add(facts,row,"description","使用说明");add(facts,row,"typeText","券类型");
        add(facts,row,"storeNames","适用门店");contents(facts,row.get("contentItems"));contents(facts,row.get("contents"));
        if(row.get("stores") instanceof List<?> stores) {
            String names=stores.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(s->Objects.toString(s.get("name"),"")).collect(java.util.stream.Collectors.joining("、"));
            if(!names.isBlank()) facts.add(new Field("适用门店",names));
        }
        for(String[] item:new String[][]{{"minSpend","最低消费"},{"discountAmount","满减金额"},{"deductAmount","抵扣金额"},{"maxDiscount","最高优惠"},{"salePrice","购券价格"}}) {
            Long amount=number(row.get(item[0]));if(amount!=null) facts.add(new Field(item[1],BigDecimal.valueOf(amount,2).toPlainString()+"元"));
        }
        if(row.get("discountRate")!=null) facts.add(new Field("折扣",row.get("discountRate")+"折"));
        if(row.get("allowCombine")!=null) facts.add(new Field("叠加",Boolean.TRUE.equals(row.get("allowCombine")) || "1".equals(row.get("allowCombine").toString())?"允许叠加，仍需满足其他券规则":"不可叠加"));
        if("AFTER_ISSUE".equals(row.get("validityType"))) add(facts,row,"validDays","领取后有效天数");
    }
    private void contents(List<Field> facts,Object content) {
        if(content instanceof List<?> list) for(Object item:list) {
            if(item instanceof Map<?,?> m && m.get("name")!=null) {
                String value=m.get("name")+" × "+Objects.toString(m.get("quantity"),"1");
                String description=Objects.toString(m.get("description"),Objects.toString(m.get("itemDescription"),""));
                if(!description.isBlank())value+="，"+description;
                facts.add(new Field("包含内容",value.substring(0,Math.min(1000,value.length()))));
            }
        }
    }
    private void add(List<Field> facts,Map<?,?> row,String key,String label) {
        if(row.get(key)!=null) {String value=row.get(key).toString();facts.add(new Field(label,value.substring(0,Math.min(2000,value.length()))));}
    }
    private static String text(Map<?,?> row,String key){return Objects.toString(row.get(key),"");}
    private static Long number(Object value){return value==null?null:new BigDecimal(value.toString()).longValueExact();}
}
