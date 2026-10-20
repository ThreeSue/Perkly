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
    default List<Row> publications(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return selectListByQuery(null, "coupon_publication", QueryWrapper.create()
                .select("coupon_template_id", "audit_status", "publish_status", "submitted_version", "published_version", "submitted_at", "published_at")
                .from("coupon_publication").in("coupon_template_id", ids).eq("channel", "PLATFORM"));
    }

    default Row publicationState(Long id) {
        if (id == null) return new Row();
        List<Row> rows = selectListByQuery(null, "coupon_publication", QueryWrapper.create()
                .from("coupon_publication").eq("coupon_template_id", id).eq("channel", "PLATFORM"));
        return rows.isEmpty() ? new Row() : rows.get(0);
    }

    default void savePublication(Long merchantId, Long templateId, Row changes) {
        QueryWrapper query = QueryWrapper.create().eq("coupon_template_id", templateId).eq("channel", "PLATFORM");
        changes.setRaw("update_time", "NOW()");
        if (selectCountByQuery(null, "coupon_publication", QueryWrapper.create().from("coupon_publication")
                .eq("coupon_template_id", templateId).eq("channel", "PLATFORM")) == 0) {
            changes.set("merchant_id", merchantId).set("coupon_template_id", templateId)
                    .set("channel", "PLATFORM").setRaw("create_time", "NOW()");
            insert(null, "coupon_publication", changes);
        } else {
            updateByQuery(null, "coupon_publication", changes, query);
        }
    }

    default long usableStoreCount(Long merchantId, Integer scopeType, Collection<Long> storeIds) {
        if (merchantId == null || (Integer.valueOf(1).equals(scopeType) && (storeIds == null || storeIds.isEmpty()))) return 0;
        QueryWrapper query = QueryWrapper.create().from("store").eq("merchant_id", merchantId).eq("status", 1);
        if (Integer.valueOf(1).equals(scopeType)) query.in("id", storeIds);
        return selectCountByQuery(null, "store", query);
    }

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
        QueryWrapper query = QueryWrapper.create().from(new QueryTable("coupon_product").as("cp"))
                .innerJoin(new QueryTable("product").as("p")).on("p.id=cp.product_id AND p.merchant_id=cp.merchant_id")
                .eq("cp.coupon_template_id", id).eq("cp.merchant_id", merchantId)
                .eq("cp.relation_type", 2).gt("cp.quantity", 0).eq("p.status", 1);
        return selectCountByQuery(null, "coupon_product", query);
    }

    default List<Map<String, Object>> contentRows(Long id) {
        if (id == null) return List.of();
        return contentRows(List.of(id));
    }

    default List<Map<String, Object>> contentRows(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return selectListByQuery(null, "coupon_product", QueryWrapper.create()
                .select("cp.coupon_template_id", "cp.product_id", "cp.quantity", "cp.item_description", "p.name", "p.type", "p.status")
                .from(new QueryTable("coupon_product").as("cp")).leftJoin(new QueryTable("product").as("p"))
                .on("p.id=cp.product_id AND p.merchant_id=cp.merchant_id")
                .in("cp.coupon_template_id", ids).eq("cp.relation_type", 2).orderBy("cp.id", true))
                .stream().map(row -> (Map<String, Object>) row).toList();
    }

    @Select("SELECT id,merchant_id,name,type,status FROM product WHERE id=#{id} FOR SHARE")
    @org.apache.ibatis.annotations.Options(useCache = false, flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    Map<String, Object> product(Long id);

    default List<Map<String, Object>> products(Long merchantId) {
        return selectListByQuery(null, "product", QueryWrapper.create().select("id", "name", "type")
                .from("product").eq("merchant_id", merchantId).eq("status", 1).orderBy("id", true))
                .stream().map(row -> (Map<String, Object>) row).toList();
    }

    default Long insertProduct(Long merchantId, String name, String type) {
        Row row = Row.ofKey(com.mybatisflex.core.row.RowKey.AUTO).set("merchant_id", merchantId).set("name", name).set("type", type).set("status", 1)
                .setRaw("create_time", "NOW()").setRaw("update_time", "NOW()");
        insertProductRow(row);
        return ((Number) row.get("id")).longValue();
    }

    @org.apache.ibatis.annotations.Insert("INSERT INTO product(merchant_id,name,type,status,create_time,update_time) VALUES(#{row.merchant_id},#{row.name},#{row.type},1,NOW(),NOW())")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "row.id", keyColumn = "id")
    int insertProductRow(@org.apache.ibatis.annotations.Param("row") Row row);

    default void replaceContents(Long merchantId, Long id,
            List<com.cool.modules.coupon.controller.admin.params.CouponContentParam> items) {
        deleteByQuery(null, "coupon_product", QueryWrapper.create().eq("coupon_template_id", id).eq("relation_type", 2));
        for (var item : items) {
            if (item.getProductId() == null) item.setProductId(insertProduct(merchantId, item.getName(), item.getType()));
            insert(null, "coupon_product", Row.of("merchant_id", merchantId).set("coupon_template_id", id)
                    .set("product_id", item.getProductId()).set("relation_type", 2).set("quantity", item.getQuantity())
                    .set("item_description", item.getItemDescription()).setRaw("create_time", "NOW()"));
        }
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

