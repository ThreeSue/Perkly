package com.cool.modules.approval.service.impl;
import com.cool.modules.approval.service.ApprovalHistoryService;

import com.cool.core.util.CoolSecurityUtil;
import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.approval.mapper.ApprovalMapper;
import com.cool.modules.approval.entity.AuditRecordEntity;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在业务事务内记录审批操作人轮次意见和提交快照
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:06:21
 */
@Service
@RequiredArgsConstructor
public class ApprovalHistoryServiceImpl implements ApprovalHistoryService {
    private final ApprovalMapper mapper;

    @Transactional(propagation=Propagation.MANDATORY)
    @Override
    public void record(String type, Long id, Long merchantId, int version, String action, String reason, String snapshot) {
        AuditRecordEntity last=mapper.selectOneByQuery(QueryWrapper.create().eq(AuditRecordEntity::getBusinessType,type)
                .eq(AuditRecordEntity::getBusinessId,id).orderBy(AuditRecordEntity::getId,false).limit(1));
        int round=last==null ? 0 : last.getSubmissionNo();
        if ("SUBMIT".equals(action)) round++;
        append(type,id,merchantId,Math.max(round,1),version,action,reason,snapshot,null);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    @Override
    public void append(String type, Long id, Long merchantId, int round, int version, String action, String reason, String snapshot, String applicant) {
        UserTypeEnum actorType=CoolSecurityUtil.getCurrentUserType();
        var auth=SecurityContextHolder.getContext().getAuthentication();
        String name=applicant!=null ? applicant : actorType==UserTypeEnum.UNKNOWN || auth==null ? "系统操作" : auth.getName();
        AuditRecordEntity row=new AuditRecordEntity();
        row.setBusinessType(type); row.setBusinessId(id); row.setMerchantId(merchantId);
        row.setSubmissionNo(round); row.setVersion(version); row.setAction(action);
        row.setReviewerId(applicant==null && auth!=null && actorType!=UserTypeEnum.UNKNOWN
                ? CoolSecurityUtil.getCurrentUserId() : null);
        row.setActorName(name); row.setActorType(applicant!=null ? "APPLICANT" : actorType==UserTypeEnum.UNKNOWN ? "SYSTEM" : actorType.name());
        row.setReason(reason); row.setSnapshot(snapshot); row.setReviewedAt(new java.util.Date());
        mapper.insertSelective(row);
    }
}
