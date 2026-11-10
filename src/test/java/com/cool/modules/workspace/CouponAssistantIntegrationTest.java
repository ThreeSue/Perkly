package com.cool.modules.workspace;

import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.service.CouponAssistantService;
import com.cool.modules.ai.service.CouponAssistantService.*;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证优惠券助手的登录入口、授权统计及确认执行
 * @Version: v1.1
 * @Date: 2026-11-08
 * @Time: 23:26:11
 */
class CouponAssistantIntegrationTest extends FeatureModulesIntegrationTest {
    @SpyBean CouponAssistantService assistant;
    @SpyBean com.cool.modules.coupon.service.CouponTemplateService templateService;
    @SpyBean com.cool.modules.ai.service.CouponAssistantNarrator testedNarrator;
    @org.springframework.beans.factory.annotation.Autowired com.cool.modules.workspace.mapper.WorkspaceCouponStoreMapper assistantStores;
    @org.junit.jupiter.api.BeforeEach void deterministicNarration() {
        doReturn(null).when(assistant).proposeRepair(any(), any(), any(), any(), any());
        doReturn(null).when(assistant).proposeDraft(any(), any());
        org.mockito.Mockito.doAnswer(call -> ((Reply) call.getArgument(1)).text()).when(assistant).narrate(any(), any());
        if ("true".equals(System.getenv("PERKLY_ASSISTANT_LIVE_TEST")))
            org.mockito.Mockito.doAnswer(call -> {
                var extracted = (Intent) call.callRealMethod();
                System.out.println("LIVE_INTENT " + extracted);
                return extracted;
            }).when(assistant).interpret(any());
    }
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test void assistantHttpEntriesAndScope() throws Exception {
        activityPermission(owner, "coupon:view");
        fixture("助手测试代金券");
        intent("COUNT", null);
        String body = "{\"text\":\"统计优惠券\",\"merchantId\":" + merchant + "}";
        var operator = token(admin, UserTypeEnum.ADMIN, "coupon:template:page");
        mvc.perform(post("/admin/ai/assistant/coupon/chat").header("Authorization", operator)
            .contentType("application/json").content(body))
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.data.text").value(org.hamcrest.Matchers.containsString("1 款")));
        var merchantToken = token(owner, UserTypeEnum.MERCHANT);
        mvc.perform(post("/merchant/governance/ai/assistant/coupon/chat").header("Authorization", merchantToken)
            .contentType("application/json").content(body))
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.data.text").value(org.hamcrest.Matchers.containsString("1 款")));
        assertThrows(CoolException.class, () -> assistant.chat(owner, false, new Chat("统计", List.of(), -1L, null, null)));
    }

    @Test void assistantDraftAndConfirmedMutations() {
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:template:add",
            "coupon:template:update", "coupon:template:delete");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        intent("CREATE", new Fields("新券", "CASH", null, BigDecimal.ZERO, null, null, null, null));
        assertTrue(assistant.chat(admin, true, chat(null)).text().contains("抵扣金额"));
        intent("CREATE", new Fields("新券", "CASH", null, BigDecimal.ZERO, null, BigDecimal.TEN, null, null));
        assertNull(assistant.chat(admin, true, chat(null)).draft().getId());
        var coupon = fixture("旧券");
        intent("UPDATE", new Fields("已改名", null, null, null, null, null, null, null));
        var update = assistant.chat(admin, true, chat(coupon.getId()));
        assertEquals("旧券", update.changes().get(0).before());
        assertEquals("旧券", coupons.selectOneById(coupon.getId()).getName());
        assistant.confirm(admin, true, update.token());
        assertEquals("已改名", coupons.selectOneById(coupon.getId()).getName());
        intent("DELETE", null);
        var deletion = assistant.chat(admin, true, chat(coupon.getId()));
        assertNotNull(coupons.selectOneById(coupon.getId()));
        assistant.confirm(admin, true, deletion.token());
        assertNull(coupons.selectOneById(coupon.getId()));
    }

    @Test void assistantPaidQuantityAndSuccessfulRedemption() {
        String code = settlementCode();
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:purchaseOrder:page", "coupon:consumptionOrder:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        doReturn(new Intent("SALES", null, "结算代金券", null, null, null, null)).when(assistant).interpret(any());
        assertTrue(assistant.chat(admin, true, chat(null)).text().contains("已支付销量 1 张"));
        doReturn(new Intent("REDEEM", null, "结算代金券", null, null, null, null)).when(assistant).interpret(any());
        assertTrue(assistant.chat(admin, true, chat(null)).text().contains("成功核销 0 次"));
        assertNotNull(code);
        fixture("无销量测试券");
        intent("RANK_SALES", null);
        var rank = assistant.chat(admin, true, chat(null));
        assertEquals("结算代金券", rank.choices().get(0).name());
        assertTrue(rank.text().contains("1 张"));
    }

    @Test void assistantFindDetailThenModifyAndSubmit() {
        authenticateOperator();
        var target = fixture("江边咖啡代金券");
        fixture("江边其他券");
        doReturn(new Intent("FIND", null, "咖啡", "CASH", null, null, null, 0, 0, 0, null, null, null, null))
            .when(assistant).interpret(any());
        var result = assistant.chat(admin, true, chat(null));
        assertTrue(result.searchResult());
        assertEquals(List.of(target.getId()), result.choices().stream().map(Candidate::id).toList());
        assertEquals(target.getName(), assistant.detail(admin, true, chat(target.getId())).detail().getName());
        activityPermission(owner, "coupon:view");
        assertThrows(CoolException.class, () -> assistant.detail(owner, false, new Chat("详情", List.of(), -1L, null, target.getId())));
        intent("UPDATE", new Fields(null, null, "早上可用", null, null, null, null, null));
        execute(target.getId(), "修改");
        intent("ENABLE", null);
        execute(target.getId(), "启用");
        intent("SUBMIT", null);
        execute(target.getId(), "提交审核");
        assertEquals(1, ((Number) assistant.detail(admin, true, chat(target.getId())).detail().getPublication().get("auditStatus")).intValue());
        assertEquals("早上可用", coupons.selectOneById(target.getId()).getDescription());
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, assistant.chat(admin, true, chat(target.getId())).token()));
    }

    @Test void assistantBatchReturnsSeparateTokensAndDetectsStateConflict() {
        authenticateOperator();
        var first = fixture("批量草稿");
        var second = fixture("状态变更券");
        doReturn(new Intent("DELETE", null, null, null, null, null, null, null, null, null, null, true, null, null))
            .when(assistant).interpret(any());
        var preview = assistant.chat(admin, true, new Chat("删除已选券", List.of(), merchant, null, null, List.of(first.getId(), second.getId())));
        assertEquals(2, preview.batch().size());
        var changed = new CouponTemplateEntity();
        changed.setId(second.getId());
        changed.setStatus(1);
        coupons.update(changed);
        var allowed = preview.batch().stream().filter(item -> item.label().contains(first.getName())).findFirst().orElseThrow();
        var conflict = preview.batch().stream().filter(item -> item.label().contains(second.getName())).findFirst().orElseThrow();
        assistant.confirm(admin, true, allowed.token());
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, conflict.token()));
        assertNull(coupons.selectOneById(first.getId()));
        assertEquals(1, coupons.selectOneById(second.getId()).getStatus());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void assistantLiveModelClarifiesStockAndSubmits() {
        stockPermissions();
        var target = stockFixture("烧烤套餐一份");
        var ids = List.of(target.getId());
        var question = assistant.chat(owner, false, stockChat("把这个券的库存调整到50，并提交审核", ids, null));
        assertEquals("SINGLE", question.clarification().type(), question.text());
        var preview = assistant.chat(owner, false, stockChat("我是说剩下能领的数量", ids,
            new Answer(question.clarification().token(), null)));
        assertStockPreview(preview, 60L);
        assertEquals(100L, coupons.selectOneById(target.getId()).getIssueLimit());
        assertTrue(assistant.confirm(owner, false, preview.token()).contains("待审核"));
        assertEquals(60L, coupons.selectOneById(target.getId()).getIssueLimit());
    }

    @Test void assistantStockChoiceKeepsTargetAndSubmitsOnce() {
        stockPermissions();
        var target = stockFixture("烧烤套餐一份");
        var other = stockFixture("双人烧烤套餐");
        var ids = List.of(target.getId());
        stockIntent("UNSPECIFIED", "50");
        var question = assistant.chat(owner, false, stockChat("把这个券的库存调整到50，并提交审核", ids, null));
        assertEquals("SINGLE", question.clarification().type());
        String clarificationToken = question.clarification().token();
        assertThrows(CoolException.class, () -> assistant.chat(owner, false,
            stockChat("剩余可领", List.of(other.getId()), new Answer(clarificationToken, "REMAINING"))));
        assertThrows(CoolException.class, () -> assistant.chat(owner, false,
            stockChat("剩余可领", ids, new Answer(clarificationToken, "UNKNOWN"))));
        var preview = assistant.chat(owner, false, stockChat("剩余可领", ids, new Answer(clarificationToken, "REMAINING")));
        assertStockPreview(preview, 60L);
        assertEquals(100L, coupons.selectOneById(target.getId()).getIssueLimit());
        assertThrows(CoolException.class, () -> assistant.chat(owner, false,
            stockChat("发行总量", ids, new Answer(clarificationToken, "TOTAL"))));
        assertTrue(assistant.confirm(owner, false, preview.token()).contains("待审核"));
        assertEquals(60L, coupons.selectOneById(target.getId()).getIssueLimit());
        assertEquals(10L, coupons.selectOneById(target.getId()).getIssuedCount());
        assertEquals(100L, coupons.selectOneById(other.getId()).getIssueLimit());
        assertEquals(1, ((Number) assistant.detail(owner, false, chat(target.getId())).detail()
            .getPublication().get("auditStatus")).intValue());
        assertThrows(CoolException.class, () -> assistant.confirm(owner, false, preview.token()));
    }

    @Test void assistantStockMultipleChoicesTextAndChangedState() {
        stockPermissions();
        var target = stockFixture("烧烤套餐一份");
        var other = stockFixture("双人烧烤套餐");
        var ids = List.of(target.getId(), other.getId());
        stockIntent("UNSPECIFIED", "50");
        var choice = assistant.chat(owner, false, stockChat("把这个券的库存调整到50，并提交审核", ids, null));
        assertEquals("CANDIDATE", choice.clarification().type());
        assertEquals(2, choice.choices().size());
        assertThrows(CoolException.class, () -> assistant.chat(owner, false,
            stockChat("其他券", ids, new Answer(choice.clarification().token(), "-1"))));
        var basis = assistant.chat(owner, false, stockChat(target.getName(), ids, new Answer(choice.clarification().token(), null)));
        assertEquals("SINGLE", basis.clarification().type());
        stockIntent("TOTAL", null);
        var preview = assistant.chat(owner, false, stockChat("我说的是总共发多少张", ids, new Answer(basis.clarification().token(), null)));
        assertStockPreview(preview, 50L);
        var changed = new CouponTemplateEntity();
        changed.setId(target.getId());
        changed.setIssuedCount(11L);
        coupons.update(changed);
        assertThrows(CoolException.class, () -> assistant.confirm(owner, false, preview.token()));
        assertEquals(100L, coupons.selectOneById(target.getId()).getIssueLimit());
    }

    @Test void assistantStockExplicitNumberAndPermissionChecks() {
        stockPermissions();
        var target = stockFixture("烧烤套餐一份");
        var ids = List.of(target.getId());
        stockIntent("REMAINING", "50");
        assertStockPreview(assistant.chat(owner, false, stockChat("剩余可领50张并送审", ids, null)), 60L);
        assertThrows(CoolException.class, () -> assistant.chat(owner, false, stockChat("改库存", List.of(-1L), null)));
        stockIntent("TOTAL", "5");
        assertBlocked(assistant.chat(owner, false, stockChat("发行总量设为5并送审", ids, null)));
        stockIntent("TOTAL", "12.5");
        assertBlocked(assistant.chat(owner, false, stockChat("发行总量设为12.5并送审", ids, null)));
        stockIntent("REMAINING", null);
        var number = assistant.chat(owner, false, stockChat("调整剩余可领数量后送审", ids, null));
        assertEquals("NUMBER", number.clarification().type());
        assertStockPreview(assistant.chat(owner, false, stockChat("50", ids, new Answer(number.clarification().token(), null))), 60L);
        var viewer = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(viewer, null, viewer.getAuthorities()));
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, stockChat("修改库存并送审", ids, null)));
        assertEquals(100L, coupons.selectOneById(target.getId()).getIssueLimit());
    }

    @Test void assistantStockUpdateRollsBackWhenPublicationFails() {
        authenticateOperator();
        var target = stockFixture("无营业门店的测试券");
        var business = merchants.selectOneById(merchant);
        // End the test-managed transaction so confirm owns its production transaction boundary.
        org.springframework.test.context.transaction.TestTransaction.end();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            business.setId(null);
            merchants.insertSelective(business);
            target.setId(null);
            target.setMerchantId(business.getId());
            coupons.insertSelective(target);
        });
        stockIntent("REMAINING", "50");
        try {
            var preview = assistant.chat(admin, true, new Chat("把剩余可领设为50并送审", List.of(),
                business.getId(), null, null, List.of(target.getId())));
            assertStockPreview(preview, 60L);
            var failure = assertThrows(CoolException.class, () -> assistant.confirm(admin, true, preview.token()));
            assertTrue(failure.getMessage().contains("没有营业中的适用门店"), failure.getMessage());
            assertEquals(100L, coupons.selectOneById(target.getId()).getIssueLimit());
            assertEquals(1, coupons.selectOneById(target.getId()).getRuleVersion());
        } finally {
            transaction.executeWithoutResult(status -> {
                coupons.deleteById(target.getId());
                merchants.deleteById(business.getId());
            });
        }
    }

    @Test void assistantStockAlreadyAtRequestedValueStillSubmits() {
        stockPermissions();
        var target = stockFixture("库存已经达标的券");
        stockIntent("REMAINING", "90");
        var preview = assistant.chat(owner, false, stockChat("剩余可领设为90张并送审", List.of(target.getId()), null));
        assertTrue(preview.text().contains("直接提交当前版本"));
        assertTrue(assistant.confirm(owner, false, preview.token()).contains("库存已是指定值"));
        assertEquals(1, coupons.selectOneById(target.getId()).getRuleVersion());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void assistantLiveModelFindModifyAndSubmit() {
        authenticateOperator();
        var target = fixture("滨江咖啡测试代金券");
        fixture("滨江其他测试券");
        String question = "找名称含滨江咖啡的草稿代金券";
        var found = assistant.chat(admin, true, new Chat(question, List.of(), merchant, null, null));
        assertTrue(found.searchResult(), found.text());
        assertEquals(List.of(target.getId()), found.choices().stream().map(Candidate::id).toList());
        assertEquals(target.getName(), assistant.detail(admin, true, chat(target.getId())).detail().getName());
        var history = List.of(new Turn("user", question), new Turn("assistant", found.text()));
        var modify = assistant.chat(admin, true, new Chat("把这张券的使用说明改成仅工作日可用", history, merchant, null, target.getId()));
        assertEquals("修改", modify.operation(), modify.text());
        assistant.confirm(admin, true, modify.token());
        assertEquals("仅工作日可用", coupons.selectOneById(target.getId()).getDescription());
        var enable = assistant.chat(admin, true, new Chat("启用这张券", history, merchant, null, target.getId()));
        assertEquals("启用", enable.operation(), enable.text());
        assistant.confirm(admin, true, enable.token());
        var submit = assistant.chat(admin, true, new Chat("把这张券提交审核", history, merchant, null, target.getId()));
        assertEquals("提交审核", submit.operation(), submit.text());
        assistant.confirm(admin, true, submit.token());
        assertEquals(1, ((Number) assistant.detail(admin, true, chat(target.getId())).detail().getPublication().get("auditStatus")).intValue());
    }

    private void stockPermissions() {
        activityPermission(owner, "coupon:view");
        activityPermission(owner, "coupon:update");
        activityPermission(owner, "coupon:submitAudit");
    }

    @Test void singleDeletePlansLegalPrerequisiteAndRejectsIssuedCoupons() {
        authenticateOperator();
        var target = fixture("可禁用删除的券");
        target.setStatus(1); coupons.update(target);
        intent("DELETE", null);
        var preview = assistant.chat(admin, true, chat(target.getId()));
        assertEquals("禁用并删除", preview.operation());
        assertEquals(1, coupons.selectOneById(target.getId()).getStatus());
        assertEquals("我已经帮你禁用了这张券，随后也把它删掉了。", assistant.confirm(admin, true, preview.token()));
        assertNull(coupons.selectOneById(target.getId()));
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, preview.token()));
        var issued = stockFixture("已发放不能删");
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, chat(issued.getId())));
        assertEquals(1, coupons.selectOneById(issued.getId()).getStatus());
    }

    @Test void singleDeleteRollsBackDisableOnDeletionFailure() {
        authenticateOperator();
        var target = fixture("删除失败回滚券"); target.setStatus(1);
        var business = merchants.selectOneById(merchant);
        org.springframework.test.context.transaction.TestTransaction.end();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            business.setId(null); merchants.insertSelective(business);
            target.setId(null); target.setMerchantId(business.getId()); coupons.insertSelective(target);
        });
        try {
            intent("DELETE", null);
            var preview = assistant.chat(admin, true, new Chat("删除这张券", List.of(), business.getId(), null, target.getId()));
            org.mockito.Mockito.doReturn(false).when(templateService).delete(target.getId());
            var error = assertThrows(CoolException.class, () -> assistant.confirm(admin, true, preview.token()));
            assertTrue(error.getMessage().contains("两步均未生效"));
            assertEquals(1, coupons.selectOneById(target.getId()).getStatus());
        } finally {
            transaction.executeWithoutResult(status -> { coupons.deleteById(target.getId()); merchants.deleteById(business.getId()); });
        }
    }

    @Test void singleDeleteRejectsMissingPrerequisitePermissionAndChangedState() {
        var target = fixture("删除状态冲突券"); target.setStatus(1); coupons.update(target);
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:template:delete");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        intent("DELETE", null);
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, chat(target.getId())));
        authenticateOperator();
        var preview = assistant.chat(admin, true, chat(target.getId()));
        target.setIssuedCount(1L); coupons.update(target);
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, preview.token()));
        assertEquals(1, coupons.selectOneById(target.getId()).getStatus());
    }

    @Test void singleDeleteRejectsPublicationHistoryAfterWithdrawal() {
        authenticateOperator();
        var target = fixture("有审核历史的券"); target.setStatus(1); coupons.update(target);
        intent("SUBMIT", null); execute(target.getId(), "提交审核");
        intent("WITHDRAW", null); execute(target.getId(), "撤回审核");
        intent("DELETE", null);
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, chat(target.getId())));
        assertEquals(1, coupons.selectOneById(target.getId()).getStatus());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void liveSingleDeleteRecognizesIntentAndPlansPrerequisite() {
        activityPermission(owner, "coupon:view"); activityPermission(owner, "coupon:update"); activityPermission(owner, "coupon:delete");
        var target = fixture("真实模型删除测试券"); target.setStatus(1); coupons.update(target);
        var preview = assistant.chat(owner, false, new Chat("把这张券删掉", List.of(), merchant, null, target.getId()));
        assertEquals("禁用并删除", preview.operation(), preview.text());
        assertEquals(1, coupons.selectOneById(target.getId()).getStatus());
        assertTrue(assistant.confirm(owner, false, preview.token()).contains("随后也把它删掉了"));
        assertNull(coupons.selectOneById(target.getId()));
    }

    @Test void assistantClarifiesSeveralFieldsWithoutLosingKnownValuesOrSubmission() {
        stockPermissions();
        var target = discountFixture();
        var ids = List.of(target.getId());
        var fields = new Fields(null, null, null, new BigDecimal("29.90"), null, null, null, null);
        doReturn(new Intent("UPDATE_AND_SUBMIT", null, null, null, null, null, fields,
            null, null, null, null, null, null, null, null, List.of("salePrice", "discountRate", "description")))
            .when(assistant).interpret(any());
        var first = assistant.chat(owner, false, stockChat("售价改成29.9元，再改一下折扣和说明，改完送审", ids, null));
        assertEquals("你想改成几折？", first.clarification().question());
        assertEquals("NUMBER", first.clarification().type());
        var second = assistant.chat(owner, false, stockChat("8 折", ids, new Answer(first.clarification().token(), null)));
        assertEquals("TEXT", second.clarification().type());
        assertTrue(second.text().contains("使用说明"));
        assertThrows(CoolException.class, () -> assistant.chat(owner, false,
            stockChat("9折", ids, new Answer(first.clarification().token(), null))));
        var preview = assistant.chat(owner, false,
            stockChat("仅工作日可用", ids, new Answer(second.clarification().token(), null)));
        assertEquals("修改并提交审核", preview.operation());
        assertEquals(List.of("使用说明", "售价", "折扣数", "审核状态"), preview.changes().stream().map(Change::field).toList());
        assertEquals(0, new BigDecimal("9").compareTo(coupons.selectOneById(target.getId()).getDiscountRate()));
        assistant.confirm(owner, false, preview.token());
        var saved = coupons.selectOneById(target.getId());
        assertEquals(0, new BigDecimal("8").compareTo(saved.getDiscountRate()));
        assertEquals(0, new BigDecimal("29.90").compareTo(saved.getSalePrice()));
        assertEquals("仅工作日可用", saved.getDescription());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void assistantLiveModelRecognizesMissingDiscount() {
        stockPermissions();
        var target = discountFixture();
        var ids = List.of(target.getId());
        var question = assistant.chat(owner, false, stockChat("给这个券改一下折扣，改完送审", ids, null));
        assertEquals("你想改成几折？", question.clarification().question());
        var answer = new Answer(question.clarification().token(), null);
        var blocked = assistant.chat(owner, false, stockChat("10折", ids, answer));
        assertBlocked(blocked);
        var preview = assistant.chat(owner, false, stockChat("8 折", ids, new Answer(blocked.clarification().token(), null)));
        assertEquals("修改并提交审核", preview.operation());
        assertTrue(preview.changes().stream().anyMatch(change -> "折扣数".equals(change.field())
            && new BigDecimal("8").compareTo((BigDecimal) change.after()) == 0));
    }

    private CouponTemplateEntity discountFixture() {
        var target = stockFixture("折扣助手测试券");
        target.setType("DISCOUNT");
        target.setDiscountRate(new BigDecimal("9"));
        coupons.update(target);
        return target;
    }

    @Test void conversationalRevisionAndCancellationInvalidateOldConfirmation() {
        stockPermissions();
        var target = stockFixture("对话测试券");
        var ids = List.of(target.getId());
        stockIntent("REMAINING", "50");
        var preview = assistant.chat(owner, false, stockChat("剩余库存改成50并送审", ids, null));
        var revised = assistant.chat(owner, false, new Chat("改成80", List.of(), merchant, null, null, ids, null, preview.token()));
        assertStockPreview(revised, 90L);
        assertThrows(CoolException.class, () -> assistant.confirm(owner, false, preview.token()));
        var cancelled = assistant.chat(owner, false, new Chat("先别改", List.of(), merchant, null, null, ids, null, revised.token()));
        assertTrue(cancelled.text().contains("取消"));
        assertThrows(CoolException.class, () -> assistant.confirm(owner, false, revised.token()));
        assertEquals(100L, coupons.selectOneById(target.getId()).getIssueLimit());
    }

    @Test void conversationalSelectionAndQuantityCorrectionKeepPendingIntent() {
        stockPermissions();
        var first = stockFixture("对话第一张券");
        var second = stockFixture("对话第二张券");
        stockIntent("UNSPECIFIED", "50");
        var choice = assistant.chat(owner, false, stockChat("改库存并送审", List.of(first.getId(), second.getId()), null));
        assertEquals("CANDIDATE", choice.clarification().type());
        var selected = assistant.chat(owner, false, stockChat("就这张", List.of(second.getId()), null));
        assertEquals("SINGLE", selected.clarification().type());
        var corrected = assistant.chat(owner, false, stockChat("改成80", List.of(second.getId()), new Answer(selected.clarification().token(), null)));
        assertTrue(corrected.text().contains("80"));
        var preview = assistant.chat(owner, false, stockChat("剩余可领数量", List.of(second.getId()),
            new Answer(corrected.clarification().token(), null)));
        assertStockPreview(preview, 90L);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void liveNaturalConversation() {
        stockPermissions();
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        var target = stockFixture("对话烧烤测试券");
        var ids = List.of(target.getId());
        var history = new java.util.ArrayList<Turn>();
        for (String text : List.of("你好，你是谁？", "那我怎么用你呢？", "找名称包含对话烧烤的券")) {
            var answer = assistant.chat(owner, false, new Chat(text, history, merchant, null, null));
            assertFalse(answer.text().contains("提供完整券名"), answer.text());
            if (text.startsWith("找")) assertEquals(target.getId(), answer.choices().get(0).id());
            System.out.println("NATURAL_USER " + text + "\nNATURAL_ASSISTANT " + answer.text());
            history.add(new Turn("user", text)); history.add(new Turn("assistant", answer.text()));
        }
        var ask = assistant.chat(owner, false, new Chat("把这个券的库存调到50，改完送审", history, merchant, null, null, ids));
        System.out.println("NATURAL_ASSISTANT " + ask.text());
        assertNotNull(ask.clarification(), ask.text());
        assertEquals("SINGLE", ask.clarification().type());
        var preview = assistant.chat(owner, false, stockChat("剩余可领数量", ids, new Answer(ask.clarification().token(), null)));
        assertStockPreview(preview, 60L);
        var revise = assistant.chat(owner, false, new Chat("改成80", history, merchant, null, null, ids, null, preview.token()));
        assertStockPreview(revise, 90L);
        System.out.println("NATURAL_USER 改成80\nNATURAL_ASSISTANT " + revise.text());
        var cancel = assistant.chat(owner, false, new Chat("先别改", history, merchant, null, null, ids, null, revise.token()));
        assertTrue(cancel.text().contains("取消"));
        System.out.println("NATURAL_USER 先别改\nNATURAL_ASSISTANT " + cancel.text());
        assertThrows(CoolException.class, () -> assistant.confirm(owner, false, revise.token()));
    }

    @Test void queryPriceCountsUseTemplatesAndSameScopeDenominator() {
        authenticateOperator();
        priceFixtures();
        queryIntent("RESET", "FREE", null, null, "TYPE", List.of(), List.of());
        var free = assistant.chat(admin, true, queryChat("免费领取有几张，占多少", null, List.of()));
        assertCount(free, 2, 4, "50.00");
        assertTrue(free.text().contains("不是发行或领取张数"));
        assertNotNull(free.queryToken());
        queryIntent("INHERIT", "PAID", null, null, "NONE", List.of(), List.of());
        var paid = assistant.chat(admin, true, queryChat("付费呢", free.queryToken(), List.of()));
        assertCount(paid, 2, 4, "50.00");
        queryIntent("INHERIT", null, null, null, "PRICE", List.of("price"), List.of());
        var all = assistant.chat(admin, true, queryChat("不限制售价，分免费付费统计", paid.queryToken(), List.of()));
        assertCount(all, 4, 4, "100.00");
        assertEquals(List.of(new GroupCount("免费领取", 2), new GroupCount("付费领取", 2)), all.countResult().groups());
    }

    @Test void queryFollowUpCombinesPublicationTypeAndCorrectedPrice() {
        authenticateOperator();
        priceFixtures();
        queryIntent("RESET", null, "CASH", 1, "NONE", List.of(), List.of());
        var base = assistant.chat(admin, true, queryChat("上架代金券有多少", null, List.of()));
        assertCount(base, 2, 2, "100.00");
        queryIntent("INHERIT", "FREE", null, null, null, List.of(), List.of());
        var free = assistant.chat(admin, true, queryChat("其中不花钱的占比", base.queryToken(), List.of()));
        assertCount(free, 1, 2, "50.00");
        assertTrue(free.countResult().scope().contains("已上架"));
        assertTrue(free.countResult().scope().contains("代金券"));
        queryIntent("INHERIT", "PAID", null, null, null, List.of(), List.of());
        var corrected = assistant.chat(admin, true, queryChat("不是免费，是付费", free.queryToken(), List.of()));
        assertCount(corrected, 1, 2, "50.00");
        queryIntent("INHERIT", "ANY", null, null, null, List.of(), List.of());
        assertCount(assistant.chat(admin, true, queryChat("占多少", corrected.queryToken(), List.of())), 1, 2, "50.00");
        queryIntent("INHERIT", null, null, null, null, List.of("publishStatus"), List.of());
        var unfilteredStatus = assistant.chat(admin, true, queryChat("不限上架状态", corrected.queryToken(), List.of()));
        assertCount(unfilteredStatus, 2, 3, "66.67");
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        assertCount(assistant.chat(admin, true, queryChat("重新看所有券，免费有多少", corrected.queryToken(), List.of())), 2, 4, "50.00");
    }

    @Test void queryPriceBoundsAndFindSharePredicates() {
        authenticateOperator();
        priceFixtures();
        var price = new PriceFilter("PAID", new BigDecimal("10"), new BigDecimal("20"), false, true);
        doReturn(new Intent("COUNT", null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, new QuerySpec("RESET", price, List.of(), "NONE", List.of()))).when(assistant).interpret(any());
        assertCount(assistant.chat(admin, true, queryChat("大于10元不超过20元", null, List.of())), 1, 4, "25.00");
        doReturn(new Intent("FIND", null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, new QuerySpec("RESET", price, List.of(), "NONE", List.of()))).when(assistant).interpret(any());
        var found = assistant.chat(admin, true, queryChat("找这些券", null, List.of()));
        assertEquals(1, found.choices().size());
        assertEquals("收费二十元", found.choices().get(0).name());
        assertTrue(found.text().contains("不高于20元"));
        assertNotNull(found.queryToken());
    }

    @Test void queryZeroDenominatorAndInvalidUnsupportedConditionsAreExplicit() {
        authenticateOperator();
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        var empty = assistant.chat(admin, true, queryChat("免费占比", null, List.of()));
        assertCount(empty, 0, 0, null);
        assertTrue(empty.text().contains("占比不适用"));
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of("仅限周末可用", "与上月环比"));
        var unsupported = assistant.chat(admin, true, queryChat("周末券环比", null, List.of()));
        assertNull(unsupported.countResult());
        assertNull(unsupported.queryToken());
        assertTrue(unsupported.text().contains("尚未执行查询"));
        assertTrue(unsupported.text().contains("仅限周末可用"));
        doReturn(new Intent("COUNT", null, null, null, "2026-01-01", "2026-01-31", null)).when(assistant).interpret(any());
        assertTrue(assistant.chat(admin, true, queryChat("一月券数", null, List.of())).text().contains("暂不支持日期筛选"));
        var invalid = new QuerySpec("RESET", new PriceFilter("ANY", BigDecimal.TEN, BigDecimal.ONE, true, true), List.of(), "NONE", List.of());
        doReturn(new Intent("COUNT", null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, invalid)).when(assistant).interpret(any());
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, queryChat("冲突价格", null, List.of())));
    }

    @Test void queryContextIsActorBoundAndRechecksAuthorizationAndSelection() {
        authenticateOperator();
        priceFixtures();
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        var base = assistant.chat(admin, true, queryChat("免费数量", null, List.of()));
        activityPermission(owner, "coupon:view");
        queryIntent("INHERIT", "PAID", null, null, null, List.of(), List.of());
        assertThrows(CoolException.class, () -> assistant.chat(owner, false, queryChat("付费呢", base.queryToken(), List.of())));
        assertThrows(CoolException.class, () -> assistant.chat(admin, true,
            new Chat("付费呢", List.of(), merchant, store, null, null, null, null, base.queryToken())));
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, queryChat("付费呢", "forged", List.of())));
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        assertCount(assistant.chat(owner, false, queryChat("免费数量", null, List.of())), 2, 4, "50.00");
        assertThrows(CoolException.class, () -> assistant.chat(owner, false, new Chat("查其他商家", List.of(), -1L, null, null)));
    }

    @Test void queryReportCombinesRealMetricsAndReportsPermissionFailures() {
        settlementCode();
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:purchaseOrder:page", "coupon:consumptionOrder:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        doReturn(new Intent("REPORT", null, "结算代金券", null, null, null, null, null, null, null, null, null, null, null,
            null, null, new QuerySpec("RESET", null, List.of(), "NONE", List.of(), List.of("SALES", "REDEEM"), 30)))
            .when(assistant).interpret(any());
        var answer = assistant.chat(admin, true, queryChat("销量和核销都看看", null, List.of()));
        assertEquals("查询汇总", answer.operation());
        assertTrue(answer.text().contains("已支付销量 1 张"), answer.text());
        assertTrue(answer.text().contains("成功核销 0 次"), answer.text());
        assertTrue(answer.text().contains(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(29).toString()), answer.text());
        operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:purchaseOrder:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        var partial = assistant.chat(admin, true, queryChat("销量和核销都看看", null, List.of()));
        assertTrue(partial.text().contains("已支付销量 1 张"), partial.text());
        assertTrue(partial.text().contains("成功核销未完成"), partial.text());
        assertFalse(partial.text().contains("成功核销 0 次"), partial.text());
    }

    @Test void queryMissingSalePriceIsNormalizedByBusinessRules() {
        var coupon = fixture("未填价格");
        coupon.setSalePrice(null);
        com.cool.modules.coupon.service.impl.CouponTemplateRules.validate(coupon);
        assertEquals(BigDecimal.ZERO, coupon.getSalePrice());
    }

    @Test void queryStoreStaffScopeExcludesUnauthorizedStores() {
        Long scopedUser = owner;
        var employee = users.selectOneById(scopedUser); employee.setMerchantRole("MEMBER"); employee.setStoreScope("SELECTED"); users.update(employee);
        activityPermission(scopedUser, "coupon:view");
        var selection = new com.cool.modules.workspace.entity.WorkspaceUserScopeEntity();
        selection.setUserId(scopedUser); selection.setStoreId(store); userScopes.insertSelective(selection);
        for (Long branch : List.of(store, otherStore)) {
            var coupon = fixture("门店免费券" + branch); coupon.setScopeType(1); coupons.update(coupon);
            var link = new com.cool.modules.workspace.entity.WorkspaceCouponStoreEntity();
            link.setMerchantId(merchant); link.setCouponTemplateId(coupon.getId()); link.setStoreId(branch); assistantStores.insertSelective(link);
        }
        fixture("全部门店券");
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        assertCount(assistant.chat(scopedUser, false, new Chat("免费款数", List.of(), merchant, store, null)), 1, 1, "100.00");
        assertThrows(CoolException.class, () -> assistant.chat(scopedUser, false, new Chat("免费款数", List.of(), merchant, otherStore, null)));
        assertThrows(CoolException.class, () -> assistant.chat(staff, false, new Chat("免费款数", List.of(), merchant, otherStore, null)));
    }

    @Test void queryHttpReturnsStructuredCountsAndContinuationToken() throws Exception {
        fixture("HTTP免费券");
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        var operator = token(admin, UserTypeEnum.ADMIN, "coupon:template:page");
        mvc.perform(post("/admin/ai/assistant/coupon/chat").header("Authorization", operator)
            .contentType("application/json").content("{\"text\":\"免费占比\",\"merchantId\":" + merchant + "}"))
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.data.queryToken").isString())
            .andExpect(jsonPath("$.data.countResult.matched").value(1))
            .andExpect(jsonPath("$.data.countResult.baseTotal").value(1))
            .andExpect(jsonPath("$.data.countResult.percentage").value(100));
    }

    @Test void revisionRemovesOneFieldAndPreservesOtherChangesWithoutExecuting() {
        authenticateOperator();
        var coupon = fixture("折扣测试"); coupon.setType("DISCOUNT"); coupon.setDiscountRate(new BigDecimal("9")); coupons.update(coupon);
        intent("UPDATE", new Fields(null, null, null, new BigDecimal("29.9"), null, null, null, new BigDecimal("8.5")));
        var first = assistant.chat(admin, true, chat(coupon.getId()));
        assertEquals(2, first.changes().size());
        doReturn(new Intent("UPDATE", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            List.of(), null, new Revision(List.of("salePrice"), null))).when(assistant).interpret(any());
        var second = assistant.chat(admin, true, new Chat("售价保持原样，折扣继续", List.of(), merchant, null, coupon.getId(), null, null, first.token()));
        assertEquals(1, second.changes().size());
        assertTrue(second.changes().stream().noneMatch(change -> change.field().contains("售价")));
        assertEquals(0, coupons.selectOneById(coupon.getId()).getSalePrice().compareTo(BigDecimal.ZERO));
        assertEquals(0, coupons.selectOneById(coupon.getId()).getDiscountRate().compareTo(new BigDecimal("9")));
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, first.token()));
        assistant.chat(admin, true, new Chat("先别改", List.of(), merchant, null, coupon.getId(), null, null, second.token()));
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, second.token()));
    }

    @Test void failedEditCanBeFollowedByNewQueryAndTargetChangesRejectOldPlans() {
        authenticateOperator();
        var first = fixture("代金券"); var second = fixture("另一张代金券");
        doReturn(new Intent("UPDATE", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            List.of("discountRate"))).when(assistant).interpret(any());
        assertBlocked(assistant.chat(admin, true, chat(first.getId())));
        queryIntent("RESET", "FREE", null, null, "NONE", List.of(), List.of());
        assertCount(assistant.chat(admin, true, queryChat("那查一下免费款数", null, List.of())), 2, 2, "100.00");
        intent("UPDATE", new Fields(null, null, null, BigDecimal.TEN, null, null, null, null));
        var plan = assistant.chat(admin, true, chat(first.getId()));
        assertThrows(CoolException.class, () -> assistant.chat(admin, true,
            new Chat("改成20", List.of(), merchant, null, second.getId(), null, null, plan.token())));
        assertEquals(0, coupons.selectOneById(second.getId()).getSalePrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void queryLiveCombinedStatistics() {
        settlementCode();
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:purchaseOrder:page", "coupon:consumptionOrder:page");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        var answer = assistant.chat(admin, true, queryChat("帮我汇总结算代金券最近30天卖出的张数，以及实际用掉了几次，两项都要", null, List.of()));
        System.out.println("QUERY_COMBINED " + answer.text());
        assertEquals("查询汇总", answer.operation(), answer.text());
        assertTrue(answer.text().matches("(?s).*1\\s*张.*"), answer.text());
        assertTrue(answer.text().matches("(?s).*0\\s*次.*"), answer.text());
        assertTrue(answer.text().contains("30天") || answer.text().contains("30 天")
            || answer.text().contains(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(29).toString()), answer.text());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void queryLiveRevisionAndFailureRecovery() {
        authenticateOperator();
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        var coupon = fixture("专用折扣测试券"); coupon.setType("DISCOUNT"); coupon.setDiscountRate(new BigDecimal("9")); coupons.update(coupon);
        var first = assistant.chat(admin, true, new Chat("优惠幅度调到八五折，卖价定在29.9元", List.of(), merchant, null, coupon.getId()));
        System.out.println("QUERY_EDIT " + first.text());
        assertNotNull(first.token(), first.text()); assertEquals(2, first.changes().size());
        var second = assistant.chat(admin, true, new Chat("售价按原来的来，但折扣不要撤回", List.of(), merchant, null, coupon.getId(), null, null, first.token()));
        System.out.println("QUERY_REVISE " + second.text());
        assertNotNull(second.token(), second.text()); assertEquals(1, second.changes().size());
        assertTrue(second.changes().stream().noneMatch(change -> change.field().contains("售价")));
        var cancel = assistant.chat(admin, true, new Chat("算了，这一轮先不执行", List.of(), merchant, null, coupon.getId(), null, null, second.token()));
        System.out.println("QUERY_CANCEL " + cancel.text());
        assertTrue(cancel.text().contains("取消"), cancel.text());
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, second.token()));
        assertEquals(0, coupons.selectOneById(coupon.getId()).getDiscountRate().compareTo(new BigDecimal("9")));
        var cash = fixture("专用代金券");
        try {
            var failed = assistant.chat(admin, true, new Chat("这张券打八折", List.of(), merchant, null, cash.getId()));
            assertNull(failed.token(), failed.text());
            System.out.println("QUERY_LIMIT " + failed.text());
        } catch (CoolException limit) { System.out.println("QUERY_LIMIT " + limit.getMessage()); }
        var recovered = assistant.chat(admin, true, queryChat("不改了，告诉我不用付钱领取的有几款", null, List.of()));
        assertCount(recovered, 2, 2, "100.00");
        System.out.println("QUERY_RECOVERY " + recovered.text());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void queryLivePriceFollowUpsUseDatabaseFacts() {
        authenticateOperator();
        priceFixtures();
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        org.mockito.Mockito.doAnswer(call -> {
            var extracted = (Intent) call.callRealMethod();
            System.out.println("QUERY_INTENT " + extracted);
            return extracted;
        }).when(assistant).interpret(any());
        var history = new java.util.ArrayList<Turn>();
        Reply result = liveQuery("我们一共配置了多少种券？", null, history);
        assertCount(result, 4, 4, "100.00");
        result = liveQuery("里头不用付钱领的有几种？在刚才那些里面占几成？", result.queryToken(), history);
        assertCount(result, 2, 4, "50.00");
        result = liveQuery("我换个条件，收钱的呢？", result.queryToken(), history);
        assertCount(result, 2, 4, "50.00");
        result = liveQuery("继续缩小到已经上架的代金券", result.queryToken(), history);
        assertCount(result, 1, 2, "50.00");
        result = liveQuery("占多少？", result.queryToken(), history);
        assertCount(result, 1, 2, "50.00");
        String lastQueryToken = result.queryToken();
        result = liveQuery("在这个范围把付费改成零元领取", lastQueryToken, history);
        if (result.countResult() == null) {
            assertNull(result.token());
            assertTrue(result.text().contains("筛选") && result.text().contains("修改"), result.text());
            result = liveQuery("是调整筛选条件为零元领取，其他条件不动", lastQueryToken, history);
        }
        assertCount(result, 1, 2, "50.00");
        result = liveQuery("再加一个条件：只能周末核销；还要和去年同期比较", result.queryToken(), history);
        assertNull(result.countResult(), result.text());
        assertTrue(result.text().contains("不支持") || result.text().contains("暂") || result.text().contains("无法"), result.text());
    }

    private Reply liveQuery(String text, String token, java.util.ArrayList<Turn> history) {
        var answer = assistant.chat(admin, true, queryChat(text, token, new java.util.ArrayList<>(history)));
        System.out.println("QUERY_USER " + text + "\nQUERY_ASSISTANT " + answer.text());
        history.add(new Turn("user", text));
        history.add(new Turn("assistant", answer.text()));
        return answer;
    }

    private Chat queryChat(String text, String token, List<Turn> history) {
        return new Chat(text, history, merchant, null, null, null, null, null, token);
    }

    private void queryIntent(String context, String mode, String type, Integer publish, String group, List<String> clear, List<String> unsupported) {
        doReturn(new Intent("COUNT", null, null, type, null, null, null, null, null, publish, null, null, null, null,
            null, null, new QuerySpec(context, mode == null ? null : new PriceFilter(mode, null, null, null, null), clear, group, unsupported)))
            .when(assistant).interpret(any());
    }

    private void assertCount(Reply reply, long matched, long total, String percentage) {
        assertNotNull(reply.countResult(), reply.text());
        assertEquals(matched, reply.countResult().matched(), reply.text());
        assertEquals(total, reply.countResult().baseTotal(), reply.text());
        assertEquals(percentage == null ? null : new BigDecimal(percentage), reply.countResult().percentage(), reply.text());
        assertTrue(reply.text().matches("(?s).*" + matched + "\\s*款.*")
            || percentage != null && reply.text().contains(new BigDecimal(percentage).stripTrailingZeros().toPlainString() + "%"), reply.text());
    }

    @Test void narrationRejectsInventedNumbersAndUnresolvedSlots() {
        var count = new CountResult(3, 12, new BigDecimal("25.00"), "免费领取", "当前授权范围", List.of());
        assertEquals("免费券有3款，占25%。", assistant.renderCountNarration("免费券有{{matched}}款，占{{percentage}}%。", count));
        assertNull(assistant.renderCountNarration("免费券有8款，占25%。", count));
        assertNull(assistant.renderCountNarration("有{{matched}}款，比昨天多2款。", count));
        assertNull(assistant.renderCountNarration("有{{matched}}款，占{{unknown}}%。", count));
        assertNull(assistant.renderCountNarration("共{{baseTotal}}款。", count));
        assertNull(assistant.renderCountNarration("有{{matched}}款，占{{percentage}}%。",
            new CountResult(0, 0, null, "免费领取", "当前授权范围", List.of())));
    }

    @Test void delegatedRulesAreProposedEvenWhenBasicFieldsAlreadyExist() {
        activityPermission(owner, "coupon:view"); activityPermission(owner, "coupon:create");
        intent("CREATE", fields(java.util.Map.of("name", "工作日引流券", "type", "FULL_REDUCTION", "salePrice", 0,
            "validDays", 7, "perCustomerLimit", 1, "scopeType", 0)));
        var suggestion = fields(java.util.Map.of("name", "建议券名", "type", "FULL_REDUCTION", "salePrice", 5,
            "minSpend", 100, "discountAmount", 20, "validDays", 30, "perCustomerLimit", 2,
            "validityType", "AFTER_ISSUE", "description", "工作日使用，规则待核对"));
        doReturn(new com.cool.modules.ai.service.CouponAssistantRecovery.DraftSuggestion(true, suggestion))
            .when(assistant).proposeDraft(any(), any());
        var reply = assistant.chat(owner, false, new Chat("门槛和减免由你建议，免费领取，7天有效，每人限领1张", List.of(), null, null, null));
        assertNotNull(reply.draft(), reply.text()); assertNull(reply.draft().getId());
        assertEquals(0, reply.draft().getSalePrice().compareTo(BigDecimal.ZERO));
        assertEquals(7, reply.draft().getValidDays()); assertEquals(1, reply.draft().getPerCustomerLimit());
        assertEquals(0, reply.draft().getMinSpend().compareTo(new BigDecimal("100")));
        assertEquals(0, reply.draft().getDiscountAmount().compareTo(new BigDecimal("20")));
        doReturn(new com.cool.modules.ai.service.CouponAssistantRecovery.DraftSuggestion(false, null))
            .when(assistant).proposeDraft(any(), any());
        var missing = assistant.chat(owner, false, new Chat("创建满减券，还没决定门槛", List.of(), null, null, null));
        assertNull(missing.draft()); assertNotNull(missing.clarification());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void liveMerchantDelegatedFullReductionDraft() {
        activityPermission(owner, "coupon:view"); activityPermission(owner, "coupon:create");
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(assistant).proposeDraft(any(), any());
        org.mockito.Mockito.doAnswer(call -> {
            boolean valid = (boolean) call.callRealMethod();
            if (!valid) System.out.println("DELEGATED_REJECTED " + call.getArgument(0));
            return valid;
        }).when(testedNarrator).narrationSupported(any(), any());
        for (int round = 0; round < 3; round++) {
            var reply = assistant.chat(owner, false, new Chat("帮当前商家设计一张工作日引流用的满减券，券名、消费门槛、减免金额和使用说明由你建议，免费领取，领取后7天有效，每人限领1张，适用于全部营业门店。先生成草稿，不要保存。", List.of(), null, null, null));
            assertNotNull(reply.draft(), reply.text()); assertNull(reply.draft().getId());
            assertEquals("FULL_REDUCTION", reply.draft().getType());
            assertEquals(0, reply.draft().getSalePrice().compareTo(BigDecimal.ZERO));
            assertEquals(7, reply.draft().getValidDays()); assertEquals(1, reply.draft().getPerCustomerLimit());
            assertNotNull(reply.draft().getMinSpend()); assertNotNull(reply.draft().getDiscountAmount());
            assertTrue(reply.draft().getDiscountAmount().signum() > 0);
            assertTrue(reply.draft().getMinSpend().compareTo(reply.draft().getDiscountAmount()) >= 0);
            assertFalse(reply.text().isBlank());
        }
    }

    @Test void draftNarrationUsesDraftFactsWithoutClaimingPersistence() {
        var draft = new CouponTemplateEntity(); draft.setName("夜宵四人烧烤套餐");
        draft.setSalePrice(new BigDecimal("168")); draft.setValidDays(30); draft.setPerCustomerLimit(2);
        var item = new com.cool.modules.coupon.controller.admin.params.CouponContentParam();
        item.setName("烤鸡翅"); item.setQuantity(8); draft.setContentItems(List.of(item));
        var reply = new Reply("草稿已准备好。请核对后保存。", List.of(), draft, List.of(), null);
        assertTrue(assistant.narrationSupported("夜宵四人烧烤套餐草稿已备好：168元，含烤鸡翅8只，30天有效，每人限领2张，请确认后保存。", reply));
        assertFalse(assistant.narrationSupported("夜宵四人烧烤套餐售价188元，请确认。", reply));
        assertFalse(assistant.narrationSupported("夜宵四人烧烤套餐已经保存，请确认。", reply));
        assertFalse(assistant.narrationSupported("草稿已准备好，请确认。", reply));
    }

    @Test void draftSurvivesUnavailableNarrationWithoutFakeModelReply() {
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:template:add");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        intent("CREATE", fields(java.util.Map.of("name", "保留未保存草稿", "type", "CASH", "salePrice", 0, "deductAmount", 10)));
        org.mockito.Mockito.doThrow(new CoolException("回复校验失败", 503)).when(assistant).narrate(any(), any());
        var reply = assistant.chat(admin, true, chat(null));
        assertNotNull(reply.draft()); assertNull(reply.draft().getId()); assertEquals("", reply.text());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void liveMerchantDraftNarrationRepeatedRequests() {
        activityPermission(owner, "coupon:view"); activityPermission(owner, "coupon:create");
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        org.mockito.Mockito.doCallRealMethod().when(assistant).proposeDraft(any(), any());
        String[] requests = {
            "帮我创建一张夜宵四人烧烤套餐兑换券，适用于当前商家所有营业门店，包含羊肉串20串、牛肉串20串、烤鸡翅8只、烤茄子2份，售价168元，领取后30天有效，每人限领2张，不与其他优惠叠加。使用说明由你拟定，先生成草稿让我核对，不要直接保存。",
            "帮我起草一张招牌烧烤双人餐兑换券，供当前商家全部营业门店使用：羊肉串10串、烤鸡翅4只、烤茄子1份，售价88元，领券后15天有效，每人限领1张，不与其他优惠同享。请拟一段简洁的使用说明，生成草稿供我核对，暂不保存。"
        };
        for (int round = 0; round < 3; round++) for (String request : requests) {
            var reply = assistant.chat(owner, false, new Chat(request, List.of(), null, null, null));
            assertNotNull(reply.draft(), reply.text()); assertNull(reply.draft().getId());
            assertEquals(merchant, reply.draft().getMerchantId());
            assertFalse(reply.text().isBlank(), "真实模型回复必须通过，不能用降级掩盖失败");
            assertTrue(assistant.narrationSupported(reply.text(), reply), reply.text());
        }
    }

    @Test void narrationAllowsNaturalOmissionsButRejectsPrematureExecutionClaims() {
        var summary = new Reply("「测试券」2026-10-11 至 2026-11-09，已支付销量 1 张，成功核销 0 次。", List.of(), null,
            List.of(), null, null, false, List.of(), "查询汇总");
        assertTrue(assistant.narrationSupported("测试券最近30天卖出1张，成功核销0次。", summary));
        assertFalse(assistant.narrationSupported("测试券最近31天卖出2张，成功核销0次。", summary));
        var unsupported = new Reply("「周末核销限制、同比」暂不支持。", List.of(), null, List.of(), null);
        assertTrue(assistant.narrationSupported("周末限制和同比目前还查不了。", unsupported));
        var preview = new Reply("确认修改「测试券」？", List.of(), null, List.of(), "preview");
        assertTrue(assistant.narrationSupported("准备修改测试券，等你确认后再执行。", preview));
        assertFalse(assistant.narrationSupported("已将测试券调整好了，请确认。", preview));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void narrationLiveUsesNaturalModelWordingAndProtectedFacts() {
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        var history = new java.util.ArrayList<Turn>();
        var count = new CountResult(37, 37, new BigDecimal("100.00"), "全部售价", "当前授权商家范围（授权门店范围）", List.of());
        var result = new Reply("统计范围：当前授权商家范围。全部售价：37 款。基准总数：37 款。占比：100.00%。", List.of(), null,
            List.of(), null, null, false, List.of(), null, null, null, count);
        for (int i = 0; i < 3; i++) {
            String text = assistant.narrate(new Chat("我们一共配置了多少款优惠券？", List.copyOf(history), merchant, null, null), result);
            System.out.println("NARRATION_VARIANT " + text);
            assertTrue(text.matches("(?s).*37\\s*款.*"), text);
            assertFalse(text.contains("100%") || text.contains("统计范围：") || text.contains("基准总数") || text.contains("{{"), text);
            assertNotEquals(result.text(), text);
            history.add(new Turn("assistant", text));
        }
        // Wording diversity is observed in live output, not guaranteed by a stochastic model.
        var ratio = new CountResult(3, 12, new BigDecimal("25.00"), "免费领取（售价为0元）", "已上架代金券", List.of());
        result = new Reply("免费3款，基准12款，占比25%。", List.of(), null, List.of(), null, null, false, List.of(), null, null, null, ratio);
        String text = assistant.narrate(new Chat("这个范围里，免费领取的又占多少？", List.of(), merchant, null, null), result);
        System.out.println("NARRATION_RATIO " + text);
        assertTrue(text.contains("25%"), text);
        assertFalse(text.contains("{{"), text);
    }

    @Test void fullRuleEditPreviewAndConfirmUseExistingValidation() {
        authenticateOperator();
        var coupon = fixture("完整规则测试券");
        coupon.setType("DISCOUNT"); coupon.setDiscountRate(new BigDecimal("9")); coupons.update(coupon);
        intent("UPDATE", fields(java.util.Map.of("validityType", "FIXED", "validFrom", "2099-01-01T08:00:00", "validTo", "2099-02-01T22:00:00",
            "saleStartTime", "2098-12-01T09:00:00", "saleEndTime", "2099-01-20T21:00:00", "allowCombine", 1, "maxDiscount", 20)));
        var preview = assistant.chat(admin, true, chat(coupon.getId()));
        assertNotNull(preview.token()); assertTrue(preview.changes().size() >= 7);
        assertEquals("AFTER_ISSUE", coupons.selectOneById(coupon.getId()).getValidityType());
        assistant.confirm(admin, true, preview.token());
        var saved = coupons.selectOneById(coupon.getId());
        assertEquals("FIXED", saved.getValidityType()); assertNull(saved.getValidDays());
        assertEquals(1, saved.getAllowCombine()); assertEquals(0, new BigDecimal("20").compareTo(saved.getMaxDiscount()));
        assertNotNull(saved.getSaleEndTime()); assertEquals(2, saved.getRuleVersion());
        intent("UPDATE", fields(java.util.Map.of("validDays", 15)));
        assistant.confirm(admin, true, assistant.chat(admin, true, chat(coupon.getId())).token());
        saved = coupons.selectOneById(coupon.getId()); assertEquals("AFTER_ISSUE", saved.getValidityType());
        assertEquals(15, saved.getValidDays()); assertNull(saved.getValidFrom()); assertNull(saved.getValidTo());
    }

    @Test void incompleteFixedPeriodClarifiesWithoutWriting() {
        authenticateOperator(); var coupon = fixture("日期澄清券");
        intent("UPDATE", fields(java.util.Map.of("validityType", "FIXED", "validFrom", "2099-01-01T08:00:00")));
        var question = assistant.chat(admin, true, chat(coupon.getId()));
        assertNotNull(question.clarification()); assertTrue(question.clarification().question().contains("结束"));
        intent("UPDATE", fields(java.util.Map.of("validTo", "2099-02-01T22:00:00")));
        var preview = assistant.chat(admin, true, new Chat("2099年2月1日22点", List.of(), merchant, null, coupon.getId(), null,
            new Answer(question.clarification().token(), null)));
        assertNotNull(preview.token()); assertEquals("AFTER_ISSUE", coupons.selectOneById(coupon.getId()).getValidityType());
        assistant.chat(admin, true, new Chat("先别改", List.of(), merchant, null, coupon.getId()));
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, preview.token()));
    }

    @Test void invalidDatesAndWrongCouponFieldsRejectBeforePreview() {
        authenticateOperator(); var coupon = fixture("规则边界券");
        for (var values : List.of(java.util.Map.of("saleStartTime", "2099-02-30T08:00:00"),
            java.util.Map.of("saleStartTime", "2099-01-02T08:00:00", "saleEndTime", "2099-01-01T08:00:00"),
            java.util.Map.of("maxDiscount", 10), java.util.Map.of("allowCombine", 2))) {
            intent("UPDATE", fields(values));
            assertBlocked(assistant.chat(admin, true, chat(coupon.getId())));
        }
        assertEquals(1, coupons.selectOneById(coupon.getId()).getRuleVersion());
    }

    @Test void storeNameResolutionAndScopeAreRechecked() {
        authenticateOperator(); var coupon = fixture("范围修改券");
        var branch = stores.selectOneById(store); branch.setName(prefix + "授权店"); stores.update(branch);
        intent("UPDATE", fields(java.util.Map.of("scopeType", 1, "storeNames", List.of(branch.getName()))));
        var preview = assistant.chat(admin, true, chat(coupon.getId()));
        assertTrue(preview.changes().stream().anyMatch(change -> change.after().toString().contains(branch.getName())));
        assistant.confirm(admin, true, preview.token());
        assertEquals(List.of(store), ((CouponTemplateEntity) templateService.info(coupon.getId())).getStoreIds());
        intent("UPDATE", fields(java.util.Map.of("scopeType", 1, "storeNames", List.of("不存在的其他商家门店"))));
        assertBlocked(assistant.chat(admin, true, chat(coupon.getId())));
        var employee = users.selectOneById(owner); employee.setMerchantRole("MEMBER"); employee.setStoreScope("SELECTED"); users.update(employee);
        activityPermission(owner, "coupon:view"); activityPermission(owner, "coupon:update");
        var assigned = new com.cool.modules.workspace.entity.WorkspaceUserScopeEntity(); assigned.setUserId(owner); assigned.setStoreId(store); userScopes.insertSelective(assigned);
        intent("UPDATE", fields(java.util.Map.of("scopeType", 0)));
        assertThrows(CoolException.class, () -> assistant.chat(owner, false, chat(coupon.getId())));
        assertEquals(List.of(store), ((CouponTemplateEntity) templateService.info(coupon.getId())).getStoreIds());
    }

    @Test void contentReplacementUsesBusinessProductRelations() {
        authenticateOperator(); var coupon = fixture("内容编辑测试券"); coupon.setType("PACKAGE"); coupons.update(coupon);
        intent("UPDATE", fields(java.util.Map.of("contentItems", List.of(java.util.Map.of("name", "烤肉", "type", "GOODS", "quantity", 2, "itemDescription", "两份")))));
        var preview = assistant.chat(admin, true, chat(coupon.getId()));
        assertTrue(preview.changes().get(0).field().contains("替换"));
        assertTrue(((CouponTemplateEntity) templateService.info(coupon.getId())).getContentItems().isEmpty());
        assistant.confirm(admin, true, preview.token());
        var saved = ((CouponTemplateEntity) templateService.info(coupon.getId())).getContentItems();
        assertEquals(1, saved.size()); assertEquals("烤肉", saved.get(0).getName()); assertEquals(2, saved.get(0).getQuantity());
        intent("UPDATE", fields(java.util.Map.of("contentItems", List.of(java.util.Map.of("name", "烤肉", "quantity", 0)))));
        assertBlocked(assistant.chat(admin, true, chat(coupon.getId())));
    }

    @Test void coverHandoffRequiresEditPermissionAndDoesNotWrite() {
        authenticateOperator(); var coupon = fixture("封面交接测试券");
        intent("EDIT_COVER", null);
        var reply = assistant.chat(admin, true, chat(coupon.getId()));
        assertEquals("编辑封面", reply.operation()); assertEquals(coupon.getId(), reply.detail().getId()); assertNull(reply.token());
        activityPermission(owner, "coupon:view");
        var denied = new com.cool.modules.workspace.entity.WorkspaceGrantEntity();
        denied.setSubjectType("USER"); denied.setSubjectId(owner); denied.setAllowed(false);
        denied.setMenuId(auth.catalog().stream().filter(menu -> "coupon:update".equals(menu.getPerms()) && "MERCHANT".equals(menu.getAccountType())).findFirst().orElseThrow().getId());
        grants.insert(denied);
        assertThrows(CoolException.class, () -> assistant.chat(owner, false, chat(coupon.getId())));
        assertNull(coupons.selectOneById(coupon.getId()).getCover());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void fullFieldsLiveModelPlansWithoutPrematureWrites() {
        authenticateOperator(); org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        var coupon = fixture("完整字段模型验收券");
        var reply = assistant.chat(admin, true, new Chat("把这张券改为允许同享其他优惠，领取购买从2099年1月1日早上8点开始，到2099年1月31日晚上10点结束。", List.of(), merchant, null, coupon.getId()));
        System.out.println("FULL_FIELDS_LIVE " + reply.text());
        assertNotNull(reply.token(), reply.text());
        assertTrue(reply.changes().stream().anyMatch(c -> c.field().equals("同享其他优惠")));
        assertTrue(reply.changes().stream().anyMatch(c -> c.field().equals("领取购买结束")));
        assertNull(coupons.selectOneById(coupon.getId()).getSaleStartTime());
        assistant.confirm(admin, true, reply.token());
        assertEquals(1, coupons.selectOneById(coupon.getId()).getAllowCombine());
        reply = assistant.chat(admin, true, new Chat("想给这张券用AI做一张封面", List.of(), merchant, null, coupon.getId()));
        assertEquals("编辑封面", reply.operation(), reply.text());
    }

    private Fields fields(java.util.Map<String, ?> values) {
        return new com.fasterxml.jackson.databind.ObjectMapper().convertValue(values, Fields.class);
    }

    private void priceFixtures() {
        var free = fixture("免费代金券");
        var another = fixture("免费兑换券");
        another.setType("EXCHANGE"); coupons.update(another);
        var paid = fixture("收费十元"); paid.setSalePrice(BigDecimal.TEN); coupons.update(paid);
        var secondPaid = fixture("收费二十元"); secondPaid.setSalePrice(new BigDecimal("20")); coupons.update(secondPaid);
        for (var row : List.of(free, paid)) {
            var publication = new com.cool.modules.coupon.entity.CouponPublicationEntity();
            publication.setMerchantId(merchant); publication.setCouponTemplateId(row.getId()); publication.setChannel("PLATFORM");
            publication.setAuditStatus(2); publication.setPublishStatus(1); publication.setPublishedVersion(1);
            publications.insertSelective(publication);
        }
        // A foreign merchant's free coupon must never enter either numerator or denominator.
        var other = merchants.selectOneById(merchant); other.setId(null); other.setName(prefix + "其他商家"); merchants.insertSelective(other);
        var foreign = fixture("其他商家免费券"); foreign.setMerchantId(other.getId()); coupons.update(foreign);
    }

    private CouponTemplateEntity stockFixture(String name) {
        var coupon = fixture(name);
        coupon.setStatus(1);
        coupon.setIssueLimit(100L);
        coupon.setIssuedCount(10L);
        coupons.update(coupon);
        return coupon;
    }

    private Chat stockChat(String text, List<Long> ids, Answer answer) {
        return new Chat(text, List.of(), merchant, null, null, ids, answer);
    }

    private void stockIntent(String basis, String quantity) {
        var stock = new StockEdit(basis, quantity == null ? null : new BigDecimal(quantity));
        doReturn(new Intent("UPDATE_AND_SUBMIT", null, null, null, null, null, null, null, null, null, null, null, null, null, stock))
            .when(assistant).interpret(any());
    }

    private void assertStockPreview(Reply preview, long total) {
        assertEquals("修改并提交审核", preview.operation(), preview.text());
        assertNotNull(preview.token());
        assertEquals(total, preview.changes().stream().filter(change -> change.field().equals("发行总量")).findFirst().orElseThrow().after());
        assertTrue(preview.changes().stream().anyMatch(change -> "待审核".equals(change.after())));
    }

    @Test void publishedRuleRepairPreservesAllFieldsAndWaitsForConfirmation() {
        authenticateOperator(); var coupon = publishedRecoveryCoupon();
        intent("UPDATE", fields(java.util.Map.of("allowCombine", 1, "perCustomerLimit", 2)));
        doReturn(new com.cool.modules.ai.service.CouponAssistantRecovery.Proposal(null, null, null, List.of("UNPUBLISH")))
            .when(assistant).proposeRepair(any(), any(), any(), any(), any());
        var proposal = assistant.chat(admin, true, chat(coupon.getId()));
        assertNotNull(proposal.token());
        assertTrue(proposal.changes().stream().anyMatch(c -> c.field().equals("前置步骤")));
        assertEquals(1, publications.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
            .eq(com.cool.modules.coupon.entity.CouponPublicationEntity::getCouponTemplateId, coupon.getId())).getPublishStatus());
        assertNotEquals(2, coupons.selectOneById(coupon.getId()).getPerCustomerLimit());
        assistant.confirm(admin, true, proposal.token());
        var updated = coupons.selectOneById(coupon.getId());
        assertEquals(1, updated.getAllowCombine()); assertEquals(2, updated.getPerCustomerLimit());
        assertEquals(2, publications.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create()
            .eq(com.cool.modules.coupon.entity.CouponPublicationEntity::getCouponTemplateId, coupon.getId())).getPublishStatus());
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, proposal.token()));
    }

    @Test void modelCanProposeStockBoundaryButCannotBypassConfirmationOrPermissions() {
        authenticateOperator(); var coupon = stockFixture("库存调整方案券");
        stockIntent("TOTAL", "5");
        doReturn(new com.cool.modules.ai.service.CouponAssistantRecovery.Proposal(null, null,
            new StockEdit("TOTAL", BigDecimal.TEN), List.of())).when(assistant).proposeRepair(any(), any(), any(), any(), any());
        var proposal = assistant.chat(admin, true, new Chat("发行总量设为5并送审", List.of(), merchant, null, coupon.getId()));
        assertNotNull(proposal.token()); assertEquals(100L, coupons.selectOneById(coupon.getId()).getIssueLimit());
        assistant.confirm(admin, true, proposal.token());
        assertEquals(10L, coupons.selectOneById(coupon.getId()).getIssueLimit());
        var published = publishedRecoveryCoupon(); intent("UPDATE", fields(java.util.Map.of("allowCombine", 1)));
        doReturn(new com.cool.modules.ai.service.CouponAssistantRecovery.Proposal(null, null, null, List.of("APPROVE")))
            .when(assistant).proposeRepair(any(), any(), any(), any(), any());
        assertThrows(CoolException.class, () -> assistant.chat(admin, true, chat(published.getId())));
        assertNotEquals(1, coupons.selectOneById(published.getId()).getAllowCombine());
    }

    @Test void clarificationCanChangeValidityModeAndSwitchTask() {
        authenticateOperator(); var coupon = fixture("追问改口券");
        intent("UPDATE", fields(java.util.Map.of("validityType", "FIXED")));
        var question = assistant.chat(admin, true, chat(coupon.getId()));
        intent("UPDATE", fields(java.util.Map.of("validityType", "AFTER_ISSUE", "validDays", 15)));
        var plan = assistant.chat(admin, true, new Chat("改成领取后15天", List.of(), merchant, null, coupon.getId(), null,
            new Answer(question.clarification().token(), null)));
        assertNotNull(plan.token()); assertNull(plan.clarification());
        assistant.chat(admin, true, new Chat("先别改", List.of(), merchant, null, coupon.getId()));
        intent("UPDATE", fields(java.util.Map.of("validityType", "FIXED")));
        question = assistant.chat(admin, true, chat(coupon.getId()));
        intent("EDIT_COVER", null);
        var cover = assistant.chat(admin, true, new Chat("取消修改，打开这张券的AI封面编辑", List.of(), merchant, null, coupon.getId(), null,
            new Answer(question.clarification().token(), null)));
        assertEquals("编辑封面", cover.operation()); assertNotNull(cover.detail()); assertNull(cover.token());
        assertEquals(30, coupons.selectOneById(coupon.getId()).getValidDays());
    }

    @Test void draftSurvivesMerchantSelectionAndMissingPriceFollowUp() {
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:template:add");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        intent("CREATE", fields(java.util.Map.of("name", "保留的豪华套餐", "type", "CASH", "deductAmount", 10)));
        var question = assistant.chat(admin, true, new Chat("给这家店起草券", List.of(), null, null, null));
        assertEquals("MERCHANT", question.clarification().type());
        var price = assistant.chat(admin, true, new Chat("选好了", List.of(), merchant, null, null));
        assertEquals("CREATE", price.clarification().type());
        intent("CREATE", fields(java.util.Map.of("salePrice", 0)));
        var draft = assistant.chat(admin, true, new Chat("免费领取", List.of(), merchant, null, null, null,
            new Answer(price.clarification().token(), null)));
        assertEquals("保留的豪华套餐", draft.draft().getName()); assertEquals(merchant, draft.draft().getMerchantId());
        assertNull(draft.draft().getId());
    }

    @Test void confirmationConflictReturnsNewPlanWithoutExecutingIt() {
        authenticateOperator(); var coupon = fixture("确认冲突券");
        intent("UPDATE", fields(java.util.Map.of("description", "新说明")));
        var plan = assistant.chat(admin, true, chat(coupon.getId()));
        coupon.setName("别的操作修改了券名"); coupons.update(coupon);
        var failure = assertThrows(CoolException.class, () -> assistant.confirm(admin, true, plan.token()));
        var replacement = assistant.recoverConfirmation(admin, true, plan.token(), failure);
        assertNotNull(replacement.token()); assertNotEquals(plan.token(), replacement.token());
        assertNotEquals("新说明", coupons.selectOneById(coupon.getId()).getDescription());
        assertThrows(CoolException.class, () -> assistant.confirm(admin, true, plan.token()));
    }

    @Test void recoveryPrerequisiteRollsBackWhenOriginalEditFails() {
        authenticateOperator();
        var coupon = fixture("事务修复券"); coupon.setStatus(1);
        var business = merchants.selectOneById(merchant);
        org.springframework.test.context.transaction.TestTransaction.end();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var publication = new com.cool.modules.coupon.entity.CouponPublicationEntity();
        transaction.executeWithoutResult(status -> {
            business.setId(null); merchants.insertSelective(business);
            coupon.setId(null); coupon.setMerchantId(business.getId()); coupons.insertSelective(coupon);
            publication.setMerchantId(business.getId()); publication.setCouponTemplateId(coupon.getId()); publication.setChannel("PLATFORM");
            publication.setAuditStatus(2); publication.setPublishStatus(1); publication.setSubmittedVersion(1); publication.setPublishedVersion(1);
            publications.insertSelective(publication);
        });
        try {
            intent("UPDATE", fields(java.util.Map.of("description", "不可半途生效")));
            doReturn(new com.cool.modules.ai.service.CouponAssistantRecovery.Proposal(null, null, null, List.of("UNPUBLISH")))
                .when(assistant).proposeRepair(any(), any(), any(), any(), any());
            var plan = assistant.chat(admin, true, new Chat("修改说明", List.of(), business.getId(), null, coupon.getId()));
            org.mockito.Mockito.doThrow(new CoolException("测试保存失败")).when(templateService).update(any(CouponTemplateEntity.class));
            assertThrows(CoolException.class, () -> assistant.confirm(admin, true, plan.token()));
            assertEquals(1, publications.selectOneById(publication.getId()).getPublishStatus());
            assertNotEquals("不可半途生效", coupons.selectOneById(coupon.getId()).getDescription());
        } finally {
            org.mockito.Mockito.doCallRealMethod().when(templateService).update(any(CouponTemplateEntity.class));
            transaction.executeWithoutResult(status -> { publications.deleteById(publication.getId()); coupons.deleteById(coupon.getId()); merchants.deleteById(business.getId()); });
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void liveModelReplansBlockedTaskAndAcceptsTaskSwitch() {
        authenticateOperator(); var coupon = publishedRecoveryCoupon();
        org.mockito.Mockito.doCallRealMethod().when(assistant).proposeRepair(any(), any(), any(), any(), any());
        org.mockito.Mockito.doCallRealMethod().when(assistant).narrate(any(), any());
        org.mockito.Mockito.doAnswer(call -> {
            boolean valid = (boolean) call.callRealMethod();
            if (!valid) System.out.println("RECOVERY_REJECTED_NARRATION " + call.getArgument(0) + " VERIFIED " + ((Reply) call.getArgument(1)).text());
            return valid;
        }).when(testedNarrator).narrationSupported(any(), any());
        var plan = assistant.chat(admin, true, new Chat("这张券允许同享其他优惠，每人限领改成两张，先给我看看修改结果", List.of(), merchant, null, coupon.getId()));
        assertNotNull(plan.token(), plan.text());
        assertTrue(plan.changes().stream().anyMatch(c -> c.field().equals("前置步骤")), plan.text());
        assertTrue(plan.changes().stream().anyMatch(c -> c.field().contains("限领")), plan.text());
        assertFalse(plan.text().contains("不支持"), plan.text());
        assistant.chat(admin, true, new Chat("先别改", List.of(), merchant, null, coupon.getId()));
        var question = assistant.chat(admin, true, new Chat("这张券有效期改成固定日期，具体起止时间我还没说", List.of(), merchant, null, coupon.getId()));
        assertNotNull(question.clarification(), question.text());
        var switched = assistant.chat(admin, true, new Chat("改成领取后15天有效，先预览", List.of(), merchant, null, coupon.getId(), null,
            new Answer(question.clarification().token(), null)));
        assertNotNull(switched.token(), switched.text()); assertNull(switched.clarification());
        var cover = assistant.chat(admin, true, new Chat("取消修改，打开这张券的AI封面编辑", List.of(), merchant, null, coupon.getId(), null, null, switched.token()));
        assertEquals("编辑封面", cover.operation()); assertNotNull(cover.detail());
        assertEquals(30, coupons.selectOneById(coupon.getId()).getValidDays());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PERKLY_ASSISTANT_LIVE_TEST", matches = "true")
    void liveModelCanDraftAnAuthorizedCreativeProposal() {
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:template:add");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
        org.mockito.Mockito.doAnswer(call -> {
            var suggestion = call.callRealMethod(); System.out.println("CREATIVE_DRAFT " + suggestion); return suggestion;
        }).when(assistant).proposeDraft(any(), any());
        var result = assistant.chat(admin, true, new Chat("帮我创建一张优惠券，适用于当前商家所有门店，具体优惠以及详情你来拟定，先出草稿给我确认", List.of(), merchant, null, null));
        assertNotNull(result.draft(), result.text()); assertNotNull(result.draft().getName()); assertNotNull(result.draft().getType());
        assertNotNull(result.draft().getSalePrice()); assertNull(result.draft().getId());
    }

    private CouponTemplateEntity publishedRecoveryCoupon() {
        var coupon = fixture("已上架调整方案券"); coupon.setStatus(1); coupons.update(coupon);
        var publication = new com.cool.modules.coupon.entity.CouponPublicationEntity();
        publication.setMerchantId(merchant); publication.setCouponTemplateId(coupon.getId()); publication.setChannel("PLATFORM");
        publication.setAuditStatus(2); publication.setPublishStatus(1); publication.setSubmittedVersion(1); publication.setPublishedVersion(1);
        publications.insertSelective(publication);
        return coupon;
    }

    private void assertBlocked(Reply reply) {
        assertNull(reply.token()); assertNotNull(reply.clarification()); assertEquals("REPAIR", reply.clarification().type());
    }

    private void authenticateOperator() {
        var operator = principal(admin, UserTypeEnum.ADMIN, "coupon:template:page", "coupon:template:info",
            "coupon:template:update", "coupon:template:delete");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, operator.getAuthorities()));
    }

    private void execute(Long couponId, String operation) {
        var preview = assistant.chat(admin, true, chat(couponId));
        assertEquals(operation, preview.operation());
        assistant.confirm(admin, true, preview.token());
    }

    private Chat chat(Long couponId) { return new Chat("按当前目标操作", List.of(), merchant, null, couponId); }

    private void intent(String action, Fields fields) {
        doReturn(new Intent(action, null, null, null, null, null, fields)).when(assistant).interpret(any());
    }

    private CouponTemplateEntity fixture(String name) {
        var coupon = new CouponTemplateEntity();
        coupon.setMerchantId(merchant);
        coupon.setName(name);
        coupon.setType("CASH");
        coupon.setSalePrice(BigDecimal.ZERO);
        coupon.setDeductAmount(BigDecimal.TEN);
        coupon.setScopeType(0);
        coupon.setValidityType("AFTER_ISSUE");
        coupon.setValidDays(30);
        coupon.setIssuedCount(0L);
        coupon.setRuleVersion(1);
        coupon.setStatus(0);
        coupons.insertSelective(coupon);
        return coupon;
    }
}
