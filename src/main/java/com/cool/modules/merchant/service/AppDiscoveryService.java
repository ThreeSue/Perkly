package com.cool.modules.merchant.service;

import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.mapper.query.AppDiscoveryQueries;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 复用现有业务表提供公开门店和平台已发布优惠券的只读查询
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:04:27
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppDiscoveryService {
    private final MerchantStoreMapper stores;
    private final CouponTemplateMapper coupons;

    public PageResult<Map<String, Object>> stores(DiscoveryPageReq req) {
        req.validate();
        return page(stores, AppDiscoveryQueries.stores(req), req);
    }

    public Map<String, Object> store(Long id) {
        positive(id);
        return one(stores, AppDiscoveryQueries.store(id), "门店不存在或暂不可展示");
    }

    public PageResult<Map<String, Object>> coupons(DiscoveryPageReq req) {
        req.validate();
        PageResult<Map<String, Object>> result = page(coupons, AppDiscoveryQueries.coupons(req), req);
        result.getList().forEach(this::couponDisplay);
        return result;
    }

    public Map<String, Object> coupon(Long id) {
        positive(id);
        Map<String, Object> row = one(coupons, AppDiscoveryQueries.coupon(id), "优惠券不存在或暂不可展示");
        couponDisplay(row);
        row.put("stores", list(stores, AppDiscoveryQueries.applicableStores(id)));
        row.put("contents", list(coupons, AppDiscoveryQueries.contents(id)));
        return row;
    }

    private PageResult<Map<String, Object>> page(BaseMapper<?> mapper, QueryWrapper query, DiscoveryPageReq req) {
        Page<Row> page = mapper.paginateAs(req.getPage(), req.getSize(), query, Row.class);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setList(page.getRecords().stream().map(row -> publicFields(row, query)).toList());
        result.getPagination().setPage(page.getPageNumber());
        result.getPagination().setSize(page.getPageSize());
        result.getPagination().setTotal(page.getTotalRow());
        return result;
    }

    private List<Map<String, Object>> list(BaseMapper<?> mapper, QueryWrapper query) {
        return mapper.selectRowsByQuery(query).stream().map(row -> publicFields(row, query)).toList();
    }

    private Map<String, Object> one(BaseMapper<?> mapper, QueryWrapper query, String message) {
        List<Map<String, Object>> rows = list(mapper, query);
        if (rows.isEmpty()) throw new CoolException(message);
        return rows.get(0);
    }

    private Map<String, Object> publicFields(Row row, QueryWrapper query) {
        // MyBatis可能省略NULL列；按投影补齐键，维持原JDBC接口的字段名和null值。
        Map<String, Object> result = new LinkedHashMap<>();
        for (QueryColumn column : CPI.getSelectColumns(query)) {
            String key = column.getAlias() == null ? column.getName() : column.getAlias();
            result.put(key, row.get(key));
        }
        return result;
    }

    private void couponDisplay(Map<String, Object> row) {
        for (String key : List.of("salePrice", "minSpend", "discountAmount", "deductAmount", "maxDiscount")) {
            Object value = row.get(key);
            if (value != null) row.put(key, new BigDecimal(value.toString()).movePointRight(2).longValueExact());
        }
        row.put("typeText", switch (String.valueOf(row.get("type"))) {
            case "FULL_REDUCTION" -> "满减券";
            case "DISCOUNT" -> "折扣券";
            case "CASH" -> "代金券";
            case "PACKAGE" -> "套餐券";
            case "EXPERIENCE" -> "体验券";
            case "EXCHANGE" -> "兑换券";
            default -> "优惠券";
        });
        row.put("saleStateText", switch (String.valueOf(row.get("saleState"))) {
            case "ENDED" -> "已结束";
            case "SOLD_OUT" -> "已售罄";
            case "NOT_STARTED" -> "未开始";
            default -> "可领购";
        });
    }

    private static void positive(Long id) {
        if (id == null || id < 1) throw new CoolException("请提供有效的ID");
    }
}
