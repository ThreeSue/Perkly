package com.cool.modules.workspace.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.*;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 兼容旧开户和门店员工接口并同步三端唯一岗位关系
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 01:20:00
 */
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class WorkspaceLegacyAccountBridge {
    private final BaseSysRoleMapper roles;
    private final BaseSysUserRoleMapper assignments;
    private final BaseSysUserMapper users;
    public void bind(BaseSysUserEntity user,String legacyRole,boolean roleChanged){
        var current=assignments.selectListByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getUserId,user.getId()));
        if(!current.isEmpty()&&!roleChanged)return;
        if(current.size()>1)throw new CoolException("账号存在多个岗位，请使用统一账号管理调整");
        if(!current.isEmpty()){
            var previous=roles.selectOneById(current.get(0).getRoleId());
            if(previous==null||!Boolean.TRUE.equals(previous.getBuiltin()))throw new CoolException("自定义岗位请使用统一账号管理调整");
        }
        String label="OWNER".equals(legacyRole)?"ws_owner":"MANAGER".equals(legacyRole)?"ws_manager":"ws_staff";
        var role=roles.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getLabel,label).forUpdate());
        if(role==null)throw new CoolException("内置岗位未初始化，请联系平台管理员");
        assignments.deleteByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getUserId,user.getId()));
        var link=new BaseSysUserRoleEntity();link.setUserId(user.getId());link.setRoleId(role.getId());assignments.insert(link);
        var change=new BaseSysUserEntity();change.setId(user.getId());change.setAccountType("OWNER".equals(legacyRole)?"MERCHANT":"STORE");change.setStoreScope("OWNER".equals(legacyRole)?"ALL":"MANAGER".equals(legacyRole)?"STORE":"SELF");users.update(change);
    }
}
