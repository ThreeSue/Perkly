package com.cool.modules.coupon.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryTable;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.row.RowMapper;
import org.apache.ibatis.annotations.Select;
import static com.mybatisflex.core.query.QueryMethods.number;
import static com.mybatisflex.core.query.QueryMethods.select;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在框架同一数据库会话内维护优惠券门店关联和引用校验
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 10:30:59
 */
public interface CouponTemplateRelationsMapper extends RowMapper {
    // 两条锁查询保留显式锁语义；普通查询和关联写入通过框架RowMapper执行，复用同一SqlSession事务。
    @Select("SELECT id FROM coupon_template WHERE id=#{id} FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    Long lockTemplate(Long id);

    default List<Long> storeIds(Long id) {
        if (id == null) return List.of();
        QueryWrapper query = QueryWrapper.create().select("store_id").from("coupon_store")
                .eq("coupon_template_id", id).orderBy("store_id", true);
        return selectObjectListByQuery(null, "coupon_store", query).stream()
                .map(value -> ((Number) value).longValue()).toList();
    }

    @Select("SELECT merchant_id,status FROM store WHERE id=#{id} FOR SHARE")
    Map<String, Object> store(Long id);

    default int deleteStores(Long id) {
        if (id == null) return 0;
        return deleteByQuery(null, "coupon_store", QueryWrapper.create().eq("coupon_template_id", id));
    }

    default int insertStore(Long merchantId, Long templateId, Long storeId) {
        Row row = Row.of("merchant_id", merchantId).set("coupon_template_id", templateId)
                .set("store_id", storeId).setRaw("create_time", "NOW()");
        return insert(null, "coupon_store", row);
    }

    default long activePublications(Long id) {
        if (id == null) return 0;
        QueryWrapper query = QueryWrapper.create().from("coupon_publication").eq("coupon_template_id", id)
                .and(new QueryColumn("audit_status").eq(1).or(new QueryColumn("publish_status").eq(1)));
        return selectCountByQuery(null, "coupon_publication", query);
    }

    default long validContents(Long id, Long merchantId) {
        if (id == null || merchantId == null) return 0;
        QueryWrapper query = QueryWrapper.create().from("coupon_product").eq("coupon_template_id", id)
                .eq("merchant_id", merchantId).eq("relation_type", 2).gt("quantity", 0);
        return selectCountByQuery(null, "coupon_product", query);
    }

    default long contents(Long id) {
        if (id == null) return 0;
        return selectCountByQuery(null, "coupon_product", QueryWrapper.create().from("coupon_product")
                .eq("coupon_template_id", id).eq("relation_type", 2));
    }

    default long references(Long id) {
        if (id == null) return 0;
        // UNION ALL保留各表每一条引用；在同一条查询中统计，不引入多次往返或重复去重。
        QueryWrapper references = referenceRows("coupon_publication", id)
                .unionAll(referenceRows("coupon_product", id))
                .unionAll(referenceRows("coupon_issue_batch", id))
                .unionAll(referenceRows("customer_coupon", id));
        return selectCountByQuery(null, "coupon_publication", QueryWrapper.create().from(references).as("refs"));
    }

    default List<Map<String, Object>> merchantNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        ids = ids.stream().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return List.of();
        QueryWrapper query = QueryWrapper.create().select("id", "name").from("merchant_info").in("id", ids);
        return selectListByQuery(null, "merchant_info", query).stream()
                .map(row -> (Map<String, Object>) row).toList();
    }

    default List<Map<String, Object>> storeNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        ids = ids.stream().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return List.of();
        QueryTable relation = new QueryTable("coupon_store").as("cs");
        QueryTable store = new QueryTable("store").as("s");
        QueryColumn templateId = new QueryColumn(relation, "coupon_template_id");
        QueryColumn storeId = new QueryColumn(relation, "store_id");
        QueryWrapper query = select(templateId, storeId, new QueryColumn(store, "name"))
                .from(relation).leftJoin(store)
                .on(new QueryColumn(store, "id").eq(storeId)
                        .and(new QueryColumn(store, "merchant_id").eq(new QueryColumn(relation, "merchant_id"))))
                .where(templateId.in(ids)).orderBy(storeId.asc());
        return selectListByQuery(null, "coupon_store", query).stream()
                .map(row -> (Map<String, Object>) row).toList();
    }

    private static QueryWrapper referenceRows(String table, Long id) {
        return select(number(1)).from(table).eq("coupon_template_id", id);
    }
}

