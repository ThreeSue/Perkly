import java.nio.file.*;
import java.sql.*;
import java.util.*;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import java.math.BigDecimal;

/** Local-only fixture helper. Reads connection settings without exporting credentials. */
public class LocalSeedData {
    static Connection connect(Path backend) throws Exception {
        Map<String,String> config = new HashMap<>();
        for (String line : Files.readAllLines(backend.resolve("src/main/resources/application-local.yml"))) {
            String value = line.strip();
            if (value.startsWith("#")) continue;
            for (String key : List.of("url", "username", "password")) {
                if (value.startsWith(key + ":") && !config.containsKey(key)) {
                    String raw = value.substring(key.length() + 1).strip();
                    if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) raw = raw.substring(1, raw.length()-1);
                    config.put(key, raw);
                }
            }
        }
        String url = config.get("url");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:")) throw new IllegalStateException("Only the configured localhost MySQL test database is allowed");
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, config.get("username"), config.get("password"));
    }
    static List<Map<String,Object>> rows(Connection db, String sql, Object... params) throws Exception {
        try (var st = db.prepareStatement(sql)) {
            for (int i=0;i<params.length;i++) st.setObject(i+1, params[i]);
            try (var rs = st.executeQuery()) {
                List<Map<String,Object>> list = new ArrayList<>();
                var md = rs.getMetaData();
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    for(int i=1;i<=md.getColumnCount();i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                    list.add(row);
                }
                return list;
            }
        }
    }
    static long insert(Connection db, Map<String,List<Map<String,Object>>> archive, String table, LinkedHashMap<String,Object> values) throws Exception {
        String sql = "INSERT INTO `" + table + "` (`" + String.join("`,`", values.keySet()) + "`) VALUES (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
        long id;
        try (var statement = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            for (Object value : values.values()) statement.setObject(index++, value);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Insert failed for " + table);
            try (var keys = statement.getGeneratedKeys()) { if (!keys.next()) throw new IllegalStateException("Missing generated ID"); id = keys.getLong(1); }
        }
        archive.computeIfAbsent(table, ignored -> new ArrayList<>()).add(rows(db, "SELECT * FROM `" + table + "` WHERE id=?", id).get(0));
        return id;
    }
    static LinkedHashMap<String,Object> values(Object... pairs) {
        LinkedHashMap<String,Object> values = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) values.put((String)pairs[i],pairs[i+1]);
        return values;
    }
    static String literal(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        // MySQL hex string avoids SQL mode dependent quote/backslash escaping.
        return "CONVERT(0x" + HexFormat.of().formatHex(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + " USING utf8mb4)";
    }
    static void seed(Connection db, Path archive) throws Exception {
        if (Files.exists(archive.resolve("manifest.json"))) throw new IllegalStateException("Batch already has a manifest; do not insert twice");
        JSONObject plan = JSONUtil.parseObj(Files.readString(archive.resolve("plan.json")));
        Map<String,List<Map<String,Object>>> records = new LinkedHashMap<>();
        List<Map<String,Object>> summary = new ArrayList<>();
        Map<String,Object> manifest = new LinkedHashMap<>();
        manifest.put("batchId", plan.getStr("batchId"));
        manifest.put("createdAtBeijing", java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
        manifest.put("database", db.getCatalog());
        manifest.put("retention", plan.getStr("retention"));
        manifest.put("records", records);
        manifest.put("summary", summary);
        manifest.put("pending", plan.getStr("pending"));
        db.setAutoCommit(false);
        boolean committed = false;
        try {
            if (((Number)rows(db,"SELECT COUNT(*) AS n FROM merchant_category").get(0).get("n")).longValue() != 0) throw new IllegalStateException("Categories changed since inspection; re-check before seeding");
            for (JSONObject merchant : plan.getJSONArray("merchants").toList(JSONObject.class)) {
                String category = merchant.getStr("category");
                String name = plan.getStr("marker") + merchant.getStr("name");
                int sort = (summary.size()+1)*10;
                long categoryId = insert(db,records,"merchant_category",values("name",category,"sort",sort,"status",1,"show_on_front",1));
                long merchantId = insert(db,records,"merchant_info",values("name",name,"category_id",categoryId,"contact_name","联调测试联系人","contact_phone","000-00000000","address","联调专用虚拟地址","introduction","联调测试商家，仅用于前后端数据互通测试。","status",1,"remark",plan.getStr("batchId") + "；保留至用户要求清理"));
                long storeCategoryId = insert(db,records,"store_category",values("merchant_id",merchantId,"name","联调门店","sort",0,"status",1,"show_on_front",1));
                long storeId = insert(db,records,"store",values("merchant_id",merchantId,"category_id",storeCategoryId,"name",name+"·测试门店","address","联调专用虚拟地址（不对应真实经营场所）","phone","000-00000000","business_hours","09:00-22:00（测试）","status",1));
                List<Long> couponIds = new ArrayList<>();
                for (JSONObject coupon : plan.getJSONArray("couponsPerMerchant").toList(JSONObject.class)) {
                    var data = values("merchant_id",merchantId,"name",plan.getStr("marker")+category+coupon.getStr("nameSuffix"),"type",coupon.getStr("type"),"description","联调测试券；批次 "+plan.getStr("batchId")+"。仅供功能测试，不对应真实交易；下单和核销以后端真实响应为准。","sale_price",new BigDecimal(coupon.getStr("salePriceYuan")),"min_spend",new BigDecimal(coupon.getStr("minSpendYuan")),"scope_type",0,"issue_limit",1000,"issued_count",0,"per_customer_limit",20,"validity_type","AFTER_ISSUE","valid_days",30,"allow_combine",0,"rule_version",1,"status",1);
                    if (coupon.containsKey("discountAmountYuan")) data.put("discount_amount",new BigDecimal(coupon.getStr("discountAmountYuan")));
                    if (coupon.containsKey("deductAmountYuan")) data.put("deduct_amount",new BigDecimal(coupon.getStr("deductAmountYuan")));
                    couponIds.add(insert(db,records,"coupon_template",data));
                }
                summary.add(values("category",category,"categoryId",categoryId,"merchant",name,"merchantId",merchantId,"storeId",storeId,"storeCategoryId",storeCategoryId,"couponTemplateIds",couponIds,"plannedProduct",merchant.getStr("plannedProduct"),"productState","NOT_INSERTED_MISSING_TABLE","publicationState","NOT_INSERTED_MISSING_TABLE"));
            }
            if (summary.size() != plan.getJSONArray("merchants").size() || records.get("coupon_template").size() != summary.size()*2) throw new IllegalStateException("Unexpected fixture counts");
            manifest.put("state","PREPARED_NOT_COMMITTED");
            Files.writeString(archive.resolve("manifest.json"),JSONUtil.toJsonPrettyStr(manifest),StandardOpenOption.CREATE_NEW);
            StringBuilder sql = new StringBuilder("-- Archive of this batch only. Not a schema migration. Do not replay while IDs exist.\nSET NAMES utf8mb4;\nSTART TRANSACTION;\n");
            for (var table : records.entrySet()) for (var row : table.getValue()) {
                sql.append("INSERT INTO `").append(table.getKey()).append("` (`").append(String.join("`,`",row.keySet())).append("`) VALUES (").append(String.join(",",row.values().stream().map(LocalSeedData::literal).toList())).append(");\n");
            }
            sql.append("COMMIT;\n");
            Files.writeString(archive.resolve("inserted-records.sql"),sql.toString(),StandardOpenOption.CREATE_NEW);
            db.commit();
            committed = true;
            manifest.put("state","COMMITTED_RETAIN_UNTIL_USER_REQUESTS_DELETE");
            Files.writeString(archive.resolve("manifest.json"),JSONUtil.toJsonPrettyStr(manifest),StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println(JSONUtil.toJsonStr(Map.of("state",manifest.get("state"),"summary",summary,"counts",records.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().size())))));
        } catch (Exception failure) {
            if (!committed) db.rollback();
            if (Files.exists(archive.resolve("manifest.json"))) {
                manifest.put("state",committed ? "COMMITTED_CHECK_ARCHIVE" : "ROLLED_BACK");
                Files.writeString(archive.resolve("manifest.json"),JSONUtil.toJsonPrettyStr(manifest),StandardOpenOption.TRUNCATE_EXISTING);
            }
            throw failure;
        }
    }
    static void verify(Connection db, Path archive) throws Exception {
        db.setReadOnly(true);
        JSONObject manifest = JSONUtil.parseObj(Files.readString(archive.resolve("manifest.json")));
        if (!manifest.getStr("state").startsWith("COMMITTED")) throw new IllegalStateException("No committed batch");
        var records = manifest.getJSONObject("records");
        Map<String,Integer> verified = new LinkedHashMap<>();
        for (String table : records.keySet()) {
            if (!Set.of("merchant_category","merchant_info","store_category","store","coupon_template").contains(table)) throw new IllegalStateException("Unexpected archive table");
            int count = 0;
            for (JSONObject expected : records.getJSONArray(table).toList(JSONObject.class)) {
                var found = rows(db,"SELECT * FROM `"+table+"` WHERE id=?",expected.getLong("id"));
                if (found.size()!=1 || !Objects.equals(expected.getStr("name"),found.get(0).get("name"))) throw new IllegalStateException("Missing or changed batch record in "+table);
                count++;
            }
            verified.put(table,count);
        }
        for (JSONObject merchant : manifest.getJSONArray("summary").toList(JSONObject.class)) {
            var coupons = rows(db,"SELECT id,type,sale_price FROM coupon_template WHERE merchant_id=? ORDER BY id",merchant.getLong("merchantId"));
            if (coupons.size()!=2 || !"FULL_REDUCTION".equals(coupons.get(0).get("type")) || !"CASH".equals(coupons.get(1).get("type"))
                || new BigDecimal(coupons.get(0).get("sale_price").toString()).signum()!=0
                || new BigDecimal(coupons.get(1).get("sale_price").toString()).compareTo(new BigDecimal("9.90"))!=0) throw new IllegalStateException("Unexpected coupon pair");
        }
        Map<String,Object> result = values("batchId",manifest.getStr("batchId"),"verifiedAtBeijing",java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString(),"persistedCounts",verified,"twoCouponsPerMerchant",true,"dataRetained",true);
        Files.writeString(archive.resolve("database-verification.json"),JSONUtil.toJsonPrettyStr(result));
        System.out.println(JSONUtil.toJsonStr(result));
    }
    public static void main(String[] args) throws Exception {
        Path backend = Path.of(args[0]);
        Path archive = Path.of(args[1]);
        Files.createDirectories(archive);
        try (var db = connect(backend)) {
            if (args.length == 3 && args[2].equals("seed")) { seed(db,archive); return; }
            if (args.length == 3 && args[2].equals("verify")) { verify(db,archive); return; }
            db.setReadOnly(true);
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("database", db.getCatalog());
            report.put("categories", rows(db, "SELECT id,parent_id,name,sort,status,show_on_front FROM merchant_category ORDER BY sort,id"));
            report.put("tables", rows(db, "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name"));
            report.put("columns", rows(db, "SELECT table_name,column_name,column_type,is_nullable,column_default,extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('merchant_category','merchant_info','store_category','store','coupon_template','coupon_publication','coupon_store','coupon_product','product','product_store') ORDER BY table_name,ordinal_position"));
            report.put("counts", rows(db, "SELECT 'merchant_category' AS table_name,COUNT(*) AS row_count FROM merchant_category UNION ALL SELECT 'merchant_info',COUNT(*) FROM merchant_info UNION ALL SELECT 'store',COUNT(*) FROM store UNION ALL SELECT 'coupon_template',COUNT(*) FROM coupon_template"));
            Files.writeString(archive.resolve("environment-before.json"), JSONUtil.toJsonPrettyStr(report), StandardOpenOption.CREATE_NEW);
            System.out.println(JSONUtil.toJsonStr(Map.of("categories", report.get("categories"), "tables", report.get("tables"), "counts", report.get("counts"))));
        }
    }
}
