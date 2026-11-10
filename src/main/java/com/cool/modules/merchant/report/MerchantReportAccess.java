package com.cool.modules.merchant.report;

import com.cool.core.exception.CoolException;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 为门店只读助手计算可信账号门店及人员数据范围
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:18:58
 */
@Service @RequiredArgsConstructor
public class MerchantReportAccess {
    private final WorkspaceAuthorization authorization;
    public record Scope(Long actorId, Long merchantId, Long storeId, Long operatorId) {}
    public Scope require(Long actor, Long operator) {
        var user = authorization.user(actor);
        if (!"STORE".equals(WorkspaceAuthorization.type(user)))
            throw new CoolException("请使用门店员工账号",403);
        authorization.require(actor,"bill:view");
        authorization.role(user);
        var store = authorization.boundStore(user);
        if (store.getStatus()==null || !Set.of(1,2).contains(store.getStatus()))
            throw new CoolException("门店已停用",403);
        if (authorization.ownRecordsOnly(user)) {
            if (operator!=null && !actor.equals(operator)) throw new CoolException("仅可查询本人记录",403);
            operator=actor;
        }
        // 人员条件只进一步收窄本店查询，不提供跨店人员详情。
        return new Scope(actor,user.getMerchantId(),store.getId(),operator);
    }
}
