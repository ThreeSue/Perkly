package com.cool.modules.merchant;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.service.MerchantAccountService;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 显式批量补齐既有演示商家账号并验证登录和导出凭据表
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 10:36:48
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=merchant-setup-unused", "sms-auth.access-key-secret=merchant-setup-unused"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "merchant.demo.setup", matches = "true")
@Transactional
@Commit
class MerchantDemoAccountsSetupTest {
    @Autowired MerchantInfoMapper merchants;
    @Autowired BaseSysUserMapper users;
    @Autowired MerchantAccountService accounts;
    @Autowired CoolCache cache;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    private static final Map<String, String> PINYIN = Map.ofEntries(
            Map.entry("巷口烧烤小馆", "xiangkoushaokaoxiaoguan"),
            Map.entry("甜岛糖水铺", "tiandaotangshuipu"),
            Map.entry("青禾咖啡", "qinghekafei"),
            Map.entry("热沸火锅", "refeihuoguo"),
            Map.entry("邻里便当", "linlibiandang"),
            Map.entry("麦香烘焙屋", "maixianghongbeiwu"),
            Map.entry("星幕影城", "xingmuyingcheng"),
            Map.entry("悦声音乐空间", "yueshengyinyuekongjian"),
            Map.entry("净家保洁", "jingjiabaojie"),
            Map.entry("洁步洗鞋坊", "jiebuxixiefang"),
            Map.entry("花间美甲", "huajianmeijia"),
            Map.entry("跃动健身", "yuedongjianshen"),
            Map.entry("童趣亲子乐园", "tongquqinzileyuan"),
            Map.entry("拾光商务酒店", "shiguangshangwujiudian"),
            Map.entry("云栖民宿", "yunqiminsu"),
            Map.entry("毛球宠物洗护", "maoqiuchongwuxihu"),
            Map.entry("炭里烧烤坊", "tanlishaokaofang"),
            Map.entry("椰屿甜品", "yeyutianpin"),
            Map.entry("拾间咖啡", "shijiankafei"),
            Map.entry("沸香火锅", "feixianghuoguo"),
            Map.entry("禾味便当", "heweibiandang"),
            Map.entry("晨麦烘焙", "chenmaihongbei"),
            Map.entry("光映影城", "guangyingyingcheng"),
            Map.entry("星声量贩KTV", "xingshengliangfanktv"),
            Map.entry("暖家家政", "nuanjiajiazheng"),
            Map.entry("衣净洗护", "yijingxihu"),
            Map.entry("指间美甲", "zhijianmeijia"),
            Map.entry("燃力健身", "ranlijianshen"),
            Map.entry("乐芽亲子乐园", "leyaqinzileyuan"),
            Map.entry("泊庭商务酒店", "botingshangwujiudian"),
            Map.entry("山屿民宿", "shanyuminsu"),
            Map.entry("爪印宠物生活馆", "zhuayinchongwushenghuoguan"));

    @Test void provisionMissingDemoAccountsAndExport() throws Exception {
        Path report = Path.of(Objects.requireNonNull(System.getProperty("merchant.demo.report"), "请指定凭据表输出路径")).toAbsolutePath().normalize();
        assertFalse(Files.exists(report.resolve("商家演示账号-20261021.json")), "已有凭据表，请勿重复覆盖初始密码");
        List<MerchantInfoEntity> list = merchants.selectListByQuery(QueryWrapper.create().orderBy(MerchantInfoEntity::getId, true));
        assertEquals(PINYIN.keySet(), new HashSet<>(list.stream().map(MerchantInfoEntity::getName).toList()), "当前商家范围与已核实的32家演示商家不符，停止开通");
        List<Map<String, Object>> rows = new ArrayList<>();
        int created = 0, verified = 0, disabled = 0;
        for (MerchantInfoEntity merchant : list) {
            List<BaseSysUserEntity> owners = users.selectListByQuery(QueryWrapper.create()
                    .eq(BaseSysUserEntity::getMerchantId, merchant.getId())
                    .eq(BaseSysUserEntity::getMerchantRole, "OWNER")
                    .orderBy(BaseSysUserEntity::getId, true));
            boolean active = Integer.valueOf(1).equals(merchant.getStatus());
            BaseSysUserEntity user;
            String password;
            String note;
            if (!owners.isEmpty()) {
                user = owners.get(0);
                String known = System.getenv("MERCHANT_EXISTING_PASSWORD");
                password = "merchant_owner".equals(user.getUsername()) && known != null
                        && passwords.matches(known, user.getPassword()) ? known : "已有密码，未重置";
                note = "保留已有账号与密码";
            } else {
                String username = availableUsername(PINYIN.get(merchant.getName()));
                password = newPassword();
                if (active) {
                    Long id = accounts.provision(username, merchant.getName() + "管理员", password, merchant.getId());
                    user = users.selectOneById(id);
                } else {
                    // 演示补录仅建档停用账号，不修改商家状态，不放宽正式开通服务的启用校验。
                    user = new BaseSysUserEntity();
                    user.setUsername(username); user.setName(merchant.getName() + "管理员"); user.setNickName(user.getName());
                    user.setPassword(passwords.encode(password)); user.setPasswordV(1); user.setStatus(0);
                    user.setMerchantId(merchant.getId()); user.setMerchantRole("OWNER");
                    users.insertSelective(user);
                }
                created++;
                note = active ? "本次补开通" : "已建档；商家与账号保持停用";
            }
            if (!"已有密码，未重置".equals(password)) {
                BaseSysLoginDto input = login(user.getUsername(), password);
                if (active && Integer.valueOf(1).equals(user.getStatus())) {
                    Dict session = (Dict) accounts.login(input);
                    assertEquals(merchant.getId(), accounts.validate(session.getStr("token"), false).getMerchantId());
                    assertEquals(user.getId(), accounts.validate(session.getStr("token"), false).getId());
                    accounts.logout(session.getStr("token"));
                    verified++;
                } else {
                    assertThrows(CoolException.class, () -> accounts.login(input));
                    disabled++;
                }
            }
            assertEquals(merchant.getStatus(), merchants.selectOneById(merchant.getId()).getStatus());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("merchantId", merchant.getId()); row.put("merchantName", merchant.getName());
            row.put("username", user.getUsername()); row.put("initialPassword", password);
            row.put("status", active && Integer.valueOf(1).equals(user.getStatus()) ? "可登录" : "停用");
            row.put("note", note); rows.add(row);
        }
        Files.createDirectories(report);
        Files.writeString(report.resolve("商家演示账号-20261021.json"), JSONUtil.toJsonPrettyStr(rows), StandardCharsets.UTF_8);
        StringBuilder table = new StringBuilder("# 商家演示账号表\n\n生成日期：2026-10-21。用于既有演示商家补录；正式业务按商家注册、审核后开通的流程处理。\n\n登录地址：http://localhost:9001\n\n| 商家 | 账号 | 初始密码 | 状态 | 备注 |\n|---|---|---|---|---|\n");
        rows.forEach(row -> table.append("| ").append(row.get("merchantName")).append(" | ")
                .append(row.get("username")).append(" | ").append(row.get("initialPassword")).append(" | ")
                .append(row.get("status")).append(" | ").append(row.get("note")).append(" |\n"));
        Files.writeString(report.resolve("商家演示账号表-20261021.md"), table, StandardCharsets.UTF_8);
        System.out.printf("DEMO_ACCOUNT_RESULT merchants=%d created=%d loginVerified=%d disabledRejected=%d%n", rows.size(), created, verified, disabled);
    }

    private String availableUsername(String pinyin) {
        String candidate = pinyin; int suffix = 2;
        while (users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, candidate)) != null) {
            candidate = pinyin + suffix++;
        }
        return candidate;
    }

    private String newPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder result = new StringBuilder("Qgj@");
        for (int i = 0; i < 10; i++) result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return result.toString();
    }

    private BaseSysLoginDto login(String username, String password) {
        var input = new BaseSysLoginDto();
        input.setUsername(username); input.setPassword(password);
        input.setCaptchaId(UUID.randomUUID().toString()); input.setVerifyCode("1234");
        cache.set("verify:img:" + input.getCaptchaId(), "1234", 60);
        return input;
    }
    @Test void resetAllMerchantPasswords() throws Exception {
        List<BaseSysUserEntity> list = users.selectListByQuery(QueryWrapper.create()
                .isNotNull(BaseSysUserEntity::getMerchantId));
        assertFalse(list.isEmpty());
        assertTrue(list.stream().allMatch(user -> "OWNER".equals(user.getMerchantRole())), "存在其他商家角色，请先明确重置范围");
        String operatorHash = users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, "admin")).getPassword();
        for (Long merchantId : list.stream().map(BaseSysUserEntity::getMerchantId).distinct().toList())
            accounts.resetMerchantPassword(merchantId);
        int active = 0, disabled = 0;
        for (BaseSysUserEntity old : list) {
            BaseSysUserEntity user = users.selectOneById(old.getId());
            assertTrue(passwords.matches("123456", user.getPassword()));
            assertEquals((old.getPasswordV() == null ? 0 : old.getPasswordV()) + 1, user.getPasswordV());
            assertEquals(old.getStatus(), user.getStatus());
            assertEquals(old.getMerchantId(), user.getMerchantId());
            if (Integer.valueOf(1).equals(user.getStatus()) && Integer.valueOf(1).equals(merchants.selectOneById(user.getMerchantId()).getStatus())) {
                Dict session = (Dict) accounts.login(login(user.getUsername(), "123456"));
                assertEquals(user.getId(), accounts.validate(session.getStr("token"), false).getId());
                accounts.logout(session.getStr("token")); active++;
            } else { assertThrows(CoolException.class, () -> accounts.login(login(user.getUsername(), "123456"))); disabled++; }
        }
        assertEquals(operatorHash, users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, "admin")).getPassword());
        System.out.printf("MERCHANT_RESET_RESULT total=%d loginVerified=%d disabledRejected=%d%n", list.size(), active, disabled);
    }

}
