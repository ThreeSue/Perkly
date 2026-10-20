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
        String encoded = UserPasswordUtil.encode(password);
        userSmsUtil.checkVerifyCode(info.getPhone(), code, SendSceneEnum.ALL);
        info.setPassword(encoded);
        info.updateById();
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
        userSmsUtil.checkVerifyCode(phone, code, SendSceneEnum.ALL);
        UserInfoEntity info = new UserInfoEntity();
        info.setId(userId);
        info.setPhone(phone);
        info.updateById();
    }
}
