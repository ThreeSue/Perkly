package com.cool.modules.support.service;
import com.cool.core.exception.CoolException;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一校验客服业务顾客与运营身份
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:38
 */
@Service @RequiredArgsConstructor
public class SupportAccess {
    private final UserInfoMapper customers;
    public UserInfoEntity customer(Long id) {
        var u=id==null?null:customers.selectOneById(id);
        if(u==null || !Integer.valueOf(1).equals(u.getStatus())) throw new CoolException("用户已失效",403);
        return u;
    }
    public void platform(String permission) {
        var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN || a==null ||
            (!CoolSecurityUtil.isSuperAdmin() && a.getAuthorities().stream().noneMatch(x->permission.equals(x.getAuthority()))))
            throw new CoolException("没有此运营操作权限",403);
    }
    public static void page(int page,int size){if(page<1 || size<1 || size>100)throw new CoolException("分页参数无效");}
    public static void request(String id){if(id==null || !id.matches("[A-Za-z0-9_-]{8,64}"))throw new CoolException("请求标识无效");}
}
