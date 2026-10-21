package com.cool.modules.merchant;

import cn.hutool.json.JSONUtil;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.service.MerchantStoreService;
import com.cool.modules.base.entity.sys.BaseSysConfEntity;
import com.cool.modules.base.mapper.sys.BaseSysConfMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 显式检查和调整演示商家的门店数量分布并导出修改前备份
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 21:29:30
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=store-distribution-unused", "sms-auth.access-key-secret=store-distribution-unused"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "merchant.store.distribution", matches = "inspect|apply")
@Transactional
@Commit
class MerchantStoreDistributionSetupTest {
    @Autowired MerchantInfoMapper merchants;
    @Autowired MerchantStoreMapper stores;
    @Autowired MerchantStoreService storeService;
    @Autowired BaseSysConfMapper configurations;
    @Autowired SqlSessionTemplate session;
    private static final String KEY = "merchant.store-distribution.v1";
    private static final Map<String, Integer> TARGETS = Map.ofEntries(
        Map.entry("巷口烧烤小馆",2), Map.entry("甜岛糖水铺",2), Map.entry("青禾咖啡",8), Map.entry("热沸火锅",6),
        Map.entry("邻里便当",7), Map.entry("麦香烘焙屋",5), Map.entry("星幕影城",3), Map.entry("悦声音乐空间",1),
        Map.entry("净家保洁",2), Map.entry("洁步洗鞋坊",6), Map.entry("花间美甲",2), Map.entry("跃动健身",5),
        Map.entry("童趣亲子乐园",3), Map.entry("拾光商务酒店",4), Map.entry("云栖民宿",1), Map.entry("毛球宠物洗护",3),
        Map.entry("炭里烧烤坊",5), Map.entry("椰屿甜品",1), Map.entry("拾间咖啡",3), Map.entry("沸香火锅",2),
        Map.entry("禾味便当",3), Map.entry("晨麦烘焙",9), Map.entry("光映影城",2), Map.entry("星声量贩KTV",4),
        Map.entry("暖家家政",1), Map.entry("衣净洗护",3), Map.entry("指间美甲",1), Map.entry("燃力健身",2),
        Map.entry("乐芽亲子乐园",2), Map.entry("泊庭商务酒店",2), Map.entry("山屿民宿",1), Map.entry("爪印宠物生活馆",1));
    private static final Map<String, List<String>> BRANCHES = Map.of(
        "福州市", List.of("五四北店", "三坊七巷店", "大学城店", "东二环店", "闽江世纪城店"),
        "泉州市", List.of("泉秀店", "城东店", "清源店", "江滨店", "晋江店"),
        "厦门市", List.of("观音山店", "软件园店", "杏林店", "同安店", "环岛路店"));

    @Test void inspectOrApply() throws Exception {
        var list = merchants.selectListByQuery(QueryWrapper.create().orderBy(MerchantInfoEntity::getId, true));
        var snapshot = new ArrayList<Object>();
        var jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(session.getConnection(), true));
        Set<Long> assigned = new HashSet<>();
        boolean hasAssignments;
        try (var tables = session.getConnection().getMetaData().getTables(null, null, "base_sys_user_store", new String[]{"TABLE"})) {
            hasAssignments = tables.next();
        }
        if (hasAssignments) assigned.addAll(jdbc.queryForList("SELECT store_id FROM base_sys_user_store", Long.class));
        for (var merchant : list) {
            var rows = stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId, merchant.getId())
                    .orderBy(MerchantStoreEntity::getId, true));
            var protectedIds = rows.stream().filter(row -> stores.couponReferenceCount(row.getId()) > 0 || assigned.contains(row.getId())).map(MerchantStoreEntity::getId).toList();
            snapshot.add(Map.of("merchant", merchant, "stores", rows, "protectedIds", protectedIds));
            System.out.printf("STORE_DISTRIBUTION %s status=%s count=%d protected=%s%n", merchant.getName(), merchant.getStatus(), rows.size(), protectedIds);
        }
        Files.writeString(Path.of("../testing/data/merchant-store-distribution-inspect.json"), JSONUtil.toJsonPrettyStr(snapshot), StandardCharsets.UTF_8);
        if (!"apply".equals(System.getProperty("merchant.store.distribution"))) return;
        if (configurations.selectOneByQuery(QueryWrapper.create().eq(BaseSysConfEntity::getCKey, KEY).forUpdate()) != null) {
            System.out.println("STORE_DISTRIBUTION_ALREADY_APPLIED");
            return;
        }
        var demo = list.stream().filter(m -> TARGETS.containsKey(m.getName())).toList();
        assertEquals(32, demo.size(), "指定演示商家范围变化，停止调整");
        for (var m : demo) assertTrue(m.getRemark() != null && m.getRemark().startsWith("演示数据："));
        Path backup = Path.of("../testing/data/backups/merchant-store-distribution-before-20261021.json");
        Files.createDirectories(backup.getParent());
        Files.writeString(backup, JSONUtil.toJsonPrettyStr(snapshot), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        var marker = new BaseSysConfEntity(); marker.setCKey(KEY); marker.setCValue(JSONUtil.toJsonStr(Map.of("backup", backup.toString(), "targets", TARGETS)));
        configurations.insert(marker);
        int removed = 0, added = 0;
        var report = new StringBuilder("# 演示商家门店数量调整\n\n现有业务关联门店保留，新门店地址为模拟数据。\n\n| 商家 | 调整前 | 调整后 |\n|---|---:|---:|\n");
        for (var merchant : demo) {
            var rows = new ArrayList<>(stores.selectListByQuery(QueryWrapper.create()
                    .eq(MerchantStoreEntity::getMerchantId, merchant.getId()).orderBy(MerchantStoreEntity::getId, true).forUpdate()));
            int before = rows.size(), target = TARGETS.get(merchant.getName());
            var protectedRows = rows.stream().filter(row -> stores.couponReferenceCount(row.getId()) > 0 || assigned.contains(row.getId())).toList();
            assertTrue(target >= protectedRows.size(), "目标数量不能少于业务关联门店数");
            var candidates = new ArrayList<>(rows.stream().filter(row -> !protectedRows.contains(row)).toList());
            Collections.shuffle(candidates, new Random(merchant.getId() * 20261021L));
            while (rows.size() > target) {
                var remove = candidates.remove(0);
                assertTrue(storeService.delete(remove.getId()));
                rows.remove(remove); removed++;
            }
            int branch = 0;
            while (rows.size() < target) {
                var source = rows.get(0);
                var created = new MerchantStoreEntity();
                created.setMerchantId(merchant.getId());
                created.setCategoryId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1,7));
                String name = "演示门店-" + merchant.getName() + "·" + BRANCHES.get(merchant.getCity()).get(branch);
                assertTrue(rows.stream().noneMatch(r -> name.equals(r.getName())), "门店名称不能重复");
                created.setName(name); created.setCover(source.getCover());
                created.setAddress(merchant.getCity() + merchant.getDistrict() + "演示支路" + (100 + branch * 23) + "号（模拟地址）");
                created.setLongitude(source.getLongitude() == null ? null : source.getLongitude().add(BigDecimal.valueOf((branch+1)*0.002)));
                created.setLatitude(source.getLatitude() == null ? null : source.getLatitude().add(BigDecimal.valueOf((branch+1)*0.001)));
                created.setPhone(source.getPhone()); created.setBusinessHours(source.getBusinessHours()); created.setStatus(1);
                storeService.add(created); rows.add(created); branch++; added++;
            }
            assertEquals(target, stores.selectCountByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant.getId())));
            for (var protectedRow : protectedRows) assertEquals(JSONUtil.toJsonStr(protectedRow), JSONUtil.toJsonStr(stores.selectOneById(protectedRow.getId())));
            report.append("| ").append(merchant.getName()).append(" | ").append(before).append(" | ").append(target).append(" |\n");
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_store r LEFT JOIN store s ON s.id=r.store_id WHERE s.id IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemption r LEFT JOIN store s ON s.id=r.store_id WHERE s.id IS NULL", Integer.class));
        if (hasAssignments) assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM base_sys_user_store r LEFT JOIN store s ON s.id=r.store_id WHERE s.id IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM store s LEFT JOIN store_type t ON s.category_id=t.id WHERE t.id IS NULL", Integer.class));
        for (var merchant : list) if (!TARGETS.containsKey(merchant.getName())) {
            assertTrue(stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant.getId())).isEmpty(), "非演示商家不受影响");
        }
        Files.writeString(Path.of("../testing/data/merchant-store-distribution-20261021.md"), report.append("\n新增 ").append(added).append(" 家，移除未关联业务的演示门店 ").append(removed).append(" 家。\n").toString(), StandardCharsets.UTF_8);
        System.out.printf("STORE_DISTRIBUTION_APPLIED added=%d removed=%d total=%d%n", added, removed, stores.selectCountByQuery(QueryWrapper.create()));
    }
}
