package com.cool.modules.merchant.mapper.query;

import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryTable;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.RawQueryColumn;
import static com.mybatisflex.core.query.QueryMethods.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用MyBatis-Flex构造公开门店和优惠券的关联筛选与投影
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:20:06
 */
public final class AppDiscoveryQueries {
    private static final QueryTable STORE = new QueryTable("store").as("s");
    private static final QueryTable MERCHANT = new QueryTable("merchant_info").as("m");
    private static final QueryTable CATEGORY = new QueryTable("merchant_category").as("mc");
    private static final QueryTable COUPON = new QueryTable("coupon_template").as("c");
    private static final QueryTable PUBLICATION = new QueryTable("coupon_publication").as("p");
    private static final QueryTable COUPON_STORE = new QueryTable("coupon_store").as("cs");
    private static final QueryTable CONTENT = new QueryTable("coupon_product").as("cp");

    private AppDiscoveryQueries() {}

    public static QueryWrapper stores(DiscoveryPageReq req) {
        QueryWrapper query = storeBase();
        filters(query, req);
        if (hasKeyword(req)) {
            query.and(contains("s.name", req.getKeyword())
                    .or(contains("m.name", req.getKeyword()))
                    .or(contains("s.address", req.getKeyword())));
        }
        return query.orderBy(col(STORE, "id").desc());
    }

    public static QueryWrapper store(Long id) {
        return storeBase().select(field(MERCHANT, "introduction", "merchantIntroduction"))
                .and(col(STORE, "id").eq(id));
    }

    public static QueryWrapper coupons(DiscoveryPageReq req) {
        QueryWrapper query = couponBase();
        filters(query, req);
        if (hasKeyword(req)) {
            query.and(contains("c.name", req.getKeyword()).or(contains("m.name", req.getKeyword())));
        }
        if (req.getType() != null) query.and(col(COUPON, "type").eq(req.getType()));
        if (req.getStoreId() != null) query.and(exists(usableStores(req.getStoreId())));
        return query.orderBy(col(PUBLICATION, "published_at").desc(), col(COUPON, "id").desc());
    }

    public static QueryWrapper coupon(Long id) {
        return couponBase().and(col(COUPON, "id").eq(id));
    }

    public static QueryWrapper applicableStores(Long id) {
        QueryWrapper coupon = select(number(1)).from(COUPON)
                .where(col(COUPON, "id").eq(id))
                .and(col(COUPON, "merchant_id").eq(col(STORE, "merchant_id")))
                .and(appliesTo(STORE));
        return storeBase().and(exists(coupon)).orderBy(col(STORE, "id").asc());
    }

    public static QueryWrapper contents(Long id) {
        return select(field(CONTENT, "product_id", "productId"),
                field(CONTENT, "relation_type", "relationType"), col(CONTENT, "quantity"),
                field(CONTENT, "item_description", "description"))
                .from(CONTENT).innerJoin(COUPON)
                .on(col(COUPON, "id").eq(col(CONTENT, "coupon_template_id"))
                        .and(col(COUPON, "merchant_id").eq(col(CONTENT, "merchant_id"))))
                .where(col(COUPON, "id").eq(id)).orderBy(col(CONTENT, "id").asc());
    }

    private static QueryWrapper storeBase() {
        return select(col(STORE, "id"), col(STORE, "name"),
                field(STORE, "merchant_id", "merchantId"), field(MERCHANT, "name", "merchantName"),
                field(MERCHANT, "logo", "merchantLogo"),
                case_().when(col(STORE, "cover").isNull(true).or(trim(col(STORE, "cover")).eq("")))
                        .then(col(MERCHANT, "logo")).else_(col(STORE, "cover")).end().as("cover"),
                field(MERCHANT, "category_id", "categoryId"), field(CATEGORY, "name", "categoryName"),
                col(STORE, "address"), col(STORE, "phone"),
                field(STORE, "business_hours", "businessHours"), col(STORE, "longitude"),
                col(STORE, "latitude"), col(STORE, "status"), string("营业中").as("statusText"))
                .from(STORE).innerJoin(MERCHANT)
                .on(col(MERCHANT, "id").eq(col(STORE, "merchant_id")))
                .leftJoin(CATEGORY).on(col(CATEGORY, "id").eq(col(MERCHANT, "category_id")))
                .where(col(STORE, "status").eq(1)).and(col(MERCHANT, "status").eq(1));
    }

    private static QueryWrapper couponBase() {
        return select(col(COUPON, "id"), field(PUBLICATION, "id", "publicationId"),
                field(COUPON, "merchant_id", "merchantId"), field(MERCHANT, "name", "merchantName"),
                field(MERCHANT, "logo", "merchantLogo"), field(MERCHANT, "category_id", "categoryId"),
                field(CATEGORY, "name", "categoryName"), col(COUPON, "name"), col(COUPON, "type"),
                col(COUPON, "cover"), col(COUPON, "description"), field(COUPON, "sale_price", "salePrice"),
                field(COUPON, "scope_type", "scopeType"), field(COUPON, "min_spend", "minSpend"),
                field(COUPON, "discount_amount", "discountAmount"), field(COUPON, "deduct_amount", "deductAmount"),
                field(COUPON, "max_discount", "maxDiscount"), field(COUPON, "discount_rate", "discountRate"),
                field(COUPON, "issue_limit", "issueLimit"), field(COUPON, "issued_count", "issuedCount"),
                remainingStock().as("remainingStock"), field(COUPON, "per_customer_limit", "perCustomerLimit"),
                field(COUPON, "allow_combine", "allowCombine"), field(COUPON, "validity_type", "validityType"),
                field(COUPON, "valid_days", "validDays"), date("sale_start_time", "saleStartTime"),
                date("sale_end_time", "saleEndTime"), date("valid_from", "validFrom"), date("valid_to", "validTo"),
                saleState().as("saleState"))
                .from(COUPON).innerJoin(MERCHANT)
                .on(col(MERCHANT, "id").eq(col(COUPON, "merchant_id")))
                .innerJoin(PUBLICATION).on(col(PUBLICATION, "coupon_template_id").eq(col(COUPON, "id"))
                        .and(col(PUBLICATION, "merchant_id").eq(col(COUPON, "merchant_id"))))
                .leftJoin(CATEGORY).on(col(CATEGORY, "id").eq(col(MERCHANT, "category_id")))
                .where(col(COUPON, "status").eq(1)).and(col(MERCHANT, "status").eq(1))
                .and(col(PUBLICATION, "channel").eq("PLATFORM"))
                .and(col(PUBLICATION, "audit_status").eq(2)).and(col(PUBLICATION, "publish_status").eq(1))
                .and(col(PUBLICATION, "published_version").eq(col(COUPON, "rule_version")))
                .and(exists(usableStores(null)));
    }

    private static QueryWrapper usableStores(Long id) {
        QueryTable usable = new QueryTable("store").as("usable");
        QueryWrapper query = select(number(1)).from(usable)
                .where(col(usable, "merchant_id").eq(col(COUPON, "merchant_id")))
                .and(col(usable, "status").eq(1)).and(appliesTo(usable));
        if (id != null) query.and(col(usable, "id").eq(id));
        return query;
    }

    private static QueryCondition appliesTo(QueryTable store) {
        QueryWrapper relation = select(number(1)).from(COUPON_STORE)
                .where(col(COUPON_STORE, "coupon_template_id").eq(col(COUPON, "id")))
                .and(col(COUPON_STORE, "merchant_id").eq(col(COUPON, "merchant_id")))
                .and(col(COUPON_STORE, "store_id").eq(col(store, "id")));
        return col(COUPON, "scope_type").eq(0)
                .or(col(COUPON, "scope_type").eq(1).and(exists(relation)));
    }

    private static QueryColumn remainingStock() {
        QueryColumn limit = col(COUPON, "issue_limit");
        QueryColumn remaining = limit.subtract(col(COUPON, "issued_count"));
        return case_().when(limit.isNull(true)).then(new RawQueryColumn("NULL"))
                .when(remaining.lt(0)).then(0).else_(remaining).end();
    }

    private static QueryColumn saleState() {
        QueryCondition ended = col(COUPON, "sale_end_time").le(now())
                .or(col(COUPON, "validity_type").eq("FIXED").and(col(COUPON, "valid_to").le(now())));
        QueryCondition soldOut = col(COUPON, "issue_limit").isNotNull(true)
                .and(col(COUPON, "issued_count").ge(col(COUPON, "issue_limit")));
        return case_().when(ended).then("ENDED").when(soldOut).then("SOLD_OUT")
                .when(col(COUPON, "sale_start_time").gt(now())).then("NOT_STARTED")
                .else_("AVAILABLE").end();
    }

    private static void filters(QueryWrapper query, DiscoveryPageReq req) {
        if (req.getMerchantId() != null) query.and(col(MERCHANT, "id").eq(req.getMerchantId()));
        if (req.getCategoryId() != null) query.and(col(MERCHANT, "category_id").eq(req.getCategoryId())
                .or(col(CATEGORY, "parent_id").eq(req.getCategoryId())));
    }

    private static boolean hasKeyword(DiscoveryPageReq req) {
        return req.getKeyword() != null && !req.getKeyword().isBlank();
    }

    private static QueryCondition contains(String column, String value) {
        // 保留原接口的字面包含搜索：仅列名来自本类固定常量，用户输入始终参数绑定。
        String pattern = "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return raw(column + " LIKE ? ESCAPE '!'", pattern);
    }

    private static QueryColumn date(String name, String alias) {
        return dateFormat(col(COUPON, name), "%Y-%m-%d %H:%i:%s").as(alias);
    }

    private static QueryColumn field(QueryTable table, String name, String alias) {
        return col(table, name).as(alias);
    }

    private static QueryColumn col(QueryTable table, String name) {
        return new QueryColumn(table, name);
    }
}
