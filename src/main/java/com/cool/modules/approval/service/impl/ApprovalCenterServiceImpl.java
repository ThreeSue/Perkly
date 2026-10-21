package com.cool.modules.approval.service.impl;
import com.cool.modules.approval.service.ApprovalCenterService;
import com.cool.modules.approval.service.ApprovalHistoryService;

import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.approval.entity.*;
import com.cool.modules.approval.mapper.*;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.coupon.service.CouponPublicationService;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.service.MerchantInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import com.cool.modules.approval.entity.resp.*;
import com.cool.modules.coupon.entity.CouponPublicationEntity;
import com.cool.modules.coupon.mapper.CouponPublicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以实体getter查询审批列表详情并原子处理入驻与券发布审核
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class ApprovalCenterServiceImpl implements ApprovalCenterService {
    private final MerchantApplicationMapper applications;
    private final ApprovalMapper history;
    private final CouponPublicationMapper publications;
    private final CouponTemplateMapper coupons;
    private final MerchantInfoMapper merchants;
    private final MerchantInfoService merchantService;
    private final BaseSysUserMapper accounts;
    private final CouponPublicationService couponService;
    private final ApprovalHistoryService records;


    @Transactional(readOnly=true)
    @Override
    public ApprovalPageResp page(String type,Integer status,String keyword,int page,int size) {
        checkType(type);
        if (page<1 || size<1 || size>100 || page>100000) throw new CoolException("分页参数不正确");
        if (status!=null && !Set.of(0,1,2,3).contains(status)) throw new CoolException("审批状态不正确");
        String word=keyword==null ? "" : keyword.trim();
        if (word.length()>100) throw new CoolException("搜索内容不能超过100字");
        List<ApprovalItemResp> items=new ArrayList<>(); long total;
        if ("MERCHANT".equals(type)) {
            QueryWrapper q=QueryWrapper.create().eq(MerchantApplicationEntity::getCurrentFlag,1)
                    .eq(MerchantApplicationEntity::getStatus,status,status!=null)
                    .like(MerchantApplicationEntity::getMerchantName,word,!word.isEmpty());
            total=applications.selectCountByQuery(q);
            applications.selectListByQuery(q.orderBy(MerchantApplicationEntity::getSubmittedAt,false).orderBy(MerchantApplicationEntity::getId,false).limit((page-1)*size,size))
                    .forEach(a -> items.add(merchantItem(a)));
        } else {
            QueryWrapper q=QueryWrapper.create().eq(CouponPublicationEntity::getChannel,"PLATFORM")
                    .isNotNull(CouponPublicationEntity::getSubmittedAt)
                    .eq(CouponPublicationEntity::getAuditStatus,status,status!=null);
            if (!word.isEmpty()) {
                List<Long> ids=coupons.selectListByQuery(QueryWrapper.create().like(CouponTemplateEntity::getName,word))
                        .stream().map(CouponTemplateEntity::getId).collect(Collectors.toCollection(ArrayList::new));
                List<Long> merchantIds=merchants.selectListByQuery(QueryWrapper.create().like(MerchantInfoEntity::getName,word))
                        .stream().map(MerchantInfoEntity::getId).toList();
                if (!merchantIds.isEmpty()) ids.addAll(coupons.selectListByQuery(QueryWrapper.create().in(CouponTemplateEntity::getMerchantId,merchantIds)).stream().map(CouponTemplateEntity::getId).toList());
                if (ids.isEmpty()) return pageResult(items,page,size,0);
                q.in(CouponPublicationEntity::getCouponTemplateId,ids);
            }
            total=publications.selectCountByQuery(q);
            List<CouponPublicationEntity> rows=publications.selectListByQuery(q.orderBy(CouponPublicationEntity::getSubmittedAt,false).orderBy(CouponPublicationEntity::getId,false).limit((page-1)*size,size));
            if (!rows.isEmpty()) {
                Map<Long,CouponTemplateEntity> templateMap=coupons.selectListByIds(rows.stream().map(CouponPublicationEntity::getCouponTemplateId).toList()).stream().collect(Collectors.toMap(CouponTemplateEntity::getId,e->e));
                Map<Long,MerchantInfoEntity> merchantMap=merchants.selectListByIds(rows.stream().map(CouponPublicationEntity::getMerchantId).toList()).stream().collect(Collectors.toMap(MerchantInfoEntity::getId,e->e));
                Map<Long,AuditRecordEntity> submitters=new HashMap<>();
                history.selectListByQuery(QueryWrapper.create().eq(AuditRecordEntity::getBusinessType,"COUPON_PLATFORM").eq(AuditRecordEntity::getAction,"SUBMIT").in(AuditRecordEntity::getBusinessId,rows.stream().map(CouponPublicationEntity::getId).toList()).orderBy(AuditRecordEntity::getId,true))
                        .forEach(r->submitters.put(r.getBusinessId(),r));
                for (var p:rows) items.add(couponItem(p,templateMap.get(p.getCouponTemplateId()),merchantMap.get(p.getMerchantId()),submitters.get(p.getId())));
            }
        }
        return pageResult(items,page,size,total);
    }

    @Transactional(readOnly=true)
    @Override
    public ApprovalDetailResp info(String type,Long id) {
        checkType(type); positive(id);
        ApprovalItemResp item; String snapshot; List<AuditRecordEntity> events;
        if ("MERCHANT".equals(type)) {
            var a=applications.selectOneById(id);
            if (a==null) throw new CoolException("申请不存在");
            item=merchantItem(a); snapshot=a.getSnapshot();
            List<Long> ids=applications.selectListByQuery(QueryWrapper.create().eq(MerchantApplicationEntity::getApplicationNo,a.getApplicationNo())).stream().map(MerchantApplicationEntity::getId).toList();
            events=history.selectListByQuery(QueryWrapper.create().eq(AuditRecordEntity::getBusinessType,"MERCHANT_APPLICATION").in(AuditRecordEntity::getBusinessId,ids).orderBy(AuditRecordEntity::getId,true));
        } else {
            var p=publications.selectOneById(id);
            if (p==null || !"PLATFORM".equals(p.getChannel())) throw new CoolException("审批记录不存在");
            events=history.selectListByQuery(QueryWrapper.create().eq(AuditRecordEntity::getBusinessType,"COUPON_PLATFORM").eq(AuditRecordEntity::getBusinessId,id).orderBy(AuditRecordEntity::getId,true));
            AuditRecordEntity submitter=events.stream().filter(e->"SUBMIT".equals(e.getAction())).reduce((a,b)->b).orElse(null);
            item=couponItem(p,coupons.selectOneById(p.getCouponTemplateId()),merchants.selectOneById(p.getMerchantId()),submitter);
            snapshot=p.getSubmittedSnapshot();
        }
        String next=item.getStatus()==1 ? "运营人员审核" : item.getStatus()==3 ? "申请人修改并重新提交" : item.getStatus()==0 ? "申请人重新提交" : "MERCHANT".equals(type) ? "已开通经营权限" : Integer.valueOf(1).equals(item.getPublishStatus()) ? "已上架" : "等待商家上架";
        ApprovalDetailResp result=new ApprovalDetailResp();
        result.setItem(item);
        result.setSnapshot(snapshot==null ? Map.of() : JSONUtil.parseObj(snapshot));
        result.setHistory(events); result.setNextStep(next); result.setHistorical(events.isEmpty());
        return result;
    }

    @Override
    public void review(String type,Long id,Integer version,Boolean approved,String reason) {
        checkType(type); positive(id);
        if (approved==null || version==null || version<1) throw new CoolException("请选择审批结果和有效版本");
        if (reason!=null) reason=reason.trim();
        if (Boolean.FALSE.equals(approved) && (reason==null || reason.isEmpty())) throw new CoolException("驳回必须填写原因");
        if (reason!=null && reason.length()>500) throw new CoolException("审批意见不能超过500字");
        if ("COUPON".equals(type)) {
            var p=publications.selectOneById(id);
            if (p==null || !"PLATFORM".equals(p.getChannel())) throw new CoolException("审批记录不存在");
            couponService.audit(p.getCouponTemplateId(),version,approved,reason); return;
        }
        var a=applications.selectOneByQuery(QueryWrapper.create().eq(MerchantApplicationEntity::getId,id).forUpdate());
        if (a==null || a.getCurrentFlag()!=1 || a.getStatus()!=1 || !version.equals(a.getSubmissionNo())) throw new CoolException("申请已处理或版本已变化，请刷新");
        if (approved) {
            if (accounts.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,a.getUsername()))>0) throw new CoolException("申请账号已被使用，不能覆盖已有账号");
            var input=JSONUtil.parseObj(a.getSnapshot());
            MerchantInfoEntity merchant=new MerchantInfoEntity();
            merchant.setName(a.getMerchantName()); merchant.setCategoryId(input.getLong("categoryId"));
            merchant.setContactName(input.getStr("contactName")); merchant.setContactPhone(input.getStr("contactPhone"));
            merchant.setAddress(input.getStr("address")); merchant.setIntroduction(input.getStr("introduction")); merchant.setStatus(1);
            Long merchantId=merchantService.add(merchant);
            BaseSysUserEntity account=new BaseSysUserEntity();
            account.setUsername(a.getUsername()); account.setName(merchant.getContactName()); account.setNickName(merchant.getContactName());
            account.setPassword(a.getPasswordHash()); account.setPasswordV(1); account.setStatus(1); account.setMerchantId(merchantId); account.setMerchantRole("OWNER");
            accounts.insertSelective(account); a.setMerchantId(merchantId);
        }
        a.setStatus(approved ? 2 : 3); applications.update(a);
        records.append("MERCHANT_APPLICATION",a.getId(),a.getMerchantId(),a.getSubmissionNo(),a.getSubmissionNo(),approved ? "APPROVE" : "REJECT",reason,null,null);
    }

    private ApprovalItemResp merchantItem(MerchantApplicationEntity a) {
        ApprovalItemResp item=new ApprovalItemResp(); item.setId(a.getId()); item.setTitle(a.getMerchantName()); item.setMerchantName(a.getMerchantName());
        item.setSubmittedBy(a.getSubmittedBy()); item.setSubmittedAt(a.getSubmittedAt()); item.setVersion(a.getSubmissionNo()); item.setStatus(a.getStatus()); return item;
    }
    private ApprovalItemResp couponItem(CouponPublicationEntity p,CouponTemplateEntity c,MerchantInfoEntity m,AuditRecordEntity submitter) {
        ApprovalItemResp item=new ApprovalItemResp(); item.setId(p.getId()); item.setTemplateId(p.getCouponTemplateId()); item.setTitle(c==null ? "券资料已失效" : c.getName());
        item.setMerchantName(m==null ? "商家资料已失效" : m.getName()); item.setStatus(p.getAuditStatus()); item.setVersion(p.getSubmittedVersion());
        item.setSubmittedAt(p.getSubmittedAt()); item.setPublishStatus(p.getPublishStatus()); item.setSubmittedBy(submitter==null ? "历史未记录" : submitter.getActorName()); return item;
    }
    private ApprovalPageResp pageResult(List<ApprovalItemResp> items,int page,int size,long total) {
        ApprovalPaginationResp pagination=new ApprovalPaginationResp();
        pagination.setPage(page); pagination.setSize(size); pagination.setTotal(total);
        ApprovalPageResp result=new ApprovalPageResp(); result.setList(items); result.setPagination(pagination);
        return result;
    }
    private void checkType(String type) { if (!Set.of("MERCHANT","COUPON").contains(type==null ? "" : type)) throw new CoolException("审批类型不正确"); }
    private void positive(Long id) { if (id==null || id<1) throw new CoolException("请选择有效审批记录"); }
}
