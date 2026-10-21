package com.cool.modules.coupon.mapper;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.row.RowKey;
import com.mybatisflex.core.row.RowMapper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import com.mybatisflex.core.provider.RowSqlProvider;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用同一MyBatis会话读写购券订单持券及核销流水并锁定业务行
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:14:30
 */
public interface CouponTradeMapper extends RowMapper {
    Set<String> TABLES = Set.of("purchase_order", "purchase_order_item", "customer_coupon", "coupon_redemption",
            "coupon_template", "coupon_store", "coupon_publication", "merchant_info", "store", "user_info");

    @Select("SELECT id,status FROM user_info WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Row lockCustomer(Long id);

    @Select("SELECT * FROM purchase_order WHERE id=#{id} AND customer_id=#{customerId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Row lockOrder(Long id, Long customerId);

    @Select("SELECT * FROM customer_coupon WHERE code=#{code} AND merchant_id=#{merchantId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Row lockCoupon(String code, Long merchantId);

    // 子接口的通用Row插入须显式声明JDBC主键回填，SQL仍由Flex的Provider生成。
    @InsertProvider(type=RowSqlProvider.class, method="insert")
    @Options(useGeneratedKeys=true,keyProperty="$$row.id",keyColumn="id")
    int insertReturningId(@Param("$$schemaName") String schema, @Param("$$tableName") String table, @Param("$$row") Row row);

    default List<Row> rows(String table, QueryWrapper query) {
        check(table);
        return selectListByQuery(null, table, query.from(table));
    }

    default Row one(String table, QueryWrapper query) {
        var rows = rows(table, query.limit(1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    default long count(String table, QueryWrapper query) {
        check(table);
        return selectCountByQuery(null, table, query.from(table));
    }

    default Long create(String table, Row values) {
        check(table);
        Row row = Row.ofKey(RowKey.AUTO); row.putAll(values);
        row.setRaw("create_time", "NOW()");
        insertReturningId(null, table, row);
        Object id = row.get("id");
        if (!(id instanceof Number)) throw new IllegalStateException("未能取得业务记录主键");
        return ((Number) id).longValue();
    }

    default void change(String table, Long id, Row values) {
        check(table);
        if (id == null || id <= 0) throw new IllegalArgumentException("无效记录标识");
        if (updateByQuery(null, table, values, QueryWrapper.create().eq("id", id)) != 1) {
            throw new IllegalStateException("业务记录更新失败");
        }
    }

    default boolean consumeStock(Long templateId, long issuedCount, int quantity) {
        return updateByQuery(null,"coupon_template",
                Row.of("issued_count",issuedCount+quantity).setRaw("update_time","NOW()"),
                QueryWrapper.create().eq("id",templateId).eq("issued_count",issuedCount)) == 1;
    }

    private static void check(String table) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("不支持的业务表");
    }
}
