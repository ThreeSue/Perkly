package com.cool.modules.dashboard.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 聚合运营看板的业务数量券类型及近期新增数据
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 23:40:24
 */
public interface DashboardMapper {
    @Select("""
        SELECT
          (SELECT COUNT(*) FROM user_info WHERE phone REGEXP '^1[3-9][0-9]{9}$' AND status IN (0,1)) AS users,
          (SELECT COUNT(*) FROM merchant_info) AS merchants,
          (SELECT COUNT(*) FROM merchant_info WHERE status=1) AS activeMerchants,
          (SELECT COUNT(*) FROM store) AS stores,
          (SELECT COUNT(*) FROM store WHERE status=1) AS activeStores,
          (SELECT COUNT(*) FROM coupon_template) AS coupons,
          (SELECT COUNT(*) FROM coupon_template WHERE status=0) AS draftCoupons,
          (SELECT COUNT(*) FROM coupon_template WHERE status=2) AS disabledCoupons,
          (SELECT COUNT(*) FROM coupon_publication p JOIN coupon_template c ON c.id=p.coupon_template_id AND c.merchant_id=p.merchant_id
             WHERE p.channel='PLATFORM' AND c.status=1 AND p.submitted_version=c.rule_version AND p.audit_status=1) AS pendingCoupons,
          (SELECT COUNT(*) FROM coupon_publication p JOIN coupon_template c ON c.id=p.coupon_template_id AND c.merchant_id=p.merchant_id
             WHERE p.channel='PLATFORM' AND c.status=1 AND p.submitted_version=c.rule_version AND p.audit_status=3 AND p.publish_status<>1) AS rejectedCoupons,
          (SELECT COUNT(*) FROM coupon_publication p JOIN coupon_template c ON c.id=p.coupon_template_id AND c.merchant_id=p.merchant_id
             WHERE p.channel='PLATFORM' AND c.status=1 AND p.submitted_version=c.rule_version AND p.audit_status=2 AND p.publish_status<>1) AS approvedUnpublishedCoupons
        """)
    Map<String, Object> counts();

    @Select("SELECT type, COUNT(*) AS total FROM coupon_template GROUP BY type")
    List<Map<String, Object>> couponTypes();

    @Select("""
        SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS date, 'users' AS kind, COUNT(*) AS total
          FROM user_info WHERE create_time>=#{start} AND create_time<#{end}
          AND phone REGEXP '^1[3-9][0-9]{9}$' AND status IN (0,1) GROUP BY DATE(create_time)
        UNION ALL
        SELECT DATE_FORMAT(create_time,'%Y-%m-%d'), 'merchants', COUNT(*) FROM merchant_info
          WHERE create_time>=#{start} AND create_time<#{end} GROUP BY DATE(create_time)
        UNION ALL
        SELECT DATE_FORMAT(create_time,'%Y-%m-%d'), 'stores', COUNT(*) FROM store
          WHERE create_time>=#{start} AND create_time<#{end} GROUP BY DATE(create_time)
        UNION ALL
        SELECT DATE_FORMAT(create_time,'%Y-%m-%d'), 'coupons', COUNT(*) FROM coupon_template
          WHERE create_time>=#{start} AND create_time<#{end} GROUP BY DATE(create_time)
        """)
    List<Map<String, Object>> trends(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
