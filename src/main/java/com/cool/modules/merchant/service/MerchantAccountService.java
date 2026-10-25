package com.cool.modules.merchant.service;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.base.dto.sys.BaseSysLoginDto;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 复用管理账号提供商家身份登录及可撤销会话并实时校验商家状态
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 20:52:23
 */
@Service
@RequiredArgsConstructor
public class MerchantAccountService {
    private final BaseSysUserMapper users;
    private final MerchantInfoMapper merchants;
    private final com.cool.modules.approval.mapper.MerchantApplicationMapper applications;
    private final com.cool.modules.approval.mapper.ApprovalMapper auditRecords;
    private final com.cool.modules.merchant.mapper.MerchantCategoryMapper categories;
    private final org.springframework.beans.factory.ObjectProvider<BaseSysLoginService> loginService;
    private final JwtTokenUtil tokens;
    private final CoolCache cache;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public Object login(BaseSysLoginDto input) {
        try {
            loginService.getObject().captchaCheck(input.getCaptchaId(), input.getVerifyCode());
        } finally {
            cache.del("verify:img:" + input.getCaptchaId());
        }
        BaseSysUserEntity user = users.selectOneByQuery(QueryWrapper.create()
                .eq(BaseSysUserEntity::getUsername, input.getUsername()));
        if (user == null) {
            var application = applications.selectOneByQuery(QueryWrapper.create()
                    .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getUsername, input.getUsername())
                    .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getCurrentFlag, 1));
            if (application == null || !java.util.Set.of(1,2,3).contains(application.getStatus()) || application.getMerchantId()!=null
                    || input.getPassword() == null || !passwords.matches(input.getPassword(), application.getPasswordHash())) unauthorized();
            return issueApplicant(application);
        }
        requireActive(user);
        // 商家账号使用BCrypt；历史MD5账号需运营重新设置密码后绑定，不自动接受弱哈希。
        if (input.getPassword() == null || !user.getPassword().startsWith("$2")
                || !passwords.matches(input.getPassword(), user.getPassword())) {
            throw new CoolException("商家账号或密码不正确，请联系运营确认账号已开通", 401);
        }
        String session = StrUtil.uuid();
        cache.set("merchant:session:" + session, user.getId(), tokens.getRefreshExpire());
        return issue(user, session);
    }

    public BaseSysUserEntity validate(String token, boolean refresh) {
        try {
            if (!(refresh ? tokens.validateRefreshToken(token) : tokens.validateToken(token))) unauthorized();
            JWT jwt = tokens.getTokenInfo(token);
            if (!"MERCHANT".equals(jwt.getPayload("userType"))
                    || !Boolean.valueOf(refresh).equals(jwt.getPayload("isRefresh"))) unauthorized();
            Long userId = Long.valueOf(jwt.getPayload("userId").toString());
            BaseSysUserEntity user = users.selectOneById(userId);
            requireActive(user);
            Long sessionUser = cache.get("merchant:session:" + jwt.getPayload("sessionId"), Long.class);
            if (!Objects.equals(sessionUser, userId)
                    || !Objects.equals(user.getMerchantId().toString(), String.valueOf(jwt.getPayload("merchantId")))
                    || !Objects.equals(user.getMerchantRole(), String.valueOf(jwt.getPayload("merchantRole")))) unauthorized();
            if (!Objects.equals(String.valueOf(user.getPasswordV()), String.valueOf(jwt.getPayload("passwordVersion")))) unauthorized();
            return user;
        } catch (Exception error) {
            throw new CoolException("商家登录已失效，请重新登录", 401);
        }
    }

    public JwtUser principal(BaseSysUserEntity user) {
        JwtUser principal = new JwtUser(user.getId(), user.getUsername(), "",
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")), true);
        principal.setUserTypeEnum(UserTypeEnum.MERCHANT);
        return principal;
    }

    public Object refresh(String token) {
        if (Boolean.TRUE.equals(tokens.getTokenInfo(token).getPayload("applicant"))) {
            var application = validateApplicant(token, true);
            cache.del("merchant:applicant:" + tokens.getTokenInfo(token).getPayload("sessionId"));
            if (Integer.valueOf(2).equals(application.getStatus())) {
                if (application.getMerchantId()==null) return issueApplicant(application);
                var user = users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, application.getUsername()));
                requireActive(user);
                if (!Objects.equals(user.getMerchantId(), application.getMerchantId())
                        || !Objects.equals(user.getPassword(), application.getPasswordHash())) unauthorized();
                String session = StrUtil.uuid();
                cache.set("merchant:session:" + session, user.getId(), tokens.getRefreshExpire());
                return issue(user, session);
            }
            return issueApplicant(application);
        }
        BaseSysUserEntity user = validate(token, true);
        String oldSession = tokens.getTokenInfo(token).getPayload("sessionId").toString();
        cache.del("merchant:session:" + oldSession);
        String session = StrUtil.uuid();
        cache.set("merchant:session:" + session, user.getId(), tokens.getRefreshExpire());
        return issue(user, session);
    }

    public void logout(String token) {
        if (Boolean.TRUE.equals(tokens.getTokenInfo(token).getPayload("applicant"))) {
            validateApplicant(token, false);
            cache.del("merchant:applicant:" + tokens.getTokenInfo(token).getPayload("sessionId"));
            return;
        }
        validate(token, false);
        cache.del("merchant:session:" + tokens.getTokenInfo(token).getPayload("sessionId"));
    }

    public com.cool.modules.approval.entity.MerchantApplicationEntity validateApplicant(String token, boolean refresh) {
        try {
            if (!(refresh ? tokens.validateRefreshToken(token) : tokens.validateToken(token))) unauthorized();
            JWT jwt = tokens.getTokenInfo(token);
            if (!"MERCHANT".equals(jwt.getPayload("userType")) || !Boolean.TRUE.equals(jwt.getPayload("applicant"))
                    || !Boolean.valueOf(refresh).equals(jwt.getPayload("isRefresh"))) unauthorized();
            String number = String.valueOf(jwt.getPayload("applicationNo"));
            if (!Objects.equals(number, cache.get("merchant:applicant:" + jwt.getPayload("sessionId"), String.class))) unauthorized();
            var application = applications.selectOneByQuery(QueryWrapper.create()
                    .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getApplicationNo, number)
                    .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getUsername, jwt.getPayload("username"))
                    .eq(com.cool.modules.approval.entity.MerchantApplicationEntity::getCurrentFlag, 1));
            if (application == null || !java.util.Set.of(1,2,3).contains(application.getStatus())) unauthorized();
            return application;
        } catch (Exception invalid) { throw new CoolException("申请登录已失效，请重新登录", 401); }
    }

    private Dict issueApplicant(com.cool.modules.approval.entity.MerchantApplicationEntity application) {
        String session = StrUtil.uuid();
        cache.set("merchant:applicant:" + session, application.getApplicationNo(), tokens.getRefreshExpire());
        Dict claims = Dict.create().set("userType", "MERCHANT").set("applicant", true)
                .set("applicationNo", application.getApplicationNo()).set("username", application.getUsername()).set("sessionId", session);
        return Dict.create().set("token", tokens.generateToken(claims)).set("expire", tokens.getExpire())
                .set("refreshToken", tokens.generateRefreshToken(claims)).set("refreshExpire", tokens.getRefreshExpire());
    }

    public Dict applicantInfo(com.cool.modules.approval.entity.MerchantApplicationEntity application) {
        var rejected = auditRecords.selectOneByQuery(QueryWrapper.create()
                .eq(com.cool.modules.approval.entity.AuditRecordEntity::getBusinessType, "MERCHANT_APPLICATION")
                .eq(com.cool.modules.approval.entity.AuditRecordEntity::getBusinessId, application.getId())
                .eq(com.cool.modules.approval.entity.AuditRecordEntity::getAction, "REJECT")
                .orderBy(com.cool.modules.approval.entity.AuditRecordEntity::getId, false).limit(1));
        return Dict.create().set("username", application.getUsername()).set("name", application.getSubmittedBy())
                .set("merchantName", application.getMerchantName()).set("onboardingLocked", true)
                .set("status", application.getStatus()).set("applicationNo", application.getApplicationNo())
                .set("activationState",application.getMerchantId()!=null ? "ACTIVATED" : application.getStatus()==2 ? "PENDING_ACTIVATION" : "NOT_APPROVED")
                .set("reason", Integer.valueOf(3).equals(application.getStatus()) && rejected != null ? rejected.getReason() : "")
                .set("snapshot", cn.hutool.json.JSONUtil.parseObj(application.getSnapshot()));
    }

    public BaseSysUserEntity current(Long userId) {
        BaseSysUserEntity user = users.selectOneById(userId);
        requireActive(user);
        return user;
    }

    public boolean platformAccount(Long userId) {
        BaseSysUserEntity user = users.selectOneById(userId);
        return user != null && user.getMerchantId() == null && Integer.valueOf(1).equals(user.getStatus());
    }

    private void requireActive(BaseSysUserEntity user) {
        if (user == null || user.getMerchantId() == null || !Integer.valueOf(1).equals(user.getStatus())
                || !java.util.Set.of("OWNER", "MANAGER", "STAFF").contains(user.getMerchantRole() == null ? "" : user.getMerchantRole()) || "admin".equals(user.getUsername())) unauthorized();
        var merchant = merchants.selectOneById(user.getMerchantId());
        if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) unauthorized();
    }

    private Dict issue(BaseSysUserEntity user, String session) {
        Dict claims = Dict.create().set("userType", "MERCHANT").set("userId", user.getId())
                .set("username", user.getUsername()).set("merchantId", user.getMerchantId()).set("merchantRole", user.getMerchantRole())
                .set("passwordVersion", user.getPasswordV()).set("sessionId", session);
        return Dict.create().set("token", tokens.generateToken(claims)).set("expire", tokens.getExpire())
                .set("refreshToken", tokens.generateRefreshToken(claims)).set("refreshExpire", tokens.getRefreshExpire());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long provision(String username, String name, String password, Long merchantId) {
        if (username == null || !username.matches("[A-Za-z0-9_]{4,40}") || "admin".equals(username)
                || name == null || name.isBlank() || name.length() > 50
                || password == null || password.length() < 6 || password.length() > 64) {
            throw new CoolException("账号需4至40位字母数字下划线，姓名必填，密码需6至64位");
        }
        var merchant = merchantId == null ? null : merchants.selectOneById(merchantId);
        if (merchant == null || !Integer.valueOf(1).equals(merchant.getStatus())) throw new CoolException("请选择已启用商家");
        BaseSysUserEntity user = users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, username));
        if (user != null) throw new CoolException("账号已存在，请使用新的独立商家账号，避免改变运营账号身份");
        user = new BaseSysUserEntity();
        user.setUsername(username); user.setName(name.trim()); user.setNickName(name.trim());
        user.setPassword(passwords.encode(password)); user.setPasswordV(1); user.setStatus(1);
        user.setMerchantId(merchantId); user.setMerchantRole("OWNER");
        users.insertSelective(user);
        return user.getId();
    }

    public List<cn.hutool.core.lang.Dict> registrationCategories() {
        return categories.selectListByQuery(QueryWrapper.create()
                .eq(com.cool.modules.merchant.entity.MerchantCategoryEntity::getStatus, 1))
                .stream().map(c -> Dict.create().set("id", c.getId()).set("name", c.getName()).set("parentId", c.getParentId())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        BaseSysUserEntity user = current(userId);
        if (oldPassword == null || !passwords.matches(oldPassword, user.getPassword()))
            throw new CoolException("原密码不正确");
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 64)
            throw new CoolException("新密码需6至64位");
        if (newPassword.equals(oldPassword)) throw new CoolException("新密码不能与原密码相同");
        replacePassword(user, newPassword);
    }

    @Transactional(rollbackFor = Exception.class)
    public int resetMerchantPassword(Long merchantId) {
        if (merchantId == null || merchants.selectOneById(merchantId) == null)
            throw new CoolException("商家不存在");
        List<BaseSysUserEntity> owners = users.selectListByQuery(QueryWrapper.create()
                .eq(BaseSysUserEntity::getMerchantId, merchantId)
                .eq(BaseSysUserEntity::getMerchantRole, "OWNER"));
        if (owners.isEmpty()) throw new CoolException("该商家尚未开通账号");
        for (BaseSysUserEntity user : owners) replacePassword(user, "123456");
        return owners.size();
    }

    private void replacePassword(BaseSysUserEntity user, String password) {
        BaseSysUserEntity changes = new BaseSysUserEntity();
        changes.setPassword(passwords.encode(password));
        changes.setPasswordV(user.getPasswordV() == null ? 1 : user.getPasswordV() + 1);
        int updated = users.updateByQuery(changes, QueryWrapper.create()
                .eq(BaseSysUserEntity::getId, user.getId())
                .eq(BaseSysUserEntity::getPassword, user.getPassword()));
        if (updated != 1) throw new CoolException("账号已发生变更，请重试");
    }

    private static void unauthorized() { throw new CoolException("商家账号未开通或已停用", 401); }
}
