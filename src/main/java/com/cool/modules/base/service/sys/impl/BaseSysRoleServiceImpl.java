package com.cool.modules.base.service.sys.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONObject;
import com.cool.core.base.BaseServiceImpl;
import com.cool.core.base.ModifyEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.base.entity.sys.BaseSysRoleDepartmentEntity;
import com.cool.modules.base.entity.sys.BaseSysRoleEntity;
import com.cool.modules.base.entity.sys.BaseSysRoleMenuEntity;
import com.cool.modules.base.mapper.sys.BaseSysRoleDepartmentMapper;
import com.cool.modules.base.mapper.sys.BaseSysRoleMapper;
import com.cool.modules.base.mapper.sys.BaseSysRoleMenuMapper;
import com.cool.modules.base.mapper.sys.BaseSysUserRoleMapper;
import com.cool.modules.base.entity.sys.BaseSysUserRoleEntity;
import com.cool.modules.base.service.sys.BaseSysPermsService;
import com.cool.modules.base.service.sys.BaseSysRoleService;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统角色
 */
@RequiredArgsConstructor
@Service
public class BaseSysRoleServiceImpl extends BaseServiceImpl<BaseSysRoleMapper, BaseSysRoleEntity>
        implements BaseSysRoleService {

    final private BaseSysRoleMapper baseSysRoleMapper;

    final private BaseSysRoleMenuMapper baseSysRoleMenuMapper;

    final private BaseSysRoleDepartmentMapper baseSysRoleDepartmentMapper;

    final private BaseSysPermsService baseSysPermsService;
    final private BaseSysUserRoleMapper baseSysUserRoleMapper;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public boolean delete(Long... ids){
        if(ids==null||ids.length==0||Arrays.stream(ids).anyMatch(id->id==null||id<=1))throw new CoolException("请选择有效角色，超级管理员角色不可删除");
        var ordered=Arrays.stream(ids).distinct().sorted().toList();
        for(Long id:ordered){
            var r=baseSysRoleMapper.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getId,id).forUpdate());
            if(r==null)throw new CoolException("角色不存在");
            if(r.getAccountType()!=null)throw new CoolException("下级岗位请在三端岗位权限中维护");
            var assigned=baseSysUserRoleMapper.selectListByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getRoleId,id).forUpdate());
            if(!assigned.isEmpty())throw new CoolException("角色“"+r.getName()+"”有"+assigned.size()+"个账号使用，请先调整这些账号的角色");
        }
        for(Long id:ordered){
            baseSysRoleMenuMapper.deleteByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId,id));
            baseSysRoleDepartmentMapper.deleteByQuery(QueryWrapper.create().eq(BaseSysRoleDepartmentEntity::getRoleId,id));
        }
        return super.delete(ordered.toArray(Long[]::new));
    }

    @Override
    public Object page(JSONObject params,com.mybatisflex.core.paginate.Page<BaseSysRoleEntity> page,QueryWrapper query){
        return super.page(params,page,query.isNull(BaseSysRoleEntity::getAccountType));
    }

    @Override
    public void modifyBefore(JSONObject params,BaseSysRoleEntity role,ModifyEnum type){
        if(type!=ModifyEnum.DELETE && role!=null){
            if(role.getAccountType()!=null)throw new CoolException("下级岗位请使用三端岗位管理");
            if(role.getId()!=null){var old=baseSysRoleMapper.selectOneById(role.getId());if(old!=null&&old.getAccountType()!=null)throw new CoolException("不能通过平台角色接口修改下级岗位");}
        }
    }

    @Override
    public Object add(JSONObject requestParams, BaseSysRoleEntity entity) {
        BaseSysRoleEntity checkLabel = getOne(QueryWrapper.create().eq(BaseSysRoleEntity::getLabel, entity.getLabel()));
        if (checkLabel != null) {
            throw new CoolException("标识已存在");
        }
        entity.setUserId((CoolSecurityUtil.getAdminUserInfo(requestParams).getLong("userId")));
        return super.add(requestParams, entity);
    }

    @Override
    public BaseSysRoleEntity info(Long id) {
        BaseSysRoleEntity roleEntity = getById(id);
        if(roleEntity!=null && roleEntity.getAccountType()!=null)throw new CoolException("下级岗位请使用三端岗位管理");
        Long[] menuIdList = new Long[0];
        Long[] departmentIdList = new Long[0];
        if (roleEntity != null) {
            List<BaseSysRoleMenuEntity> list = baseSysRoleMenuMapper
                    .selectListByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId, id, !id.equals(1L)));
            menuIdList = list.stream().map(BaseSysRoleMenuEntity::getMenuId).toArray(Long[]::new);

            List<BaseSysRoleDepartmentEntity> departmentEntities = baseSysRoleDepartmentMapper.selectListByQuery(
                    QueryWrapper.create().eq(BaseSysRoleDepartmentEntity::getRoleId, id, !id.equals(1L)));

            departmentIdList = departmentEntities.stream().map(BaseSysRoleDepartmentEntity::getDepartmentId)
                    .toArray(Long[]::new);
            
            
            roleEntity.setMenuIdList(Arrays.asList( menuIdList ));
            roleEntity.setDepartmentIdList(Arrays.asList(departmentIdList ));
            
        }
        
        return roleEntity;
    }

    @Override
    public void modifyAfter(JSONObject requestParams, BaseSysRoleEntity baseSysRoleEntity, ModifyEnum type) {
        if (type == ModifyEnum.DELETE) {
            Long[] ids = requestParams.get("ids", Long[].class);
            for (Long id : ids) {
                baseSysPermsService.refreshPermsByRoleId(id);
            }
        } else {
            baseSysPermsService.updatePerms(baseSysRoleEntity.getId(), requestParams.get("menuIdList", Long[].class),
                    requestParams.get("departmentIdList", Long[].class));
        }
    }

    @Override
    public Object list(JSONObject requestParams, QueryWrapper queryWrapper) {
        return baseSysRoleMapper.selectListByQuery(queryWrapper.isNull(BaseSysRoleEntity::getAccountType).ne(BaseSysRoleEntity::getId, 1L).and(qw -> {
            JSONObject object = CoolSecurityUtil.getAdminUserInfo(requestParams);
            qw.eq(BaseSysRoleEntity::getUserId, object.get("userId")).or(w -> {
                w.in(BaseSysRoleEntity::getId,
                        (Object) object.get("roleIds", Long[].class));
            });
        }, !CoolSecurityUtil.getAdminUsername().equals("admin")));
    }
}
