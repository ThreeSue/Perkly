package com.cool.modules.approval.service.impl;
import com.cool.modules.approval.service.MerchantRegistrationService;
import com.cool.modules.approval.service.ApprovalHistoryService;

import cn.hutool.json.JSONUtil;
import com.cool.modules.approval.entity.resp.MerchantRegistrationResp;
import com.cool.core.exception.CoolException;
import com.cool.modules.approval.entity.req.MerchantRegistrationReq;
import com.cool.modules.approval.entity.MerchantApplicationEntity;
import com.cool.modules.approval.mapper.MerchantApplicationMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.MerchantCategoryMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提交商家入驻申请并以密码验证驳回后的重提身份
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class MerchantRegistrationServiceImpl implements MerchantRegistrationService {
    private final MerchantApplicationMapper applications;
    private final BaseSysUserMapper accounts;
    private final MerchantCategoryMapper categories;
    private final ApprovalHistoryService history;
    private final com.cool.modules.approval.mapper.MerchantActivationMapper activations;
    private final jakarta.validation.Validator validator;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();

    @Override
    public MerchantRegistrationResp submit(MerchantRegistrationReq input, boolean resubmit) {
        var violations=validator.validate(input);
        if (!violations.isEmpty()) throw new CoolException("申请资料不完整或格式不正确："+violations.iterator().next().getPropertyPath());
        long phoneDigits = input.getContactPhone().chars().filter(Character::isDigit).count();
        if (phoneDigits < 7 || phoneDigits > 20) throw new CoolException("联系电话需包含7至20位数字");
        if ("admin".equalsIgnoreCase(input.getUsername()) || input.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new CoolException("账号或密码格式不正确");
        var category=categories.selectOneById(input.getCategoryId());
        if (category==null || !Integer.valueOf(1).equals(category.getStatus())) throw new CoolException("请选择启用的商家分类");
        if (accounts.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,input.getUsername()))>0) throw new CoolException("账号已被使用，请使用新账号申请");
        MerchantApplicationEntity old=applications.selectOneByQuery(QueryWrapper.create()
                .eq(MerchantApplicationEntity::getUsername,input.getUsername()).eq(MerchantApplicationEntity::getCurrentFlag,1).forUpdate());
        if (resubmit) {
            if (old==null || !passwords.matches(input.getPassword(),old.getPasswordHash())) throw new CoolException("申请账号或密码不正确");
            if (activations.selectCountByQuery(QueryWrapper.create()
                    .eq(com.cool.modules.approval.entity.MerchantActivationEntity::getApplicationNo,old.getApplicationNo())) > 0)
                throw new CoolException("此申请请验证联系人手机号后通过小程序修改重提");
            if (!Integer.valueOf(3).equals(old.getStatus())) throw new CoolException("只有已驳回的申请可以重提");
        } else if (old!=null) throw new CoolException("该账号已有入驻申请；驳回后请使用重提接口");
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("name",input.getMerchantName().trim()); snapshot.put("categoryId",input.getCategoryId());
        snapshot.put("categoryName",category.getName()); snapshot.put("contactName",input.getContactName().trim());
        snapshot.put("contactPhone",input.getContactPhone().trim()); snapshot.put("address",input.getAddress());
        snapshot.put("licenseUrl",input.getLicenseUrl()); snapshot.put("introduction",input.getIntroduction());
        MerchantApplicationEntity application=new MerchantApplicationEntity();
        application.setApplicationNo(old==null ? UUID.randomUUID().toString() : old.getApplicationNo());
        application.setSubmissionNo(old==null ? 1 : old.getSubmissionNo()+1);
        application.setUsername(input.getUsername()); application.setSubmittedBy(input.getContactName().trim());
        application.setPasswordHash(old==null ? passwords.encode(input.getPassword()) : old.getPasswordHash());
        application.setMerchantName(input.getMerchantName().trim()); application.setSnapshot(JSONUtil.toJsonStr(snapshot));
        application.setStatus(1); application.setCurrentFlag(1); application.setSubmittedAt(new Date()); application.setCreateTime(new Date());
        if (old!=null) { old.setCurrentFlag(0); applications.update(old); }
        try { applications.insertSelective(application); }
        catch (org.springframework.dao.DuplicateKeyException duplicate) { throw new CoolException("申请已存在，请勿重复提交"); }
        history.append("MERCHANT_APPLICATION",application.getId(),null,application.getSubmissionNo(),application.getSubmissionNo(),"SUBMIT",null,application.getSnapshot(),input.getContactName().trim());
        MerchantRegistrationResp result=new MerchantRegistrationResp();
        result.setApplicationNo(application.getApplicationNo()); result.setSubmissionNo(application.getSubmissionNo()); result.setStatus("待审核");
        return result;
    }
}
