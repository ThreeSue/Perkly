package com.cool.modules.user.service.impl;

import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.hutool.core.util.ObjUtil;
import com.cool.core.base.BaseServiceImpl;
import com.cool.core.exception.CoolPreconditions;
import com.cool.modules.user.entity.UserWxEntity;
import com.cool.modules.user.mapper.UserWxMapper;
import com.cool.modules.user.proxy.WxProxy;
import com.cool.modules.user.service.UserWxService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;

/**
 * 微信用户
 */
@Service
@RequiredArgsConstructor
public class UserWxServiceImpl extends BaseServiceImpl<UserWxMapper, UserWxEntity> implements UserWxService {

    private final WxProxy wxProxy;

    /**
     * 获得小程序用户信息
     */
    public UserWxEntity getMiniUserInfo(String code, String encryptedData, String iv) {
        // 身份登录仅由微信服务端兑换 code；不依赖客户端头像昵称加密数据。
        try {
            WxMaJscode2SessionResult result = wxProxy.getSessionInfo(code);
            CoolPreconditions.check(result == null || ObjUtil.isEmpty(result.getOpenid()), "微信登录凭证无效");
            UserWxEntity userWxEntity = new UserWxEntity();
            userWxEntity.setOpenid(result.getOpenid());
            userWxEntity.setUnionid(result.getUnionid());
            userWxEntity.setGender(0);
            return getBySave(userWxEntity, 0);
        } catch (WxErrorException e) {
            CoolPreconditions.alwaysThrow("微信登录失败，请检查小程序配置或重新获取登录凭证", e);
        }
        CoolPreconditions.alwaysThrow("获得小程序用户信息");
        return null;
    }

    public UserWxEntity getBySave(UserWxEntity entity, int type) {
        UserWxEntity one = this.getOne(
            QueryWrapper.create().eq(UserWxEntity::getOpenid, entity.getOpenid()));
        if (ObjUtil.isEmpty(one)) {
            entity.setType(type);
            super.save(entity);
            return entity;
        }
        return one;
    }
}
