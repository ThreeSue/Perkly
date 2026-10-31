package com.cool.modules.approval.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.cool.core.cache.CoolCache;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.modules.approval.entity.*;
import com.cool.modules.approval.entity.req.GuestMerchantOnboardingParam.*;
import com.cool.modules.approval.entity.resp.MerchantOnboardingResp;
import com.cool.modules.approval.mapper.*;
import com.cool.modules.approval.service.*;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantCategoryMapper;
import com.cool.modules.merchant.service.MerchantInfoService;
import com.cool.modules.user.util.UserSmsUtil;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以独立手机号凭证处理游客申请审批发码和一次性商家激活
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 11:04:31
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class GuestMerchantOnboardingServiceImpl implements GuestMerchantOnboardingService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.workspace.service.WorkspaceLegacyAccountBridge workspaceBridge;

    private final MerchantActivationMapper activations;
    private final MerchantApplicationMapper applications;
    private final MerchantCategoryMapper categories;
    private final ApprovalMapper audits;
    private final ApprovalHistoryService history;
    private final BaseSysUserMapper accounts;
    private final MerchantInfoService merchants;
    private final UserSmsUtil sms;
    private final CoolCache cache;
    private final JwtTokenUtil jwt;
    private final jakarta.validation.Validator validator;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_MS = 30*60_000L, CODE_MS = 7*24*3600_000L;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    @Override public String verify(String phone, String smsCode) {
        UserSmsUtil.validatePhone(phone);
        sms.checkVerifyCode(phone,smsCode,UserSmsUtil.SendSceneEnum.ALL);
        String token = random(32);
        cache.set("onboarding:"+SecureUtil.sha256(token),Dict.create().set("phone",phone)
                .set("expiresAt",System.currentTimeMillis()+SESSION_MS),1800);
        return token;
    }
    private String phone(String token) {
        require(token != null && token.matches("[a-f0-9]{64}"),"请先验证联系人手机号");
        Dict session=cache.get("onboarding:"+SecureUtil.sha256(token),Dict.class);
        require(session != null && session.getLong("expiresAt")!=null && session.getLong("expiresAt")>System.currentTimeMillis(),"手机号验证已失效，请重新验证");
        return session.getStr("phone");
    }
    @Override public MerchantOnboardingResp submit(Submit input, boolean resubmit) {
        validate(input); String phone=phone(input.onboardingToken());
        var category=categories.selectOneById(input.categoryId());
        require(category != null && Integer.valueOf(1).equals(category.getStatus()),"请选择启用的商家分类");
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("name",input.merchantName().trim()); data.put("categoryId",input.categoryId());
        data.put("categoryName",category.getName()); data.put("contactName",input.contactName().trim());
        data.put("contactPhone",phone);
        data.put("introduction",input.introduction());
        data.put("licenseNumber",input.licenseNumber()==null?null:input.licenseNumber().toUpperCase(java.util.Locale.ROOT));
        if (!resubmit) {
            var existing=activations.selectOneByQuery(QueryWrapper.create().eq(MerchantActivationEntity::getPhone,phone)
                    .eq(MerchantActivationEntity::getRequestId,input.requestId()).forUpdate());
            if (existing != null) {
                var current=current(existing.getApplicationNo(),false);
                var snapshot=JSONUtil.parseObj(current.getSnapshot());
                for (String key:List.of("name","categoryId","contactName","introduction","licenseNumber"))
                    require(Objects.equals(String.valueOf(data.get(key)),String.valueOf(snapshot.get(key))),"同一requestId不能用于不同申请资料");
                return response(current,existing,true);
            }
        }
        MerchantActivationEntity credential;
        MerchantApplicationEntity old=null;
        if (resubmit) {
            old=current(input.applicationNo(),true);
            credential=owned(input.applicationNo(),phone,true);
            require(old.getStatus()==3 && credential.getActivatedAt()==null,"只有已驳回的申请可以修改重提");
            old.setCurrentFlag(0);applications.update(old);
        } else {
            credential=new MerchantActivationEntity();credential.setApplicationNo(UUID.randomUUID().toString());
            credential.setPhone(phone);credential.setRequestId(input.requestId());credential.setCreateTime(new Date());
            try { activations.insertSelective(credential); }
            catch (org.springframework.dao.DuplicateKeyException conflict) {throw new CoolException("申请请求处理中，请使用原requestId重试");}
        }
        var application=new MerchantApplicationEntity();
        application.setApplicationNo(credential.getApplicationNo());application.setSubmissionNo(old==null ? 1 : old.getSubmissionNo()+1);
        // 占位标识不是用户或商家账号；不创建user_info，也不提前创建base_sys_user。
        application.setUsername(old==null ? "guest_"+random(16) : old.getUsername());
        application.setPasswordHash(old==null ? passwords.encode(random(32)) : old.getPasswordHash());
        application.setMerchantName(input.merchantName().trim());application.setSnapshot(JSONUtil.toJsonStr(data));
        application.setStatus(1);application.setCurrentFlag(1);application.setSubmittedBy(input.contactName().trim());
        application.setSubmittedAt(new Date());application.setCreateTime(new Date());applications.insertSelective(application);
        history.append("MERCHANT_APPLICATION",application.getId(),null,application.getSubmissionNo(),application.getSubmissionNo(),"SUBMIT",null,application.getSnapshot(),application.getSubmittedBy());
        return response(application,credential,true);
    }
    @Override @Transactional(readOnly=true) public List<MerchantOnboardingResp> list(String token) {
        String phone=phone(token);
        return activations.selectListByQuery(QueryWrapper.create().eq(MerchantActivationEntity::getPhone,phone)
                .orderBy(MerchantActivationEntity::getId,false).limit(100)).stream()
                .map(c->response(current(c.getApplicationNo(),false),c,false)).toList();
    }
    @Override @Transactional(readOnly=true) public MerchantOnboardingResp info(String token,String applicationNo) {
        String phone=phone(token);
        var c=owned(applicationNo,phone,false);return response(current(applicationNo,false),c,true);
    }
    @Override public void issue(MerchantApplicationEntity application) {
        require(application.getMerchantId()==null,"已开通商家无需再次发码");
        var c=activations.selectOneByQuery(QueryWrapper.create().eq(MerchantActivationEntity::getApplicationNo,application.getApplicationNo()).forUpdate());
        if (c==null) {
            c=new MerchantActivationEntity();c.setApplicationNo(application.getApplicationNo());
            c.setPhone(JSONUtil.parseObj(application.getSnapshot()).getStr("contactPhone"));
            UserSmsUtil.validatePhone(c.getPhone());c.setRequestId("legacy_"+random(16));c.setCreateTime(new Date());
            activations.insertSelective(c);
        }
        setCode(c);
    }
    private void setCode(MerchantActivationEntity credential) {
        String code="MK-"+random(16).toUpperCase(Locale.ROOT);
        credential.setCodeHash(SecureUtil.sha256(code));credential.setCodeCipher(encrypt(code));
        credential.setExpiresAt(new Date(System.currentTimeMillis()+CODE_MS));activations.update(credential);
    }
    @Override public MerchantOnboardingResp renew(String token,String applicationNo) {
        String phone=phone(token);var a=current(applicationNo,true);var c=owned(applicationNo,phone,true);
        require(a.getStatus()==2 && a.getMerchantId()==null && c.getActivatedAt()==null,"只有审核通过且尚未开通的申请可以重新发码");
        setCode(c);history.append("MERCHANT_APPLICATION",a.getId(),null,a.getSubmissionNo(),a.getSubmissionNo(),"REISSUE",null,null,a.getSubmittedBy());
        return response(a,c,true);
    }
    @Override public MerchantOnboardingResp activate(Activate input) {
        validate(input);String phone=phone(input.onboardingToken());
        require(input.password().getBytes(StandardCharsets.UTF_8).length<=72 && !input.password().matches(".*\\s.*"),"商家密码不可包含空白且不能超过72字节");
        String code=input.code().trim().toUpperCase(Locale.ROOT);
        require(code.matches("MK-[A-F0-9]{32}"),"营业码格式不正确");
        // 先读归属定位申请，再始终按申请→开通记录顺序加锁，与审核/重发一致。
        var found=activations.selectOneByQuery(QueryWrapper.create().eq(MerchantActivationEntity::getPhone,phone)
                .eq(MerchantActivationEntity::getCodeHash,SecureUtil.sha256(code)));
        require(found!=null,"营业码无效或与当前验证手机号不匹配");
        var a=current(found.getApplicationNo(),true);var c=owned(found.getApplicationNo(),phone,true);
        require(Objects.equals(c.getCodeHash(),SecureUtil.sha256(code)),"营业码已更新，请使用最新营业码");
        require(c.getActivatedAt()==null && a.getMerchantId()==null,"该申请已开通，请直接使用商家账号登录");
        require(a.getStatus()==2,"申请尚未审核通过");
        require(c.getExpiresAt()!=null && c.getExpiresAt().after(new Date()),"营业码已过期，请验证手机号后重新获取");
        require(!"admin".equalsIgnoreCase(input.username()),"不能使用此商家账号");
        require(accounts.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,input.username()))==0,"商家账号已被使用");
        require(applications.selectCountByQuery(QueryWrapper.create().eq(MerchantApplicationEntity::getUsername,input.username())
                .ne(MerchantApplicationEntity::getApplicationNo,a.getApplicationNo()).eq(MerchantApplicationEntity::getCurrentFlag,1))==0,"该账号已有其他入驻申请");
        var snapshot=JSONUtil.parseObj(a.getSnapshot());
        var merchant=new MerchantInfoEntity();merchant.setName(a.getMerchantName());merchant.setCategoryId(snapshot.getLong("categoryId"));
        // 地址与坐标由OWNER后续创建门店时填写，不从旧申请快照迁入商家资料。
        merchant.setContactName(snapshot.getStr("contactName"));merchant.setContactPhone(phone);
        merchant.setLicenseNumber(snapshot.getStr("licenseNumber"));
        merchant.setIntroduction(snapshot.getStr("introduction"));merchant.setStatus(1);Long merchantId=merchants.add(merchant);
        var account=new BaseSysUserEntity();account.setUsername(input.username());account.setName(merchant.getContactName());
        account.setNickName(merchant.getContactName());account.setPassword(passwords.encode(input.password()));account.setPasswordV(1);
        account.setStatus(1);account.setMerchantId(merchantId);account.setMerchantRole("OWNER");
        try {accounts.insertSelective(account);} catch(org.springframework.dao.DuplicateKeyException conflict) {throw new CoolException("商家账号已被使用");}
        workspaceBridge.bind(account,"OWNER",false);
        a.setMerchantId(merchantId);applications.update(a);
        c.setAccountId(account.getId());c.setActivatedAt(new Date());c.setCodeCipher(null);activations.update(c,false);
        history.append("MERCHANT_APPLICATION",a.getId(),merchantId,a.getSubmissionNo(),a.getSubmissionNo(),"ACTIVATE",null,null,a.getSubmittedBy());
        return response(a,c,true);
    }
    private MerchantApplicationEntity current(String applicationNo, boolean lock) {
        require(applicationNo!=null && applicationNo.length()<=40,"请提供申请单号");
        var query=QueryWrapper.create().eq(MerchantApplicationEntity::getApplicationNo,applicationNo).eq(MerchantApplicationEntity::getCurrentFlag,1);
        if(lock)query.forUpdate();var a=applications.selectOneByQuery(query);require(a!=null,"申请不存在或无权访问");return a;
    }
    private MerchantActivationEntity owned(String applicationNo,String phone,boolean lock) {
        var query=QueryWrapper.create().eq(MerchantActivationEntity::getApplicationNo,applicationNo).eq(MerchantActivationEntity::getPhone,phone);
        if(lock)query.forUpdate();var c=activations.selectOneByQuery(query);require(c!=null,"申请不存在或无权访问");return c;
    }
    private MerchantOnboardingResp response(MerchantApplicationEntity a,MerchantActivationEntity c,boolean detail) {
        var r=new MerchantOnboardingResp();r.setApplicationNo(a.getApplicationNo());r.setSubmissionNo(a.getSubmissionNo());
        r.setMerchantName(a.getMerchantName());r.setReviewStatus(a.getStatus());r.setMerchantId(a.getMerchantId());r.setAccountId(c.getAccountId());
        r.setActivatedAt(c.getActivatedAt());r.setCodeExpiresAt(c.getExpiresAt());
        String state=a.getMerchantId()!=null ? "ACTIVATED" : a.getStatus()==1 ? "PENDING" : a.getStatus()==3 ? "REJECTED" :
                c.getExpiresAt()!=null && c.getExpiresAt().after(new Date()) ? "APPROVED" : "CODE_EXPIRED";
        r.setState(state);r.setStateText(Map.of("ACTIVATED","已开通","PENDING","待审核","REJECTED","已驳回","APPROVED","审核通过，待开通","CODE_EXPIRED","营业码已过期").get(state));
        if(detail) {
            r.setSnapshot(JSONUtil.parseObj(a.getSnapshot()));
            if("APPROVED".equals(state) && c.getCodeCipher()!=null)r.setActivationCode(decrypt(c.getCodeCipher()));
            if(a.getStatus()==3) {
                var audit=audits.selectOneByQuery(QueryWrapper.create().eq(AuditRecordEntity::getBusinessType,"MERCHANT_APPLICATION")
                        .eq(AuditRecordEntity::getBusinessId,a.getId()).eq(AuditRecordEntity::getAction,"REJECT").orderBy(AuditRecordEntity::getId,false).limit(1));
                r.setReason(audit==null ? "" : audit.getReason());
            }
        }
        return r;
    }
    private String encrypt(String code) {return crypt(code,true);}
    private String decrypt(String cipher) {return crypt(cipher,false);}
    private String crypt(String value,boolean encrypt) {
        try {
            byte[] key=SecureUtil.sha256().digest("merchant-activation:"+jwt.getTokenSecret());
            byte[] bytes=encrypt ? new byte[12] : Base64.getDecoder().decode(value);
            if(encrypt)RANDOM.nextBytes(bytes);
            byte[] iv=Arrays.copyOf(bytes,12);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
            byte[] result=cipher.doFinal(encrypt ? value.getBytes(StandardCharsets.UTF_8) : Arrays.copyOfRange(bytes,12,bytes.length));
            if(!encrypt)return new String(result,StandardCharsets.UTF_8);
            byte[] output=Arrays.copyOf(iv,iv.length+result.length);System.arraycopy(result,0,output,iv.length,result.length);return Base64.getEncoder().encodeToString(output);
        } catch(Exception failure) {throw new CoolException("开通凭证处理失败，请重新获取营业码");}
    }
    private void validate(Object input) {require(input!=null && validator.validate(input).isEmpty(),"申请或开通参数不完整或格式不正确");}
    private static void require(boolean condition,String message) {if(!condition)throw new CoolException(message);}
    private static String random(int length) {byte[] bytes=new byte[length];RANDOM.nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
}
