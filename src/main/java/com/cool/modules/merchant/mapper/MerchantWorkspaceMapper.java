package com.cool.modules.merchant.mapper;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.row.RowMapper;
import java.util.List;
import java.util.Set;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用MyBatis-Flex查询构造器完成商家工作区投影分页和归属锁定
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:24:46
 */
public interface MerchantWorkspaceMapper extends RowMapper {
    default Row profile(Long merchantId) {
        return selectOneByQuery(null, "merchant_info", QueryWrapper.create()
                .select(column("name"), column("logo"), column("introduction"),
                        column("contact_name").as("contactName"), column("contact_phone").as("contactPhone"),
                        column("address"), column("status"))
                .from("merchant_info").eq("id", merchantId));
    }

    default List<Row> storeOptions(Long merchantId) {
        return selectListByQuery(null, "store", QueryWrapper.create().select("id", "name", "status")
                .from("store").eq("merchant_id", merchantId).orderBy("name", true).orderBy("id", true));
    }

    default Page<Row> orderPage(Long merchantId, int page, int size) {
        return paginate(null, "purchase_order", new Page<>(page, size), orderQuery(merchantId).orderBy("id", false));
    }

    default Row order(Long merchantId, Long id) {
        return selectOneByQuery(null, "purchase_order", orderQuery(merchantId).eq("id", id));
    }

    default List<Row> orderItems(Long merchantId, Long orderId) {
        return selectListByQuery(null, "purchase_order_item", QueryWrapper.create()
                .select(column("coupon_name").as("couponName"), column("unit_price").as("unitPrice"),
                        column("quantity"), column("subtotal"))
                .from("purchase_order_item").eq("order_id", orderId).eq("merchant_id", merchantId));
    }

    default boolean lockOwned(String table, Long id, Long merchantId) {
        if (!Set.of("store", "coupon_template", "purchase_order").contains(table)) return false;
        return selectOneByQuery(null, table, QueryWrapper.create().select("id").from(table)
                .eq("id", id).eq("merchant_id", merchantId).forUpdate()) != null;
    }

    private static QueryWrapper orderQuery(Long merchantId) {
        return QueryWrapper.create().select(column("id"), column("order_no").as("orderNo"), column("channel"),
                        column("order_status").as("orderStatus"), column("payment_status").as("paymentStatus"),
                        column("total_amount").as("totalAmount"), column("paid_at").as("paidAt"),
                        column("create_time").as("createTime"))
                .from("purchase_order").eq("merchant_id", merchantId);
    }

    private static QueryColumn column(String name) { return new QueryColumn(name); }
}
