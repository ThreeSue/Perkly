package com.cool.modules.user.service.impl;

import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.jwt.JWT;
import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolPreconditions;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.modules.base.service.sys.BaseSysLoginService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.entity.UserWxEntity;
import com.cool.modules.user.proxy.WxProxy;
import com.cool.modules.user.service.UserInfoService;
import com.cool.modules.user.service.UserLoginService;
import com.cool.modules.user.service.UserWxService;
import com.cool.modules.user.util.UserSmsUtil;
import com.cool.modules.user.util.UserPasswordUtil;
import com.cool.modules.user.util.UserSmsUtil.SendSceneEnum;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import com.cool.core.exception.CoolException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UserLoginServiceImpl implements UserLoginService {

    private final CoolCache coolCache;

    private final JwtTokenUtil jwtTokenUtil;

    private final UserInfoService userInfoService;

    private final UserSmsUtil userSmsUtil;

    private final BaseSysLoginService baseSysLoginService;

    private final UserWxService userWxService;

    private final WxProxy wxProxy;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private static final String WX_BIND_PREFIX = "app:wx:binding:";
    private static final String WX_CONFIRM_PREFIX = "app:wx:confirm:";
    private final static List<GrantedAuthority> authority =
      List.of(new SimpleGrantedAuthority("ROLE_" + UserTypeEnum.APP.name()));

    @Override
    public Object register(String phone, String smsCode, String password) {
        UserSmsUtil.validatePhone(phone);
        // 密码不符合规则时不消耗短信验证码。
        String encodedPassword = UserPasswordUtil.encode(password);
        UserInfoEntity existing = userInfoService.getOne(
            QueryWrapper.create().eq(UserInfoEntity::getPhone, phone));
        if (existing != null) throw new CoolException("该手机号已注册，请直接登录");
        userSmsUtil.checkVerifyCode(phone, smsCode, SendSceneEnum.ALL);
        UserInfoEntity user = new UserInfoEntity();
        user.setPhone(phone);
        user.setPassword(encodedPassword);
        user.setNickName(generateRandomNickname());
        user.setStatus(1);
        try {
            if (!userInfoService.save(user)) throw new CoolException("注册未成功，请重新获取验证码后重试");
        } catch (DuplicateKeyException e) {
            throw new CoolException("该手机号已注册，请直接登录");
        }
        return generateToken(user, null);
    }

    @Override
    public void smsCode(String phone, String captchaId, String code) {
        UserSmsUtil.validatePhone(phone);
        // 校验图片验证码，不通过直接抛异常
        baseSysLoginService.captchaCheck(captchaId, code);
        userSmsUtil.sendVerifyCode(phone, SendSceneEnum.ALL);
        coolCache.del("verify:img:" + captchaId);
    }

    @Override
    public Object phoneVerifyCode(String phone, String smsCode) {
        // 校验短信验证码，不通过直接抛异常
        userSmsUtil.checkVerifyCode(phone, smsCode, SendSceneEnum.ALL);
        return generateTokenByPhone(phone);
    }


    @Override
    public Object refreshToken(String refreshToken) {
        CoolPreconditions.check(!jwtTokenUtil.validateRefreshToken(refreshToken), "错误的refreshToken");
        JWT jwt = jwtTokenUtil.getTokenInfo(refreshToken);
        CoolPreconditions.check(jwt == null || !(Boolean) jwt.getPayload("isRefresh"),
            "错误的refreshToken");
        Long userId = Convert.toLong(jwt.getPayload("userId"));
        return generateToken(userId, refreshToken);
    }

    @Override
    public Object mini(String code, String encryptedData, String iv) {
        UserWxEntity userWxEntity = userWxService.getMiniUserInfo(code, encryptedData, iv);
        return wxLoginToken(userWxEntity);
    }

    private Object wxLoginToken(UserWxEntity userWxEntity) {
        UserInfoEntity account = findWechatAccount(userWxEntity);
        if (account != null) {
            CoolPreconditions.check(!Integer.valueOf(1).equals(account.getStatus()), "账号不可用");
            if (ObjUtil.isNotEmpty(account.getPhone())) return generateToken(account, null);
        }
        String bindingToken = UUID.randomUUID().toString();
        coolCache.set(WX_BIND_PREFIX + bindingToken, Dict.create()
            .set("wxId", userWxEntity.getId()).set("expiresAt", System.currentTimeMillis() + 600_000L), 600);
        return Dict.create().set("bindingRequired", true).set("bindingToken", bindingToken).set("bindingExpire", 600);
    }

    private String wechatIdentity(UserWxEntity wx) {
        return ObjUtil.isNotEmpty(wx.getUnionid()) ? wx.getUnionid() : wx.getOpenid();
    }

    private UserInfoEntity findWechatAccount(UserWxEntity wx) {
        if (wx.getUserId() != null) {
            UserInfoEntity account = userInfoService.getById(wx.getUserId());
            CoolPreconditions.check(account == null, "微信关联账号不存在");
            return account;
        }
        // 兼容旧版以 unionid/openid 存在 user_info 的账号，不删除旧记录。
        return userInfoService.getOne(QueryWrapper.create().eq(UserInfoEntity::getUnionid, wechatIdentity(wx)));
    }

    @Override
    public synchronized Object bindWechatPhone(String bindingToken, String phone, String smsCode) {
        UserSmsUtil.validatePhone(phone);
        if (bindingToken == null || !bindingToken.matches("[0-9a-fA-F-]{36}")) throw new CoolException("请重新点击微信登录");
        String key = WX_BIND_PREFIX + bindingToken;
        Dict ticket = coolCache.get(key, Dict.class);
        if (ticket == null || ticket.getLong("expiresAt") == null || ticket.getLong("wxId") == null
                || ticket.getLong("expiresAt") <= System.currentTimeMillis()) {
            coolCache.del(key);
            throw new CoolException("微信绑定凭证已失效，请重新点击微信登录");
        }
        Object result;
        try {
            // 锁覆盖本机整个数据库事务，唯一索引同时约束重复绑定。
            result = new TransactionTemplate(transactionManager).execute(status -> {
                UserWxEntity wx = userWxService.getById(ticket.getLong("wxId"));
                CoolPreconditions.check(wx == null, "微信绑定凭证无效");
                UserInfoEntity wxAccount = findWechatAccount(wx);
                if (wxAccount != null) {
                    CoolPreconditions.check(!Integer.valueOf(1).equals(wxAccount.getStatus()), "账号不可用");
                    CoolPreconditions.check(ObjUtil.isNotEmpty(wxAccount.getPhone()) && !phone.equals(wxAccount.getPhone()),
                        "当前微信已绑定其他手机号");
                }
                UserInfoEntity target = userInfoService.getOne(QueryWrapper.create().eq(UserInfoEntity::getPhone, phone));
                if (target != null) {
                    CoolPreconditions.check(!Integer.valueOf(1).equals(target.getStatus()), "账号不可用");
                    CoolPreconditions.check(ObjUtil.isNotEmpty(target.getUnionid()) && !wechatIdentity(wx).equals(target.getUnionid()),
                        "该手机号已绑定其他微信");
                    CoolPreconditions.check(userWxService.count(QueryWrapper.create()
                        .eq(UserWxEntity::getUserId, target.getId()).ne(UserWxEntity::getId, wx.getId())) > 0,
                        "该手机号已绑定其他微信");
                }
                userSmsUtil.checkVerifyCode(phone, smsCode, SendSceneEnum.ALL);
                if (target != null && (wxAccount == null || !target.getId().equals(wxAccount.getId()))) {
                    return previewWechatBinding(wx, wxAccount, target);
                }
                if (target == null) {
                    target = wxAccount != null ? wxAccount : new UserInfoEntity();
                    target.setPhone(phone);
                    if (target.getId() == null) {
                        target.setNickName(generateRandomNickname());
                        target.setStatus(1);
                        target.setGender(0);
                        CoolPreconditions.check(!userInfoService.save(target), "账号创建失败，请重试");
                    } else {
                        CoolPreconditions.check(!userInfoService.updateById(target), "手机号绑定失败，请重试");
                    }
                }
                UserWxEntity link = new UserWxEntity();
                link.setId(wx.getId());
                link.setUserId(target.getId());
                CoolPreconditions.check(!userWxService.updateById(link), "微信绑定失败，请重试");
                return target;
            });
        } catch (DuplicateKeyException e) {
            throw new CoolException("账号绑定发生冲突，请重新获取验证码后重试");
        }
        coolCache.del(key);
        if (result instanceof Dict) return result;
        return generateToken((UserInfoEntity) result, null);
    }

    private Dict previewWechatBinding(UserWxEntity wx, UserInfoEntity wxAccount, UserInfoEntity target) {
        String token = UUID.randomUUID().toString();
        String sourceName = wxAccount != null ? wxAccount.getNickName() : wx.getNickName();
        String sourceAvatar = wxAccount != null ? wxAccount.getAvatarUrl() : wx.getAvatarUrl();
        boolean canUseWechatProfile = ObjUtil.isNotEmpty(sourceName) || ObjUtil.isNotEmpty(sourceAvatar);
        coolCache.set(WX_CONFIRM_PREFIX + token, Dict.create().set("wxId", wx.getId()).set("targetId", target.getId())
            .set("phone", target.getPhone()).set("nickName", sourceName).set("avatarUrl", sourceAvatar)
            .set("canUseWechatProfile", canUseWechatProfile).set("expiresAt", System.currentTimeMillis() + 600_000L), 600);
        String phone = target.getPhone();
        return Dict.create().set("confirmationRequired", true).set("confirmationToken", token).set("confirmationExpire", 600)
            .set("canUseWechatProfile", canUseWechatProfile)
            .set("wechatProfile", Dict.create().set("nickName", sourceName).set("avatarUrl", sourceAvatar)
                .set("id", wxAccount != null ? wxAccount.getId() : null))
            .set("phoneProfile", Dict.create().set("id", target.getId()).set("nickName", target.getNickName())
                .set("avatarUrl", target.getAvatarUrl()).set("phoneMasked", phone.substring(0, 3) + "****" + phone.substring(7)));
    }

    @Override
    public synchronized Object confirmWechatBinding(String confirmationToken, String profileSource) {
        if (!"phone".equals(profileSource) && !"wechat".equals(profileSource)) throw new CoolException("请选择要保留的资料");
        if (confirmationToken == null || !confirmationToken.matches("[0-9a-fA-F-]{36}")) throw new CoolException("请重新进行微信绑定");
        String key = WX_CONFIRM_PREFIX + confirmationToken;
        Dict ticket = coolCache.get(key, Dict.class);
        if (ticket == null || ticket.getLong("expiresAt") == null || ticket.getLong("expiresAt") <= System.currentTimeMillis()) {
            coolCache.del(key);
            throw new CoolException("绑定确认已过期，请重新进行微信绑定");
        }
        if ("wechat".equals(profileSource) && !Boolean.TRUE.equals(ticket.getBool("canUseWechatProfile"))) {
            throw new CoolException("微信账号尚未设置昵称头像，请保留手机号账号资料");
        }
        UserInfoEntity account;
        try {
            account = new TransactionTemplate(transactionManager).execute(status -> {
                UserWxEntity wx = userWxService.getById(ticket.getLong("wxId"));
                UserInfoEntity target = userInfoService.getById(ticket.getLong("targetId"));
                CoolPreconditions.check(wx == null || target == null || !Integer.valueOf(1).equals(target.getStatus())
                    || !ticket.getStr("phone").equals(target.getPhone()), "账号状态已变更，请重新绑定");
                UserInfoEntity wxAccount = findWechatAccount(wx);
                CoolPreconditions.check(wxAccount != null && !Integer.valueOf(1).equals(wxAccount.getStatus()), "账号不可用");
                CoolPreconditions.check(wxAccount != null && ObjUtil.isNotEmpty(wxAccount.getPhone())
                    && !target.getId().equals(wxAccount.getId()), "当前微信已绑定其他账号");
                CoolPreconditions.check(ObjUtil.isNotEmpty(target.getUnionid()) && !wechatIdentity(wx).equals(target.getUnionid()),
                    "该手机号已绑定其他微信");
                CoolPreconditions.check(userWxService.count(QueryWrapper.create().eq(UserWxEntity::getUserId, target.getId())
                    .ne(UserWxEntity::getId, wx.getId())) > 0, "该手机号已绑定其他微信");
                if ("wechat".equals(profileSource)) {
                    UserInfoEntity patch = new UserInfoEntity();
                    patch.setId(target.getId());
                    if (ObjUtil.isNotEmpty(ticket.getStr("nickName"))) {
                        patch.setNickName(ticket.getStr("nickName")); target.setNickName(patch.getNickName());
                    }
                    if (ObjUtil.isNotEmpty(ticket.getStr("avatarUrl"))) {
                        patch.setAvatarUrl(ticket.getStr("avatarUrl")); target.setAvatarUrl(patch.getAvatarUrl());
                    }
                    CoolPreconditions.check(!userInfoService.updateById(patch), "资料保存失败，请重试");
                }
                UserWxEntity link = new UserWxEntity(); link.setId(wx.getId()); link.setUserId(target.getId());
                CoolPreconditions.check(!userWxService.updateById(link), "微信绑定失败，请重试");
                return target;
            });
        } catch (DuplicateKeyException e) {
            throw new CoolException("账号绑定发生冲突，请重新进行微信绑定");
        }
        coolCache.del(key);
        return generateToken(account, null);
    }

    @Override
    public Object mp(String code) {
        return null;
    }

    @Override
    public Object wxApp(String code) {
        return null;
    }

    @Override
    public Object uniPhone(String accessToken, String openid, String appId) {
        return null;
    }

    @Override
    public Object miniPhone(String code, String encryptedData, String iv) {
        try {
            WxMaPhoneNumberInfo phoneNumber = wxProxy.getPhoneNumber(code);
            CoolPreconditions.checkEmpty(phoneNumber, "微信登录失败");
            return generateTokenByPhone(phoneNumber.getPhoneNumber());
        } catch (WxErrorException e) {
            CoolPreconditions.alwaysThrow(e.getMessage(), e);
        }
        CoolPreconditions.alwaysThrow("微信登录失败");
        return null;
    }

    @Override
    public Object password(String phone, String password) {
        UserSmsUtil.validatePhone(phone);
        UserInfoEntity userInfoEntity = userInfoService.getOne(
            QueryWrapper.create().eq(UserInfoEntity::getPhone, phone));
        CoolPreconditions.checkEmpty(userInfoEntity, "账号或密码错误");
        CoolPreconditions.check(!UserPasswordUtil.matches(password, userInfoEntity.getPassword()),
            "账号或密码错误；未设置密码时请先使用短信登录");
        return generateToken(userInfoEntity, null);
    }

    /**
     * 前置已校验用户的手机号，
     * 根据手机号找到用户生成token
     */
    private Object generateTokenByPhone(String phone) {
        UserInfoEntity userInfoEntity = userInfoService.getOne(
            QueryWrapper.create().eq(UserInfoEntity::getPhone, phone));
        if (ObjUtil.isEmpty(userInfoEntity)) {
            userInfoEntity = new UserInfoEntity();
            userInfoEntity.setPhone(phone);
            userInfoEntity.setStatus(1);
            // 生成随机昵称
            userInfoEntity.setNickName(generateRandomNickname());
            userInfoEntity.save();
        }
        return generateToken(userInfoEntity, null);
    }

    /**
     *
     * @return 生成的昵称
     */
    private String generateRandomNickname() {
        // 定义昵称的长度
        int length = 8;
        // 生成随机字符串
        return RandomUtil.randomString(length);
    }
    /**
     * 生成token
     */
    private Dict generateToken(Long userId, String refreshToken) {
        UserInfoEntity userInfoEntity = userInfoService.getById(userId);
        return generateToken(userInfoEntity, refreshToken);
    }
    private Dict generateToken(UserInfoEntity userInfoEntity, String refreshToken) {
        CoolPreconditions.check(userInfoEntity == null || !Integer.valueOf(1).equals(userInfoEntity.getStatus()),
            "账号不可用");
        Dict tokenInfo = Dict.create()
            .set("userType", UserTypeEnum.APP.name())
            .set("userId", userInfoEntity.getId());
        String token = jwtTokenUtil.generateToken(tokenInfo);
        if (ObjUtil.isEmpty(refreshToken)) {
            refreshToken = jwtTokenUtil.generateRefreshToken(tokenInfo);
        }
        JwtUser jwtUser = new JwtUser(userInfoEntity.getId(),
            authority,
            ObjUtil.equals(userInfoEntity.getStatus(), 1));
        coolCache.set("app:userDetails:" + jwtUser.getUserId(), jwtUser);
        return Dict.create()
            .set("userId", userInfoEntity.getId())
            .set("token", token)
            .set("expire", jwtTokenUtil.getExpire())
            .set("refreshToken", refreshToken)
            .set("refreshExpire", jwtTokenUtil.getRefreshExpire());
    }
}
