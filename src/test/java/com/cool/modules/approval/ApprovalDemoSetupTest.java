package com.cool.modules.approval;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONUtil;
import com.cool.modules.approval.entity.AuditRecordEntity;
import com.cool.modules.approval.entity.MerchantApplicationEntity;
import com.cool.modules.approval.mapper.ApprovalMapper;
import com.cool.modules.approval.mapper.MerchantApplicationMapper;
import com.cool.modules.approval.service.ApprovalCenterService;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.coupon.entity.CouponPublicationEntity;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponPublicationMapper;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantCategoryMapper;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 显式为现有商家与优惠券补录最近十天的演示审批并验证状态关联
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 13:29:12
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=approval-demo-unused", "sms-auth.access-key-secret=approval-demo-unused",
        "logging.level.com.cool=WARN"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "approval.demo.setup", matches = "true")
@org.springframework.context.annotation.Import(ApprovalDemoSetupTest.SeedConfiguration.class)
@Transactional
@Commit
class ApprovalDemoSetupTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class SeedConfiguration {
        // 补录不使用 HTTP 登录注册入口；独立测试容器不加载并行开发中的商家鉴权控制器。
        // 不改变应用本身的路由、安全配置或正常启动行为。
        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor seedOnlyControllers() {
            return new org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry registry) {
                    if (registry.containsBeanDefinition("merchantAuthController")) registry.removeBeanDefinition("merchantAuthController");
                }
                @Override
                public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {}
            };
        }
    }
    private static final String SEED = "approval-demo-20261021";
    private static final String MARKER = "【演示补录】";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Autowired MerchantInfoMapper merchants;
    @Autowired MerchantCategoryMapper categories;
    @Autowired BaseSysUserMapper users;
    @Autowired MerchantApplicationMapper applications;
    @Autowired ApprovalMapper records;
    @Autowired CouponPublicationMapper publications;
    @Autowired CouponTemplateMapper coupons;
    @Autowired ApprovalCenterService center;
    private final List<Long> insertedApplications = new ArrayList<>();
    private final List<Long> insertedEvents = new ArrayList<>();
    private final List<CouponPublicationEntity> changedPublications = new ArrayList<>();
    private LocalDateTime windowStart;
    private LocalDateTime safeEnd;

    @Test
    void seedExistingMerchantsAndSubmittedCoupons() throws Exception {
        // 即使本用例显式允许提交，也必须在全部一致性检查完成后才真正提交。
        TestTransaction.flagForRollback();
        if (records.selectCountByQuery(QueryWrapper.create().like(AuditRecordEntity::getReason, MARKER)) > 0) {
            System.out.println("APPROVAL_DEMO_ALREADY_PRESENT: skip; existing demonstrations are never overwritten.");
            return;
        }
        assertEquals(0, records.selectCountByQuery(QueryWrapper.create()), "已有真实审批日志，停止整批历史补录");
        assertEquals(0, applications.selectCountByQuery(QueryWrapper.create()), "已有入驻申请，停止整批历史补录");
        LocalDateTime now = LocalDateTime.now(ZONE).withNano(0);
        windowStart = now.toLocalDate().minusDays(9).atStartOfDay();
        safeEnd = now.minusMinutes(20);
        List<MerchantInfoEntity> merchantList = merchants.selectListByQuery(QueryWrapper.create()
                .orderBy(MerchantInfoEntity::getId, true));
        assertEquals(32, merchantList.size(), "现有商家范围变化，请重新核对后补录");
        Map<Long, BaseSysUserEntity> ownerMap = users.selectListByQuery(QueryWrapper.create()
                .eq(BaseSysUserEntity::getMerchantRole, "OWNER").orderBy(BaseSysUserEntity::getId, true))
                .stream().collect(Collectors.toMap(BaseSysUserEntity::getMerchantId, u -> u, (first, ignored) -> first));
        assertTrue(merchantList.stream().allMatch(m -> ownerMap.containsKey(m.getId())), "商家必须已有关联经营账号");
        Map<Long, MerchantCategoryEntity> categoryMap = categories.selectListByQuery(QueryWrapper.create())
                .stream().collect(Collectors.toMap(MerchantCategoryEntity::getId, c -> c));
        Map<Long, MerchantInfoEntity> merchantMap = merchantList.stream()
                .collect(Collectors.toMap(MerchantInfoEntity::getId, m -> m));
        Map<Long, CouponTemplateEntity> couponMap = coupons.selectListByQuery(QueryWrapper.create())
                .stream().collect(Collectors.toMap(CouponTemplateEntity::getId, c -> c));
        List<CouponPublicationEntity> publicationList = publications.selectListByQuery(QueryWrapper.create()
                .eq(CouponPublicationEntity::getChannel, "PLATFORM")
                .isNotNull(CouponPublicationEntity::getSubmittedAt)
                .orderBy(CouponPublicationEntity::getId, true).forUpdate());
        assertEquals(24, publicationList.size(), "已提交优惠券范围变化，请重新核对");
        // 备份仅含发布状态与资料快照，不导出任何经营账号密码或申请口令。
        Path directory = Path.of("..", "testing", "data").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = directory.resolve("approval-before-" + stamp + ".json");
        Files.writeString(backup, JSONUtil.toJsonPrettyStr(Map.of(
                "seed", SEED, "backedUpAt", now.toString(), "publications", publicationList,
                "auditRecordCount", 0, "merchantApplicationCount", 0)), StandardCharsets.UTF_8);
        StringBuilder rollback = new StringBuilder("-- 仅在本次补录后尚无后续业务操作时使用；不会自动执行。\nSTART TRANSACTION;\n");
        for (CouponPublicationEntity p : publicationList) {
            rollback.append("UPDATE coupon_publication SET submitted_at=").append(sqlDate(p.getSubmittedAt()))
                    .append(", published_at=").append(sqlDate(p.getPublishedAt())).append(" WHERE id=")
                    .append(p.getId()).append(";\n");
        }
        Map<Long, LocalDateTime> admittedAt = new HashMap<>();
        BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
        int mIndex = 0;
        for (MerchantInfoEntity merchant : merchantList) {
            BaseSysUserEntity owner = ownerMap.get(merchant.getId());
            MerchantCategoryEntity category = categoryMap.get(merchant.getCategoryId());
            assertNotNull(category);
            String applicant = "演示·" + merchant.getContactName();
            String reviewer = mIndex % 2 == 0 ? "演示·运营林悦" : "演示·运营陈宁";
            LocalDateTime submitted = interpolate(windowStart.plusHours(2), windowStart.plusDays(7),
                    (mIndex * 13) % merchantList.size(), merchantList.size());
            boolean retry = mIndex % 4 == 0;
            String applicationNo = "DEMO-APPR-20261021-" + merchant.getId();
            // 已通过的补录申请不会参与登录；随机口令散列不会改变已有账号的密码。
            String hash = passwords.encode(UUID.randomUUID().toString());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", merchant.getName()); snapshot.put("categoryId", merchant.getCategoryId());
            snapshot.put("categoryName", category.getName()); snapshot.put("contactName", merchant.getContactName());
            snapshot.put("contactPhone", merchant.getContactPhone()); snapshot.put("address", merchant.getAddress());
            snapshot.put("introduction", merchant.getIntroduction()); snapshot.put("demoSeed", SEED);
            String json = JSONUtil.toJsonStr(snapshot);
            if (retry) {
                MerchantApplicationEntity old = application(merchant, owner, applicationNo, hash, applicant, json,
                        1, 3, 0, submitted.minusMinutes(70));
                append("MERCHANT_APPLICATION", old.getId(), merchant.getId(), 1, 1, "SUBMIT", applicant,
                        "APPLICANT", "首次提交入驻申请，演示资料补全流程。", json, submitted.minusMinutes(70));
                append("MERCHANT_APPLICATION", old.getId(), merchant.getId(), 1, 1, "REJECT", reviewer,
                        "ADMIN", "请补齐经营地址与联系人说明后重新提交。", null, submitted.minusMinutes(30));
            }
            int round = retry ? 2 : 1;
            MerchantApplicationEntity current = application(merchant, owner, applicationNo, hash, applicant, json,
                    round, 2, 1, submitted);
            append("MERCHANT_APPLICATION", current.getId(), merchant.getId(), round, round, "SUBMIT", applicant,
                    "APPLICANT", retry ? "资料已补全，重新提交给平台运营审核。" : "向平台运营提交入驻申请。", json, submitted);
            LocalDateTime approved = submitted.plusMinutes(90 + (mIndex % 4) * 15L);
            append("MERCHANT_APPLICATION", current.getId(), merchant.getId(), round, round, "APPROVE", reviewer,
                    "ADMIN", "入驻资料审核通过，关联已有经营账号；现有账号与平台启停状态保持不变。", null, approved);
            admittedAt.put(merchant.getId(), approved);
            mIndex++;
        }
        int cIndex = 0;
        for (CouponPublicationEntity p : publicationList) {
            MerchantInfoEntity merchant = merchantMap.get(p.getMerchantId());
            CouponTemplateEntity coupon = couponMap.get(p.getCouponTemplateId());
            assertNotNull(merchant); assertNotNull(coupon); assertNotNull(p.getSubmittedVersion());
            assertNotNull(p.getSubmittedSnapshot(), "已提交的券必须有资料快照");
            LocalDateTime begin = later(windowStart, admittedAt.get(merchant.getId()).plusMinutes(30));
            // 优惠券已有创建时间，补录的提交不能早于模板本身的创建。
            if (coupon.getCreateTime() != null) begin = later(begin, local(coupon.getCreateTime()));
            LocalDateTime lastSubmit = safeEnd.minusHours(2);
            assertTrue(begin.plusHours(2).isBefore(lastSubmit), "当前券太新，不适合补录十天内历史流程");
            LocalDateTime submitted = interpolate(begin.plusHours(2), lastSubmit,
                    (cIndex * 7) % publicationList.size(), publicationList.size());
            String applicant = "演示·" + merchant.getContactName();
            String reviewer = cIndex % 2 == 0 ? "演示·运营陈宁" : "演示·运营林悦";
            boolean retry = cIndex % 4 == 0;
            int round = retry ? 2 : 1;
            int version = p.getSubmittedVersion();
            if (retry) {
                int oldVersion = version > 1 ? version - 1 : version;
                append("COUPON_PLATFORM", p.getId(), merchant.getId(), 1, oldVersion, "SUBMIT", applicant,
                        "MERCHANT", "首次提交优惠券发布申请。", p.getSubmittedSnapshot(), submitted.minusHours(2));
                append("COUPON_PLATFORM", p.getId(), merchant.getId(), 1, oldVersion,
                        version > 1 ? "REJECT" : "WITHDRAW", version > 1 ? reviewer : applicant,
                        version > 1 ? "ADMIN" : "MERCHANT",
                        version > 1 ? "请明确套餐内容、适用门店与使用限制。" : "商家主动撤回，补充使用说明后重新提交。",
                        null, submitted.minusMinutes(80));
            }
            append("COUPON_PLATFORM", p.getId(), merchant.getId(), round, version, "SUBMIT", applicant,
                    "MERCHANT", retry ? "资料已调整，重新提交运营审核。" : "提交给平台运营审核。",
                    p.getSubmittedSnapshot(), submitted);
            p.setSubmittedAt(date(submitted));
            if (p.getAuditStatus() == 2 || p.getAuditStatus() == 3) {
                boolean approved = p.getAuditStatus() == 2;
                append("COUPON_PLATFORM", p.getId(), merchant.getId(), round, version, approved ? "APPROVE" : "REJECT",
                        reviewer, "ADMIN", approved ? "优惠内容及使用规则明确，审核通过，等待商家上架。"
                        : cIndex % 2 == 0 ? "适用门店说明不完整，请补充后重提。" : "优惠条件与使用限制描述不清，请完善。",
                        null, submitted.plusMinutes(35));
            }
            if (p.getPublishStatus() == 1 || p.getPublishStatus() == 2) {
                assertEquals(2, p.getAuditStatus(), "上架券必须已通过审核");
                append("COUPON_PLATFORM", p.getId(), merchant.getId(), round, version, "PUBLISH", applicant,
                        "MERCHANT", "已通过运营审核，由商家操作上架。", null, submitted.plusMinutes(60));
                p.setPublishedAt(date(submitted.plusMinutes(60)));
                if (p.getPublishStatus() == 2) append("COUPON_PLATFORM", p.getId(), merchant.getId(), round, version,
                        "UNPUBLISH", applicant, "MERCHANT", "商家暂停本券活动，主动下架。", null, submitted.plusMinutes(100));
            }
            publications.update(p); changedPublications.add(p); cIndex++;
        }
        validateResults(merchantList, publicationList);
        rollback.insert(rollback.indexOf("UPDATE"),
                "DELETE FROM audit_record WHERE id IN (" + ids(insertedEvents) + ") AND reason LIKE '" + MARKER + "%';\n"
                + "DELETE FROM merchant_application WHERE id IN (" + ids(insertedApplications)
                + ") AND application_no LIKE 'DEMO-APPR-20261021-%';\n");
        rollback.append("COMMIT;\n");
        Files.writeString(directory.resolve("approval-demo-rollback-" + stamp + ".sql"), rollback, StandardCharsets.UTF_8);
        String report = "# 审批演示补录\n\n标记：" + SEED + "；所有操作意见含“" + MARKER + "”。\n\n"
                + "写入范围：现有 " + merchantList.size() + " 家商家；" + insertedApplications.size() + " 条入驻轮次；"
                + publicationList.size() + " 张已提交优惠券；" + insertedEvents.size() + " 条流程日志。\n\n"
                + "时间窗口：" + windowStart + " 至 " + safeEnd + "（北京时间）。商家入驻分散至多个日期；优惠券提交不早于其原始创建时间。\n\n"
                + "已有商家均补为入驻已通过，部分带驳回重提历史；没有新增经营账号，没有修改密码、商家启停、券审核与上下架状态、券规则和库存。\n\n"
                + "优惠券提交和上架时间已与新演示日志对齐。查看入驻记录时选择“已通过”或“全部状态”。\n\n"
                + "补录前备份：" + backup.getFileName() + "。回退脚本只可在尚无后续业务操作时人工执行。\n";
        Files.writeString(directory.resolve("approval-demo-20261021.md"), report, StandardCharsets.UTF_8);
        System.out.printf("APPROVAL_DEMO_RESULT merchants=%d applications=%d publications=%d events=%d%n",
                merchantList.size(), insertedApplications.size(), publicationList.size(), insertedEvents.size());
        TestTransaction.flagForCommit();
    }

    private MerchantApplicationEntity application(MerchantInfoEntity m, BaseSysUserEntity owner, String number,
            String hash, String applicant, String snapshot, int round, int status, int current, LocalDateTime time) {
        MerchantApplicationEntity a = new MerchantApplicationEntity();
        a.setApplicationNo(number); a.setSubmissionNo(round); a.setMerchantId(m.getId());
        a.setUsername(owner.getUsername()); a.setPasswordHash(hash); a.setMerchantName(m.getName());
        a.setSnapshot(snapshot); a.setStatus(status); a.setCurrentFlag(current); a.setSubmittedBy(applicant);
        a.setSubmittedAt(date(time)); a.setCreateTime(date(time));
        applications.insertSelective(a); insertedApplications.add(a.getId()); return a;
    }

    private void append(String type, Long businessId, Long merchantId, int round, int version, String action,
            String actor, String actorType, String reason, String snapshot, LocalDateTime time) {
        assertFalse(time.isBefore(windowStart)); assertFalse(time.isAfter(safeEnd));
        AuditRecordEntity a = new AuditRecordEntity();
        a.setBusinessType(type); a.setBusinessId(businessId); a.setMerchantId(merchantId);
        a.setSubmissionNo(round); a.setVersion(version); a.setAction(action); a.setActorName(actor);
        a.setActorType(actorType); a.setReason(MARKER + reason); a.setSnapshot(snapshot); a.setReviewedAt(date(time));
        records.insertSelective(a); insertedEvents.add(a.getId());
    }

    private void validateResults(List<MerchantInfoEntity> merchantList, List<CouponPublicationEntity> pubs) {
        assertEquals(32, center.page("MERCHANT", 2, "", 1, 100).getPagination().getTotal());
        Set<Long> linked = applications.selectListByQuery(QueryWrapper.create()
                .eq(MerchantApplicationEntity::getCurrentFlag, 1)).stream()
                .map(MerchantApplicationEntity::getMerchantId).collect(Collectors.toSet());
        assertEquals(merchantList.stream().map(MerchantInfoEntity::getId).collect(Collectors.toSet()), linked);
        for (MerchantApplicationEntity a : applications.selectListByQuery(QueryWrapper.create()
                .eq(MerchantApplicationEntity::getCurrentFlag, 1))) {
            var info = center.info("MERCHANT", a.getId());
            assertFalse(info.isHistorical());
            assertEquals(a.getSubmissionNo() * 2, info.getHistory().size());
            assertEquals("APPROVE", info.getHistory().get(info.getHistory().size() - 1).getAction());
        }
        for (CouponPublicationEntity p : pubs) {
            var info = center.info("COUPON", p.getId());
            assertFalse(info.isHistorical()); assertTrue(info.getItem().getSubmittedBy().startsWith("演示·"));
            var timeline = info.getHistory();
            Date previous = null;
            for (AuditRecordEntity event : timeline) {
                if (previous != null) assertFalse(event.getReviewedAt().before(previous));
                previous = event.getReviewedAt();
            }
            String last = timeline.get(timeline.size() - 1).getAction();
            String expected = p.getPublishStatus() == 1 ? "PUBLISH" : p.getPublishStatus() == 2 ? "UNPUBLISH"
                    : p.getAuditStatus() == 1 ? "SUBMIT" : p.getAuditStatus() == 3 ? "REJECT" : "APPROVE";
            assertEquals(expected, last);
        }
    }

    private static LocalDateTime interpolate(LocalDateTime start, LocalDateTime end, int index, int count) {
        return start.plusSeconds(Duration.between(start, end).getSeconds() * index / Math.max(1, count - 1));
    }
    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
    private static Date date(LocalDateTime value) { return Date.from(value.atZone(ZONE).toInstant()); }
    private static LocalDateTime local(Date value) { return value.toInstant().atZone(ZONE).toLocalDateTime(); }
    private static String ids(List<Long> values) { return values.stream().map(String::valueOf).collect(Collectors.joining(",")); }
    private static String sqlDate(Date value) { return value == null ? "NULL" : "'" + local(value).format(SQL_TIME) + "'"; }
}
