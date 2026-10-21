package com.cool.modules.coupon.service.impl;
import com.cool.modules.coupon.service.CouponPublicationService;
import com.cool.modules.coupon.service.CouponTemplateService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper;
import com.mybatisflex.core.row.Row;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在同一事务内流转平台优惠券审核和上下架并保存版本快照
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 16:45:38
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CouponPublicationServiceImpl implements CouponPublicationService {
    private final CouponTemplateService templates;
    private final CouponTemplateRelationsMapper relations;
    private final com.cool.modules.approval.service.ApprovalHistoryService history;

    @Override
    public Object submitAudit(Long id, Integer version) {
        CouponTemplateEntity e = templates.validateForPublication(id, version);
        Row state = state(id);
        if (is(state, "publish_status", 1) || is(state, "audit_status", 1)) fail("已上架或正在审核，不能重复提交");
        relations.savePublication(e.getMerchantId(), id, Row.of("audit_status", 1)
                .set("submitted_version", version).set("submitted_snapshot", snapshot(e)).setRaw("submitted_at", "NOW()"));
        history.record("COUPON_PLATFORM", ((Number)state(id).get("id")).longValue(),e.getMerchantId(),version,"SUBMIT",null,snapshot(e));
        return templates.info(id);
    }

    @Override
    public Object audit(Long id, Integer version, Boolean approved) {
        return audit(id,version,approved,Boolean.FALSE.equals(approved) ? "历史接口未填写驳回原因" : null);
    }

    @Override
    public Object audit(Long id, Integer version, Boolean approved, String reason) {
        if (approved == null) fail("请选择审核通过或驳回");
        if (Boolean.FALSE.equals(approved) && (reason==null || reason.isBlank())) fail("请填写驳回原因");
        if (reason!=null && reason.length()>500) fail("审批意见不能超过500字");
        CouponTemplateEntity e = locked(id, version);
        Row state = state(id);
        if (!is(state, "audit_status", 1)) fail("只有待审核的优惠券可以审核");
        if (!is(state, "submitted_version", version)) fail("提交版本已失效，请重新提交审核");
        if (approved) templates.validateForPublication(id, version);
        relations.savePublication(e.getMerchantId(), id, Row.of("audit_status", approved ? 2 : 3));
        history.record("COUPON_PLATFORM",((Number)state.get("id")).longValue(),e.getMerchantId(),version,approved ? "APPROVE" : "REJECT",reason,null);
        return templates.info(id);
    }

    @Override
    public Object withdrawAudit(Long id, Integer version) {
        CouponTemplateEntity e = locked(id, version);
        if (!is(state(id), "audit_status", 1)) fail("只有待审核的优惠券可以撤回");
        relations.savePublication(e.getMerchantId(), id, Row.of("audit_status", 0));
        history.record("COUPON_PLATFORM",((Number)state(id).get("id")).longValue(),e.getMerchantId(),version,"WITHDRAW",null,null);
        return templates.info(id);
    }

    @Override
    public Object publish(Long id, Integer version) {
        CouponTemplateEntity e = templates.validateForPublication(id, version);
        Row state = state(id);
        if (!is(state, "audit_status", 2) || !is(state, "submitted_version", version)) fail("当前版本尚未通过平台审核，请重新提交审核");
        if (is(state, "publish_status", 1)) fail("此券已经上架");
        relations.savePublication(e.getMerchantId(), id, Row.of("publish_status", 1)
                .set("published_version", version).set("published_snapshot", state.get("submitted_snapshot"))
                .setRaw("published_at", "NOW()"));
        history.record("COUPON_PLATFORM",((Number)state.get("id")).longValue(),e.getMerchantId(),version,"PUBLISH",null,null);
        return templates.info(id);
    }

    @Override
    public Object unpublish(Long id, Integer version) {
        CouponTemplateEntity e = locked(id, version);
        if (!is(state(id), "publish_status", 1)) fail("只有已上架的优惠券可以下架");
        relations.savePublication(e.getMerchantId(), id, Row.of("publish_status", 2));
        history.record("COUPON_PLATFORM",((Number)state(id).get("id")).longValue(),e.getMerchantId(),version,"UNPUBLISH",null,null);
        return templates.info(id);
    }

    private CouponTemplateEntity locked(Long id, Integer version) {
        if (id == null || id <= 0 || relations.lockTemplate(id) == null) throw new CoolException("优惠券不存在");
        CouponTemplateEntity e = templates.getById(id);
        if (!Objects.equals(e.getRuleVersion(), version) || version == null) fail("券规则版本已变化，请刷新后重新操作");
        return e;
    }

    private Row state(Long id) {
        return relations.publicationState(id);
    }

    private boolean is(Row row, String key, int value) {
        return row.get(key) instanceof Number n && n.intValue() == value;
    }

    private String snapshot(CouponTemplateEntity e) {
        Map<String, Object> values = BeanUtil.beanToMap(e);
        for (String key : List.of("publication", "queryWrapper", "createTime", "updateTime", "issuedCount")) values.remove(key);
        return JSONUtil.toJsonStr(values);
    }

    private static void fail(String message) { throw new CoolException(message); }
}
