import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以只读事务核查画像来源实际结构及汇总质量且不导出用户明细
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 15:59:15
 */
public class ProfileSourceAudit {
    public static void main(String[] args) throws Exception {
        Map<?,?> root=new Yaml().load(Files.readString(Path.of("src/main/resources/application-local.yml")));
        Map<?,?> source=(Map<?,?>)((Map<?,?>)root.get("spring")).get("datasource");
        String url=Objects.toString(source.get("url"));
        if(!url.startsWith("jdbc:mysql://127.0.0.1:"))throw new IllegalStateException("Only local database audit is allowed");
        StringBuilder out=new StringBuilder("# 用户画像来源只读审计\n\n执行时间："+java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))+"\n\n仅结构与聚合，不输出账号、电话、券码、订单号。\n");
        try(Connection c=DriverManager.getConnection(url,Objects.toString(source.get("username")),Objects.toString(source.get("password")))) {
            c.setReadOnly(true);c.setAutoCommit(false);
            out.append("\n数据库：").append(c.getCatalog()).append("\n");
            for(String table:List.of("user_info","purchase_order","purchase_order_item","customer_coupon","consumption_order","wallet_bill","wallet_transaction","coupon_redemption","completed_visit","merchant_info","store","merchant_category","store_type","coupon_template")) {
                Set<String> columns=new LinkedHashSet<>();out.append("\n## ").append(table).append("\n\n");
                try(ResultSet rs=c.getMetaData().getColumns(c.getCatalog(),null,table,null)) {
                    while(rs.next()){columns.add(rs.getString("COLUMN_NAME"));out.append("- ").append(rs.getString("COLUMN_NAME")).append(" ").append(rs.getString("TYPE_NAME")).append(" nullable=").append(rs.getInt("NULLABLE")).append("\n");}
                }
                if(columns.isEmpty()){out.append("表不存在。\n");continue;}
                scalar(c,out,"总行数","SELECT COUNT(*) FROM `"+table+"`");
                for(String col:List.of("customer_id","consumption_amount","amount","completed_at","redeemed_at","settlement_bill_id","consumption_order_id"))
                    if(columns.contains(col))scalar(c,out,col+" 空值行数","SELECT COUNT(*) FROM `"+table+"` WHERE `"+col+"` IS NULL");
                for(String col:List.of("status","order_status","payment_status"))if(columns.contains(col))
                    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT `"+col+"`,COUNT(*) FROM `"+table+"` GROUP BY `"+col+"`")){
                        while(r.next())out.append("\n").append(col).append("=").append(r.getString(1)).append(": ").append(r.getLong(2)).append("\n");
                    }
            }
            out.append("\n## 身份关联、去重和金额质量\n");
            scalar(c,out,"购券订单顾客不存在","SELECT COUNT(*) FROM purchase_order p LEFT JOIN user_info u ON u.id=p.customer_id WHERE u.id IS NULL");
            scalar(c,out,"持券顾客不存在","SELECT COUNT(*) FROM customer_coupon p LEFT JOIN user_info u ON u.id=p.customer_id WHERE u.id IS NULL");
            scalar(c,out,"完成消费缺可关联持券顾客","SELECT COUNT(*) FROM consumption_order o LEFT JOIN customer_coupon c ON c.id=o.customer_coupon_id LEFT JOIN user_info u ON u.id=c.customer_id WHERE o.status='COMPLETED' AND u.id IS NULL");
            scalar(c,out,"核销缺可关联顾客","SELECT COUNT(*) FROM coupon_redemption r LEFT JOIN customer_coupon c ON c.id=r.customer_coupon_id LEFT JOIN user_info u ON u.id=c.customer_id WHERE u.id IS NULL");
            scalar(c,out,"核销已关联完成消费","SELECT COUNT(*) FROM coupon_redemption r JOIN consumption_order o ON o.id=r.consumption_order_id WHERE o.status='COMPLETED'");
            scalar(c,out,"核销和消费金额已知但换算后不一致","SELECT COUNT(*) FROM coupon_redemption r JOIN consumption_order o ON o.id=r.consumption_order_id WHERE r.consumption_amount IS NOT NULL AND o.consumption_amount IS NOT NULL AND r.consumption_amount*100<>o.consumption_amount");
            scalar(c,out,"重复核销同一持券的多余记录","SELECT COALESCE(SUM(n-1),0) FROM (SELECT COUNT(*) n FROM coupon_redemption GROUP BY customer_coupon_id HAVING COUNT(*)>1) x");
            scalar(c,out,"成功免费领取订单","SELECT COUNT(*) FROM purchase_order WHERE order_status='PAID' AND payment_status='SUCCESS' AND total_amount=0");
            scalar(c,out,"成功付费购券订单","SELECT COUNT(*) FROM purchase_order WHERE order_status='PAID' AND payment_status='SUCCESS' AND total_amount>0");
            scalar(c,out,"成功购券金额或时间未知","SELECT COUNT(*) FROM purchase_order WHERE order_status='PAID' AND (total_amount IS NULL OR paid_at IS NULL)");
            scalar(c,out,"持券无购券明细来源","SELECT COUNT(*) FROM customer_coupon WHERE order_item_id IS NULL");
            scalar(c,out,"门店缺坐标","SELECT COUNT(*) FROM store WHERE longitude IS NULL OR latitude IS NULL");
            c.rollback();
        }
        Path target=Path.of("docs/用户画像来源只读审计-20261110.md");Files.writeString(target,out.toString());
        System.out.println("Read-only audit written to "+target);
    }
    static void scalar(Connection c,StringBuilder out,String label,String sql)throws SQLException{
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();out.append("\n").append(label).append(": ").append(r.getLong(1)).append("\n");}
    }
}
