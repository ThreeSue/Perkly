package com.cool.modules.user.service.impl;

import com.cool.core.exception.CoolException;
import com.cool.modules.user.util.UserPasswordUtil;
import com.cool.core.base.BaseServiceImpl;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.service.UserWxService;
import com.cool.modules.user.entity.UserWxEntity;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.lang.Dict;
import com.cool.modules.user.util.UserSmsUtil;
import com.cool.modules.user.util.UserSmsUtil.SendSceneEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl extends BaseServiceImpl<UserInfoMapper, UserInfoEntity> implements
    UserInfoService {

    private final UserSmsUtil userSmsUtil;
    private final UserWxService userWxService;
    @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.searchrisk.SearchRiskService risk;
    @Override @org.springframework.transaction.annotation.Transactional
    public Long add(UserInfoEntity input){
        risk.checkContent(input.getNickName(),"PROFILE","USER_NICKNAME",null);
        Long id=super.add(input);
        risk.recordSavedContent(null,input.getNickName(),"USER_NICKNAME",id);
        risk.bind("PROFILE",id);
        return id;
    }
    @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.searchrisk.mapper.SearchRiskAuditMapper riskAudits;
    @Override @org.springframework.transaction.annotation.Transactional public boolean update(UserInfoEntity input){
        if(input.getBanReason()!=null&&input.getBanReason().length()>200)throw new CoolException("停用原因最多200字");
        var previous=input.getId()==null?null:mapper.selectOneByQuery(QueryWrapper.create()
            .eq(UserInfoEntity::getId,input.getId()).forUpdate());
        risk.checkChangedContent(previous==null?null:previous.getNickName(),input.getNickName(),
            "PROFILE","USER_NICKNAME",input.getId());
        if(Integer.valueOf(0).equals(input.getStatus())&&(input.getBanReason()==null||input.getBanReason().isBlank()))input.setBanReason("平台已停用此账号，请联系平台反馈核查");
        if(Integer.valueOf(1).equals(input.getStatus()))input.setBanReason("");
        boolean changed=super.update(input);
        if(changed&&input.getNickName()!=null)risk.recordSavedContent(previous==null?null:previous.getNickName(),
            input.getNickName(),"USER_NICKNAME",input.getId());
        if(changed&&previous!=null&&input.getStatus()!=null&&!java.util.Objects.equals(previous.getStatus(),input.getStatus())){
            var a=new com.cool.modules.searchrisk.SearchRiskAudit();a.setTargetId(input.getId());a.setOperatorId(com.cool.core.util.CoolSecurityUtil.getCurrentUserId());a.setAction("USER_STATUS");
            a.setDetail(cn.hutool.json.JSONUtil.toJsonStr(java.util.Map.of("subjectType","APP","from",java.util.Objects.toString(previous.getStatus(),"UNKNOWN"),"to",input.getStatus(),"reason",java.util.Objects.toString(input.getBanReason(),""))));riskAudits.insert(a);
        }
        return changed;
    }

    @Override
    public Object accountInfo(Long userId) {
        UserInfoEntity info = getById(userId);
        if (info == null || !Integer.valueOf(1).equals(info.getStatus())) throw new CoolException("账号不可用");
        boolean wxBound = userWxService.count(QueryWrapper.create().eq(UserWxEntity::getUserId, userId)) > 0;
        if (!wxBound && info.getUnionid() != null && !info.getUnionid().isBlank()) {
            wxBound = userWxService.count(QueryWrapper.create().eq(UserWxEntity::getOpenid, info.getUnionid())) > 0
                || userWxService.count(QueryWrapper.create().eq(UserWxEntity::getUnionid, info.getUnionid())) > 0;
        }
        String phone = info.getPhone();
        boolean hasPhone = phone != null && phone.matches("1[3-9][0-9]{9}");
        return Dict.create().set("id", userId).set("nickName", info.getNickName()).set("avatarUrl", info.getAvatarUrl())
            .set("phoneBound", hasPhone).set("phoneMasked", hasPhone ? phone.substring(0, 3) + "****" + phone.substring(7) : null)
            .set("wechatBound", wxBound).set("passwordSet", info.getPassword() != null && !info.getPassword().isBlank());
    }

    @Override
    public UserInfoEntity person(Long userId) {
        UserInfoEntity info = getById(userId);
        info.setPassword(null);
        return info;
    }

    @Override
    public void updatePassword(Long userId, String password, String code) {
        UserInfoEntity info = getById(userId);
        if (info == null || !Integer.valueOf(1).equals(info.getStatus())) {
            throw new CoolException("账号不可用");
        }
        com.cool.core.util.PasswordPolicy.validate(password, info.getPhone());
        String encoded = UserPasswordUtil.encode(password);
        Long usageId=userSmsUtil.checkVerifyCode(info.getPhone(),code,SendSceneEnum.ALL,com.cool.modules.user.sms.SmsScene.CHANGE_PASSWORD);
        info.setPassword(encoded);
        if(info.updateById())userSmsUtil.linkAccount(usageId,userId);
    }

    @Override
    public void logoff(Long userId) {
        UserInfoEntity info = new UserInfoEntity();
        info.setId(userId);
        info.setStatus(2);
        info.setNickName("已注销-00" + userId);
        info.updateById();
    }

    @Override
    public void bindPhone(Long userId, String phone, String code) {
        UserSmsUtil.validatePhone(phone);
        UserInfoEntity current = getById(userId);
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) throw new CoolException("账号不可用");
        if (current.getPhone() != null && !current.getPhone().isBlank()) throw new CoolException("账号已有绑定手机号，不支持在此更换");
        if (count(QueryWrapper.create().eq(UserInfoEntity::getPhone, phone)) > 0) throw new CoolException("该手机号已有账号，请从微信登录入口验证并确认绑定");
        Long usageId=userSmsUtil.checkVerifyCode(phone,code,SendSceneEnum.ALL,com.cool.modules.user.sms.SmsScene.BIND_PHONE);
        UserInfoEntity info = new UserInfoEntity();
        info.setPhone(phone);
        QueryWrapper condition = QueryWrapper.create().eq(UserInfoEntity::getId, userId).eq(UserInfoEntity::getStatus, 1);
        if (current.getPhone() == null) condition.isNull(UserInfoEntity::getPhone);
        else condition.eq(UserInfoEntity::getPhone, current.getPhone());
        try {
            if (!update(info, condition)) {
                throw new CoolException("账号状态已变更，请重新验证");
            }
            userSmsUtil.linkAccount(usageId,userId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new CoolException("该手机号已被绑定，请使用原账号登录");
        }
    }
}
