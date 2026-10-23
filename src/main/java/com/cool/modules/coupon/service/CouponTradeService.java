package com.cool.modules.coupon.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.entity.CouponRedemptionEntity;
import com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper;
import com.cool.modules.coupon.mapper.CouponTradeMapper;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.cool.modules.coupon.service.CouponTradeRules.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 事务处理用户购券模拟支付发券券包及商家单次核销
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:16:53
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CouponTradeService {
    private final CouponTradeMapper mapper;
    private final CouponTemplateRelationsMapper relations;
    private final CouponTemplateService templates;
    private final AppDiscoveryService discovery;
    private final MerchantAccountService merchants;
    private final com.cool.modules.merchant.service.MerchantStaffService staff;

    public Map<String,Object> create(Long customerId, Long templateId, Integer quantity, String requestId) {
        customer(customerId, true);
        require(templateId != null && templateId > 0, "请选择优惠券");
        int qty = quantity == null ? 1 : quantity;
        require(qty >= 1 && qty <= 20, "每单数量须为1至20张");
        require(requestId != null && requestId.matches("[A-Za-z0-9_-]{8,64}"), "请提供8至64位的唯一请求标识requestId");
        Row existing = mapper.one("purchase_order", q().eq("customer_id",customerId).eq("request_id",requestId));
        if (existing != null) {
            Row item = item(number(existing.get("id")));
            require(number(item.get("coupon_template_id")) == templateId && number(item.get("quantity")) == qty, "同一requestId不能用于不同购券内容");
            return orderInfo(customerId, number(existing.get("id")));
        }
        CouponTemplateEntity coupon = available(customerId, templateId, qty);
        Map<String,Object> snapshot = BeanUtil.beanToMap(coupon);
        for (String key : List.of("publication","queryWrapper","createTime","updateTime","issuedCount")) snapshot.remove(key);
        JSONObject rule = JSONUtil.parseObj(snapshot);
        validateDays(rule);
        BigDecimal total = coupon.getSalePrice().multiply(BigDecimal.valueOf(qty));
        require(total.compareTo(new BigDecimal("99999999.99")) <= 0, "订单总金额超出范围");
        Row publication = relations.publicationState(templateId);
        Long id = mapper.create("purchase_order", Row.of("order_no", "Q"+UUID.randomUUID().toString().replace("-",""))
                .set("merchant_id",coupon.getMerchantId()).set("customer_id",customerId).set("channel","PLATFORM")
                .set("order_status","PENDING").set("payment_status","UNPAID").set("total_amount",total)
                .set("request_id",requestId).set("expires_at",new Date(System.currentTimeMillis()+15*60_000L)));
        mapper.create("purchase_order_item", Row.of("merchant_id",coupon.getMerchantId()).set("order_id",id)
                .set("coupon_template_id",templateId).set("publication_id",publication.get("id"))
                .set("coupon_name",coupon.getName()).set("rule_snapshot",JSONUtil.toJsonStr(snapshot))
                .set("unit_price",coupon.getSalePrice()).set("quantity",qty).set("subtotal",total));
        return orderInfo(customerId,id);
    }

    public Map<String,Object> claim(Long customerId, Long templateId, Integer quantity, String requestId) {
        Map<String,Object> created = create(customerId,templateId,quantity,requestId);
        require(number(created.get("totalAmount")) == 0, "此券需要购买，请使用订单模拟支付接口");
        return simulatePay(customerId, number(created.get("id")), "SUCCESS");
    }

    public Map<String,Object> simulatePay(Long customerId, Long orderId, String result) {
        customer(customerId,true);
        positive(orderId);
        String outcome = result == null ? "SUCCESS" : result;
        require(Set.of("SUCCESS","FAILED").contains(outcome), "模拟支付结果只能为SUCCESS或FAILED");
        Row order = mapper.lockOrder(orderId,customerId);
        require(order != null,"订单不存在或无权访问");
        if ("PAID".equals(order.get("order_status"))) {
            require("SUCCESS".equals(outcome),"已完成订单不能改为失败");
            return orderInfo(customerId,orderId);
        }
        require("PENDING".equals(order.get("order_status")),"订单已关闭，不能支付");
        require(expires(order).after(new Date()),"订单已超时，请重新下单");
        if ("FAILED".equals(outcome)) {
            mapper.change("purchase_order",orderId,Row.of("order_status","FAILED").set("payment_status","FAILED").setRaw("update_time","NOW()"));
            return orderInfo(customerId,orderId);
        }
        Row item = item(orderId); Long templateId = number(item.get("coupon_template_id"));
        int quantity = ((Number)item.get("quantity")).intValue();
        CouponTemplateEntity current = available(customerId,templateId,quantity);
        JSONObject rule = JSONUtil.parseObj(item.get("rule_snapshot"));
        require(Objects.equals(rule.getInt("ruleVersion"),current.getRuleVersion()),"优惠券规则已变更，请重新下单");
        Row publication = relations.publicationState(templateId);
        require(number(publication.get("id")) == number(item.get("publication_id")),"优惠券发布记录已变更，请重新下单");
        // DATETIME(0)由数据库按秒保存；发券起点取整，避免四舍五入后短暂显示未生效。
        Date now = new Date(System.currentTimeMillis() / 1000 * 1000);
        Date from = "FIXED".equals(rule.getStr("validityType")) ? rule.getDate("validFrom") : now;
        Date to = "FIXED".equals(rule.getStr("validityType")) ? rule.getDate("validTo")
                : Date.from(now.toInstant().atZone(ZONE).plusDays(rule.getInt("validDays")).toInstant());
        require(to != null && to.after(now) && from != null && to.after(from),"优惠券有效期不可用");
        for (int sequence=1;sequence<=quantity;sequence++) {
            String code = code();
            mapper.create("customer_coupon",Row.of("merchant_id",order.get("merchant_id")).set("customer_id",customerId)
                    .set("coupon_template_id",templateId).set("publication_id",item.get("publication_id"))
                    .set("order_item_id",item.get("id")).set("issue_sequence",sequence)
                    .set("source_type",decimal(order.get("total_amount")).signum() == 0 ? "CLAIM" : "PURCHASE")
                    .set("channel","PLATFORM").set("code",code).set("qr_code_image",qrImage(code)).set("status",0)
                    .set("valid_from",from).set("valid_to",to).set("rule_snapshot",item.get("rule_snapshot")).set("issued_at",now));
        }
        require(mapper.consumeStock(templateId,current.getIssuedCount(),quantity),"库存已变化，请重新尝试支付");
        mapper.change("purchase_order",orderId,Row.of("order_status","PAID").set("payment_status","SUCCESS").set("paid_at",now).setRaw("update_time","NOW()"));
        return orderInfo(customerId,orderId);
    }

    public Map<String,Object> cancel(Long customerId, Long id) {
        customer(customerId,true); positive(id);
        Row order = mapper.lockOrder(id,customerId);
        require(order != null,"订单不存在或无权访问");
        require(Set.of("PENDING","CANCELLED").contains(order.get("order_status")),"只有待支付订单可以取消");
        if (!"CANCELLED".equals(order.get("order_status"))) mapper.change("purchase_order",id,Row.of("order_status","CANCELLED").setRaw("update_time","NOW()"));
        return orderInfo(customerId,id);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> orderInfo(Long customerId, Long id) {
        customer(customerId,false); positive(id);
        Row row = mapper.one("purchase_order",q().eq("id",id).eq("customer_id",customerId));
        require(row != null,"订单不存在或无权访问");
        Map<String,Object> result = orderSummary(row);
        List<Row> items = mapper.rows("purchase_order_item",q().eq("order_id",id).orderBy("id",true));
        result.put("items",items.stream().map(this::itemSummary).toList());
        result.put("couponIds",items.isEmpty() ? List.of() : mapper.rows("customer_coupon",q().select("id")
                .in("order_item_id",items.stream().map(r->r.get("id")).toList()).eq("customer_id",customerId).orderBy("id",true))
                .stream().map(r->r.get("id")).toList());
        return result;
    }

    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> orders(Long customerId, Integer page, Integer size, String status) {
        customer(customerId,false);
        QueryWrapper query = q().eq("customer_id",customerId);
        if (status != null && !status.isBlank()) {
            require(Set.of("PENDING","PAID","CANCELLED","FAILED","EXPIRED").contains(status),"订单状态无效");
            query.eq("order_status","EXPIRED".equals(status) ? "PENDING" : status);
            if ("PENDING".equals(status)) query.gt("expires_at",new Date());
            if ("EXPIRED".equals(status)) query.le("expires_at",new Date());
        }
        return page("purchase_order",query,page,size,this::orderSummary);
    }

    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> wallet(Long customerId, Integer page, Integer size, String state) {
        customer(customerId,false);
        QueryWrapper query = q().select("id","merchant_id","coupon_template_id","status","valid_from","valid_to","issued_at","used_at","source_type","rule_snapshot")
                .eq("customer_id",customerId);
        Date now = new Date();
        if (state != null && !state.isBlank()) {
            switch(state) {
                case "USED" -> query.eq("status",1);
                case "VOID" -> query.eq("status",2);
                case "EXPIRED" -> query.eq("status",0).le("valid_to",now);
                case "NOT_STARTED" -> query.eq("status",0).gt("valid_to",now).gt("valid_from",now);
                case "UNUSED" -> query.eq("status",0).gt("valid_to",now).le("valid_from",now);
                default -> throw new CoolException("持券状态无效");
            }
        }
        return page("customer_coupon",query,page,size,r->couponSummary(r,false));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> walletInfo(Long customerId, Long id) {
        Row row = ownedCoupon(customerId,id);
        Map<String,Object> result = couponSummary(row,true);
        result.put("code",row.get("code"));
        result.put("qrCodeImage",row.get("qr_code_image") == null ? qrImage((String)row.get("code")) : row.get("qr_code_image"));
        result.put("qrCodeContent",row.get("code"));
        return result;
    }

    @Transactional(readOnly=true)
    public byte[] walletQr(Long customerId, Long id) {
        Row row = ownedCoupon(customerId,id);
        return qrPng((String)row.get("code"));
    }

    public Map<String,Object> preview(Long operatorId, Long storeId, String code, Long consumptionAmount) {
        return redemption(operatorId,storeId,code,consumptionAmount,false);
    }
    public Map<String,Object> redeem(Long operatorId, Long storeId, String code, Long consumptionAmount) {
        return redemption(operatorId,storeId,code,consumptionAmount,true);
    }
    private Map<String,Object> redemption(Long operatorId, Long storeId, String code, Long consumptionAmount, boolean confirm) {
        var operator = merchants.current(operatorId);
        storeId = staff.requireStore(operator, storeId);
        Long merchantId = operator.getMerchantId();
        require(code != null && code.matches("[a-f0-9]{64}"),"请输入有效券码或扫描本系统二维码");
        positive(storeId);
        Row coupon = mapper.lockCoupon(code,merchantId);
        require(coupon != null,"券不存在或不属于当前商家");
        String state = couponState(coupon,new Date());
        require("UNUSED".equals(state), switch(state) {
            case "USED" -> "该优惠券已核销，请勿重复操作";
            case "EXPIRED" -> "该优惠券已过期";
            case "NOT_STARTED" -> "该优惠券尚未生效";
            default -> "该优惠券已作废";
        });
        customer(number(coupon.get("customer_id")),false);
        Map<String,Object> store = relations.store(storeId);
        require(store != null && number(store.get("merchant_id")) == merchantId && number(store.get("status")) == 1,"请选择本商家营业中的门店");
        JSONObject rule = JSONUtil.parseObj(coupon.get("rule_snapshot"));
        require(number(coupon.get("merchant_id")) == rule.getLong("merchantId"),"持券规则归属异常");
        boolean allStores = Integer.valueOf(0).equals(rule.getInt("scopeType"));
        require(allStores || (rule.getJSONArray("storeIds") != null && rule.getJSONArray("storeIds").toList(Long.class).contains(storeId)),"此券不适用于所选门店");
        BigDecimal discount = discount(rule,consumptionAmount);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("customerCouponId",coupon.get("id")); result.put("couponName",rule.getStr("name"));
        result.put("merchantName",rule.getStr("merchantName")); result.put("storeId",storeId);
        Row storeInfo = mapper.one("store",q().select("name").eq("id",storeId));
        result.put("storeName",storeInfo.get("name")); result.put("consumptionAmount",consumptionAmount);
        result.put("discountAmount",cents(discount)); result.put("contents",rule.get("contentItems"));
        result.put("type",rule.getStr("type"));
        result.put("description",rule.getStr("description"));
        result.put("validFrom",time(coupon.get("valid_from"))); result.put("validTo",time(coupon.get("valid_to")));
        result.put("minSpend",rule.get("minSpend") == null ? null : cents(rule.get("minSpend")));
        result.put("confirmed",confirm);
        if (confirm) {
            Date now = new Date();
            Long receiptId = mapper.create("coupon_redemption",Row.of("merchant_id",merchantId).set("customer_coupon_id",coupon.get("id"))
                    .set("store_id",storeId).set("operator_type","MERCHANT").set("operator_id",operatorId)
                    .set("consumption_amount",consumptionAmount == null ? null : BigDecimal.valueOf(consumptionAmount,2))
                    .set("discount_amount",discount).set("redeemed_at",now));
            mapper.change("customer_coupon",number(coupon.get("id")),Row.of("status",1).set("used_at",now).setRaw("update_time","NOW()"));
            result.put("redemptionId",receiptId); result.put("redeemedAt",time(now));
        }
        return result;
    }

    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> redemptions(Long operatorId, Integer page, Integer size, Long storeId) {
        return redemptions(operatorId,page,size,storeId,"OWNER".equals(merchants.current(operatorId).getMerchantRole()) ? "store" : "self");
    }
    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> redemptions(Long operatorId, Integer page, Integer size, Long storeId, String scope) {
        var operator = merchants.current(operatorId);
        Long merchantId = operator.getMerchantId();
        storeId = staff.requireStore(operator,storeId);
        require(Set.of("self","store").contains(scope),"记录范围无效");
        require(!"store".equals(scope) || Set.of("MANAGER","OWNER").contains(operator.getMerchantRole()),"只有店长可查看门店记录");
        QueryWrapper query = q().eq(CouponRedemptionEntity::getMerchantId,merchantId);
        if ("self".equals(scope)) query.eq(CouponRedemptionEntity::getOperatorId,operatorId).eq(CouponRedemptionEntity::getOperatorType,"MERCHANT");
        if (storeId != null) {
            require(mapper.one("store",q().eq("id",storeId).eq("merchant_id",merchantId)) != null,"门店不存在或无权访问");
            query.eq(CouponRedemptionEntity::getStoreId,storeId);
        }
        return page("coupon_redemption",query,page,size,row->{
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("id",row.get("id")); result.put("customerCouponId",row.get("customer_coupon_id"));
            result.put("storeId",row.get("store_id")); result.put("operatorId",row.get("operator_id"));
            result.put("operatorName",staff.operatorName(number(row.get("operator_id"))));
            result.put("consumptionAmount",row.get("consumption_amount") == null ? null : cents(row.get("consumption_amount")));
            result.put("discountAmount",cents(row.get("discount_amount"))); result.put("redeemedAt",time(row.get("redeemed_at")));
            Row store = mapper.one("store",q().select("name").eq("id",row.get("store_id")));
            result.put("storeName",store == null ? "门店资料已失效" : store.get("name"));
            Row coupon = mapper.one("customer_coupon",q().select("rule_snapshot").eq("id",row.get("customer_coupon_id")));
            result.put("couponName",coupon == null ? "优惠券资料已失效" : JSONUtil.parseObj(coupon.get("rule_snapshot")).getStr("name"));
            return result;
        });
    }

    private CouponTemplateEntity available(Long customerId, Long templateId, int quantity) {
        require(relations.lockTemplate(templateId) != null,"优惠券不存在");
        Map<String,Object> display = discovery.coupon(templateId);
        require("AVAILABLE".equals(display.get("saleState")),"优惠券未开始、已结束或已售罄");
        CouponTemplateEntity coupon = (CouponTemplateEntity)templates.info(templateId);
        templates.validateForPublication(templateId,coupon.getRuleVersion());
        require(coupon.getIssueLimit() == null || quantity <= coupon.getIssueLimit()-coupon.getIssuedCount(),"优惠券库存不足");
        long received = mapper.count("customer_coupon",q().eq("customer_id",customerId).eq("coupon_template_id",templateId));
        require(quantity <= coupon.getPerCustomerLimit()-received,"超出每人累计领取购买上限");
        return coupon;
    }
    private Row ownedCoupon(Long customerId, Long id) {
        customer(customerId,false); positive(id);
        Row row = mapper.one("customer_coupon",q().eq("id",id).eq("customer_id",customerId));
        require(row != null,"优惠券不存在或无权访问"); return row;
    }
    private void customer(Long id, boolean lock) {
        positive(id);
        Row user = lock ? mapper.lockCustomer(id) : mapper.one("user_info",q().select("id","status").eq("id",id));
        require(user != null && number(user.get("status")) == 1,"用户账号不可用");
    }
    private Row item(Long id) {
        List<Row> rows = mapper.rows("purchase_order_item",q().eq("order_id",id).limit(2));
        require(rows.size() == 1,"订单须包含一条有效购券明细"); return rows.get(0);
    }
    private static Date expires(Row order) {
        Date expires = date(order.get("expires_at"));
        return expires != null ? expires : new Date(date(order.get("create_time")).getTime()+15*60_000L);
    }
    private Map<String,Object> orderSummary(Row row) {
        String status = (String)row.get("order_status");
        if ("PENDING".equals(status) && !expires(row).after(new Date())) status = "EXPIRED";
        Map<String,Object> result = new LinkedHashMap<>();
        for(String key:List.of("id","channel")) result.put(key,row.get(key));
        result.put("orderNo",row.get("order_no")); result.put("merchantId",row.get("merchant_id"));
        Row merchant = mapper.one("merchant_info",q().select("name").eq("id",row.get("merchant_id")));
        result.put("merchantName",merchant == null ? "商家资料已失效" : merchant.get("name"));
        result.put("orderStatus",status);
        result.put("orderStatusText",Map.of("PENDING","待支付","PAID","已完成","CANCELLED","已取消","FAILED","支付失败","EXPIRED","已超时").getOrDefault(status,status));
        result.put("paymentStatus",row.get("payment_status")); result.put("totalAmount",cents(row.get("total_amount")));
        result.put("createdAt",time(row.get("create_time"))); result.put("paidAt",time(row.get("paid_at"))); result.put("expiresAt",time(expires(row)));
        return result;
    }
    private Map<String,Object> itemSummary(Row row) {
        JSONObject rule = JSONUtil.parseObj(row.get("rule_snapshot"));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id",row.get("id")); result.put("couponTemplateId",row.get("coupon_template_id")); result.put("couponName",row.get("coupon_name"));
        result.put("unitPrice",cents(row.get("unit_price"))); result.put("quantity",row.get("quantity")); result.put("subtotal",cents(row.get("subtotal")));
        result.put("cover",rule.getStr("cover")); result.put("ruleVersion",rule.getInt("ruleVersion"));
        return result;
    }
    private Map<String,Object> couponSummary(Row row, boolean detail) {
        JSONObject rule = JSONUtil.parseObj(row.get("rule_snapshot")); String state = couponState(row,new Date());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id",row.get("id")); result.put("couponTemplateId",row.get("coupon_template_id"));
        result.put("merchantId",row.get("merchant_id")); result.put("merchantName",rule.getStr("merchantName"));
        result.put("name",rule.getStr("name")); result.put("type",rule.getStr("type")); result.put("cover",rule.getStr("cover"));
        result.put("state",state); result.put("stateText",Map.of("UNUSED","待使用","USED","已使用","VOID","已作废","EXPIRED","已过期","NOT_STARTED","未生效").get(state));
        result.put("validFrom",time(row.get("valid_from"))); result.put("validTo",time(row.get("valid_to")));
        result.put("issuedAt",time(row.get("issued_at"))); result.put("usedAt",time(row.get("used_at"))); result.put("sourceType",row.get("source_type"));
        if (detail) {
            Map<String,Object> publicRule = new LinkedHashMap<>();
            for (String key:List.of("name","type","description","scopeType","storeIds","storeNames","contentItems","ruleVersion","allowCombine","discountRate")) publicRule.put(key,rule.get(key));
            for (String key:List.of("salePrice","minSpend","discountAmount","deductAmount","maxDiscount")) publicRule.put(key,rule.get(key) == null ? null : cents(rule.get(key)));
            result.put("rule",publicRule);
        }
        return result;
    }
    private PageResult<Map<String,Object>> page(String table, QueryWrapper query, Integer page, Integer size, Function<Row,Map<String,Object>> display) {
        int p = page == null ? 1 : page; int s = size == null ? 20 : size;
        require(p >= 1 && s >= 1 && s <= 100,"页码须大于0，每页数量须为1至100");
        var result = mapper.paginate(null,table,new Page<Row>(p,s),query.from(table).orderBy("id",false));
        PageResult<Map<String,Object>> response = new PageResult<>(); response.setList(result.getRecords().stream().map(display).toList());
        response.getPagination().setPage((long)p); response.getPagination().setSize((long)s); response.getPagination().setTotal(result.getTotalRow());
        return response;
    }
    private static void validateDays(JSONObject rule) {
        if ("AFTER_ISSUE".equals(rule.getStr("validityType"))) require(rule.getInt("validDays") != null && rule.getInt("validDays") >= 1 && rule.getInt("validDays") <= 36500,"发券后有效天数须为1至36500");
    }
    private static void positive(Long id) { require(id != null && id > 0,"请提供有效的记录标识"); }
    private static QueryWrapper q() { return QueryWrapper.create(); }
}
