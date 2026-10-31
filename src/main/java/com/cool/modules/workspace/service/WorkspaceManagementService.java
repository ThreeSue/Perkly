package com.cool.modules.workspace.service;

import com.cool.core.exception.CoolException;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.workspace.entity.*;
import com.cool.modules.workspace.mapper.*;
import com.cool.modules.workspace.model.WorkspaceModels.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在逐级授权及主体范围内维护单角色账号岗位个人权限与默认门店
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:28:00
 */
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class WorkspaceManagementService {
    private final WorkspaceAuthorization auth;
    private final com.cool.modules.approval.service.ApprovalHistoryService history;
    private final BaseSysUserMapper users;
    private final BaseSysRoleMapper roles;
    private final BaseSysMenuMapper menus;
    private final BaseSysRoleMenuMapper roleMenus;
    private final BaseSysUserRoleMapper userRoles;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final MerchantUserStoreMapper memberships;
    private final WorkspaceGrantMapper grants;
    private final WorkspaceUserScopeMapper scopes;
    private final WorkspaceRoleStoreMapper roleStores;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder();

    private void platform(){
        if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN || (!CoolSecurityUtil.isSuperAdmin() &&
            SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().noneMatch(a->"workspace:administration:manage".equals(a.getAuthority()))))
            throw new CoolException("没有运营授权管理权限",403);
    }
    private BaseSysUserEntity actor(Long id,String permission){if(id==null){platform();return null;}auth.require(id,permission);auth.context(id);return auth.user(id);}
    private Long merchant(BaseSysUserEntity actor,Long requested){
        if(actor!=null){if(requested!=null && !requested.equals(actor.getMerchantId()))throw new CoolException("无权管理其他商家",403);return actor.getMerchantId();}
        if(requested==null || merchants.selectOneById(requested)==null)throw new CoolException("请选择有效商家");return requested;
    }
    private Set<Long> ids(List<Long> ids){if(ids==null)return new HashSet<>();if(ids.size()>1000 || ids.stream().anyMatch(i->i==null||i<=0))throw new CoolException("权限或门店ID无效");return new HashSet<>(ids);}
    private void subset(Collection<Long> selected,Collection<Long> allowed){if(!new HashSet<>(allowed).containsAll(selected))throw new CoolException("包含无权分配的功能、岗位或门店",403);}
    private List<Long> roleMenuIds(Long role){return roleMenus.selectListByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId,role)).stream().map(BaseSysRoleMenuEntity::getMenuId).toList();}
    private List<Long> roleStoreIds(Long role){return roleStores.selectListByQuery(QueryWrapper.create().eq(WorkspaceRoleStoreEntity::getRoleId,role)).stream().map(WorkspaceRoleStoreEntity::getStoreId).toList();}
    private void validStores(BaseSysUserEntity actor,Long merchant,Collection<Long> selected){
        List<Long> allowed=actor==null?stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant)).stream().map(MerchantStoreEntity::getId).toList():auth.storeIds(actor);
        subset(selected,allowed);
    }
    private void roleScope(BaseSysUserEntity actor,BaseSysRoleEntity role){
        if(role==null || role.getAccountType()==null)throw new CoolException("岗位不存在",404);
        if(actor==null)return;
        if(Boolean.TRUE.equals(role.getBuiltin()))throw new CoolException("内置共享岗位仅运营可修改",403);
        if(!Objects.equals(actor.getMerchantId(),role.getMerchantId()))throw new CoolException("岗位不属于本商家",403);
        if("STORE".equals(WorkspaceAuthorization.type(actor)) && !Objects.equals(role.getStoreId(),auth.boundStore(actor).getId()))throw new CoolException("只能维护本店自定义岗位",403);
    }
    public List<Option> merchants(Long actorId){var a=actor(actorId,"account:manage");
        return merchants.selectListByQuery(a==null?QueryWrapper.create():QueryWrapper.create().eq(MerchantInfoEntity::getId,a.getMerchantId())).stream().map(m->new Option(m.getId(),m.getName(),m.getStatus())).toList();}
    public List<Option> stores(Long actorId,Long merchantId){var a=actorId==null?actor(null,"store:view"):auth.user(actorId);if(actorId!=null)auth.context(actorId);
        Long m=merchant(a,merchantId);var allowed=a==null?null:new HashSet<>(auth.storeIds(a));
        return stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,m)).stream().filter(s->allowed==null||allowed.contains(s.getId())).map(s->new Option(s.getId(),s.getName(),s.getStatus())).toList();}
    public List<BaseSysMenuEntity> catalog(Long actorId){var a=actorId==null?actor(null,"role:manage"):auth.user(actorId);if(a!=null){auth.context(actorId);var p=auth.permissions(actorId);if(!p.contains("role:manage")&&!p.contains("account:manage"))throw new CoolException("没有岗位或账号管理权限",403);}if(a==null)return auth.catalog();return auth.catalog();}
    public List<RoleView> roleList(Long actorId,Filter f){
        var a=actor(actorId,"role:manage");Long m=a!=null?a.getMerchantId():f.merchantId();
        var list=roles.selectListByQuery(QueryWrapper.create().isNotNull(BaseSysRoleEntity::getAccountType));
        var counts=roleAccountCounts(a,m);
        var globalCounts=a==null && m!=null?roleAccountCounts(null,null):counts;
        return list.stream().filter(r->m==null||Boolean.TRUE.equals(r.getBuiltin())||m.equals(r.getMerchantId()))
            .filter(r->f.accountType()==null||f.accountType().equals(r.getAccountType()))
            .filter(r->f.builtin()==null||f.builtin().equals(Boolean.TRUE.equals(r.getBuiltin())))
            .filter(r->f.keyword()==null||(r.getName()+r.getLabel()).toLowerCase().contains(f.keyword().toLowerCase()))
            .filter(r->a==null||!"STORE".equals(WorkspaceAuthorization.type(a))||"STORE".equals(r.getAccountType())&&(Boolean.TRUE.equals(r.getBuiltin())||Objects.equals(r.getStoreId(),auth.boundStore(a).getId())))
                        .map(r->roleView(r,counts.getOrDefault(r.getId(),0L),a==null?globalCounts.getOrDefault(r.getId(),0L):null)).toList();
    }
    private Map<Long,Long> roleAccountCounts(BaseSysUserEntity actor,Long merchantId){
        var query=QueryWrapper.create().isNotNull(BaseSysUserEntity::getMerchantId);
        if(merchantId!=null)query.eq(BaseSysUserEntity::getMerchantId,merchantId);
        var visible=users.selectListByQuery(query).stream().filter(u->accessible(actor,u)).toList();
        if(visible.isEmpty())return Map.of();
        var byUser=userRoles.selectListByQuery(QueryWrapper.create().in(BaseSysUserRoleEntity::getUserId,visible.stream().map(BaseSysUserEntity::getId).toList()))
            .stream().collect(Collectors.groupingBy(BaseSysUserRoleEntity::getUserId));
        var result=new HashMap<Long,Long>();
        for(var u:visible){var assigned=byUser.getOrDefault(u.getId(),List.of());if(assigned.size()==1 && assigned.get(0).getRoleId()!=null)result.merge(assigned.get(0).getRoleId(),1L,Long::sum);}
        return result;
    }
    private RoleView roleView(BaseSysRoleEntity r,long accountCount,Long globalCount){var m=r.getMerchantId()==null?null:merchants.selectOneById(r.getMerchantId());return new RoleView(r.getId(),r.getName(),r.getLabel(),r.getRemark(),r.getAccountType(),r.getMerchantId(),m==null?"全商家共用":m.getName(),r.getStoreId(),r.getBuiltin(),r.getStatus(),roleMenuIds(r.getId()),r.getGrantMenuIdList()==null?List.of():r.getGrantMenuIdList(),roleStoreIds(r.getId()),accountCount,globalCount);}
    private RoleView roleView(BaseSysRoleEntity r){long count=userRoles.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getRoleId,r.getId()));return roleView(r,count,null);}
    public Long saveRole(Long actorId,RoleInput p){
        var a=actor(actorId,"role:manage");var r=p.id()==null?new BaseSysRoleEntity():roles.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getId,p.id()).forUpdate());
        if(p.id()!=null)roleScope(a,r);
        if(p.name()==null||p.name().isBlank()||p.name().length()>50||p.label()==null||!p.label().matches("[A-Za-z0-9_-]{2,100}"))throw new CoolException("请填写50字以内岗位名及2至100位英文标识");
        if(!Set.of("MERCHANT","STORE").contains(Objects.toString(p.accountType(),"")))throw new CoolException("请选择岗位适用账号类型");
        if(a!=null&&"STORE".equals(WorkspaceAuthorization.type(a))&&!"STORE".equals(p.accountType()))throw new CoolException("店长只能创建本店岗位",403);
        boolean builtin=p.id()!=null&&Boolean.TRUE.equals(r.getBuiltin());
        Long m=builtin?null:merchant(a,p.merchantId());
        if(p.id()!=null && (!Objects.equals(r.getMerchantId(),m)||!Objects.equals(r.getAccountType(),p.accountType())))throw new CoolException("在用岗位的归属和适用类型不可直接变更");
        Set<Long> selected=ids(p.menuIds()),delegatable=ids(p.grantMenuIds());
        var available=auth.catalog().stream().filter(x->"SHARED".equals(x.getAccountType())||p.accountType().equals(x.getAccountType())).map(BaseSysMenuEntity::getId).collect(Collectors.toSet());
        subset(selected,available);subset(delegatable,available);
        if(a!=null){var permitted=auth.grantable(a);var existingUsage=p.id()==null?Set.<Long>of():new HashSet<>(roleMenuIds(r.getId()));var existingDelegation=p.id()==null||r.getGrantMenuIdList()==null?Set.<Long>of():new HashSet<>(r.getGrantMenuIdList());
            var additions=new HashSet<>(selected);additions.removeAll(existingUsage);subset(additions,permitted);additions=new HashSet<>(delegatable);additions.removeAll(existingDelegation);subset(additions,permitted);
        }
        Set<Long> applicable=ids(p.applicableStoreIds());
        if(m!=null)validStores(a,m,applicable);
        if(a!=null&&"STORE".equals(WorkspaceAuthorization.type(a))){applicable=Set.of(auth.boundStore(a).getId());r.setStoreId(auth.boundStore(a).getId());}
        if(builtin && !applicable.isEmpty())throw new CoolException("共享内置岗位不能绑定单个商家的门店");
        String label=p.id()==null?"ws_"+m+"_"+p.label():r.getLabel();
        r.setName(p.name().trim());r.setLabel(label);r.setRemark(p.remark());r.setMerchantId(m);r.setAccountType(p.accountType());r.setGrantMenuIdList(new ArrayList<>(delegatable));r.setStatus(state(p.status()));
        if(p.id()==null){r.setBuiltin(false);r.setUserId(CoolSecurityUtil.getCurrentUserId());r.setRelevance(0);roles.insert(r);}else roles.update(r);
        roleMenus.deleteByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId,r.getId()));
        for(Long id:selected){var link=new BaseSysRoleMenuEntity();link.setRoleId(r.getId());link.setMenuId(id);roleMenus.insert(link);}
        roleStores.deleteByQuery(QueryWrapper.create().eq(WorkspaceRoleStoreEntity::getRoleId,r.getId()));
        for(Long id:applicable){var link=new WorkspaceRoleStoreEntity();link.setRoleId(r.getId());link.setStoreId(id);roleStores.insert(link);}
        history.record("WORKSPACE_ROLE",r.getId(),m,1,p.id()==null?"CREATE":"UPDATE",null,cn.hutool.json.JSONUtil.toJsonStr(roleView(r)));return r.getId();
    }
    public void deleteRole(Long actorId,Long id){var a=actor(actorId,"role:manage");var r=roles.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getId,id).forUpdate());roleScope(a,r);
        if(Boolean.TRUE.equals(r.getBuiltin()))throw new CoolException("内置岗位不可删除");
        var used=userRoles.selectListByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getRoleId,id).forUpdate());
        if(!used.isEmpty())throw new CoolException("该岗位有"+used.size()+"个账号使用，请先调整这些账号的角色");
        var removed=roleView(r);roleMenus.deleteByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId,id));roleStores.deleteByQuery(QueryWrapper.create().eq(WorkspaceRoleStoreEntity::getRoleId,id));roles.deleteById(id);history.record("WORKSPACE_ROLE",id,r.getMerchantId(),1,"DELETE",null,cn.hutool.json.JSONUtil.toJsonStr(removed));
    }
    public List<RoleView> assignableRoles(Long actorId,Long merchantId,String accountType,Long storeId){
        var a=actor(actorId,"account:manage");Long m=merchant(a,merchantId);
        if(a!=null&&"STORE".equals(WorkspaceAuthorization.type(a))){if(!"STORE".equals(accountType))throw new CoolException("门店只能创建本店账号",403);storeId=auth.boundStore(a).getId();}
        final Long targetStore=storeId;var ceiling=a==null?null:auth.grantable(a);
        return roles.selectListByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getAccountType,accountType).eq(BaseSysRoleEntity::getStatus,1)).stream()
            .filter(r->Boolean.TRUE.equals(r.getBuiltin())||m.equals(r.getMerchantId()))
            .filter(r->r.getStoreId()==null||r.getStoreId().equals(targetStore))
            .filter(r->roleStoreIds(r.getId()).isEmpty()||roleStoreIds(r.getId()).contains(targetStore))
            .filter(r->a==null||auth.owner(a)||!"ws_owner".equals(r.getLabel())&&ceiling.containsAll(roleMenuIds(r.getId()))&&ceiling.containsAll(r.getGrantMenuIdList()==null?List.of():r.getGrantMenuIdList()))
             .map(r->roleView(r,0L,null)).toList();
    }
    public List<AccountView> accounts(Long actorId,Filter f){var a=actor(actorId,"account:manage");
        var query=QueryWrapper.create().isNotNull(BaseSysUserEntity::getMerchantId);Long m=a==null?f.merchantId():a.getMerchantId();if(m!=null)query.eq(BaseSysUserEntity::getMerchantId,m);
        var rows=users.selectListByQuery(query.orderBy(BaseSysUserEntity::getId,false));
        return rows.stream().filter(u->f.accountType()==null||f.accountType().equals(WorkspaceAuthorization.type(u)))
            .filter(u->f.keyword()==null||(u.getUsername()+u.getName()).toLowerCase().contains(f.keyword().toLowerCase()))
            .filter(u->accessible(a,u)).map(this::accountView).filter(v->f.storeId()==null||f.storeId().equals(v.storeId())||v.storeIds().contains(f.storeId())).filter(v->f.roleId()==null||f.roleId().equals(v.roleId())).toList();
    }
    private boolean accessible(BaseSysUserEntity a,BaseSysUserEntity u){
        if(a==null)return true;if(!Objects.equals(a.getMerchantId(),u.getMerchantId()))return false;
        if(auth.owner(a))return true;if(auth.owner(u))return false;
        if("STORE".equals(WorkspaceAuthorization.type(a)))return "STORE".equals(WorkspaceAuthorization.type(u))&&Objects.equals(auth.boundStore(a).getId(),auth.boundStore(u).getId());
        return auth.storeIds(a).containsAll(auth.storeIds(u));
    }
    private AccountView accountView(BaseSysUserEntity u){
        var m=merchants.selectOneById(u.getMerchantId());var links=memberships.selectListByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId,u.getId()));
        var s=links.size()==1?stores.selectOneById(links.get(0).getStoreId()):null;
        var assigned=userRoles.selectListByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getUserId,u.getId()));var role=assigned.size()==1?roles.selectOneById(assigned.get(0).getRoleId()):null;
        var personal=grants.selectListByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").eq(WorkspaceGrantEntity::getSubjectId,u.getId()));
        var scope=scopes.selectListByQuery(QueryWrapper.create().eq(WorkspaceUserScopeEntity::getUserId,u.getId())).stream().map(WorkspaceUserScopeEntity::getStoreId).toList();
        return new AccountView(u.getId(),u.getUsername(),u.getName(),WorkspaceAuthorization.type(u),u.getMerchantId(),m==null?"商家资料缺失":m.getName(),s==null?null:s.getId(),s==null?null:s.getName(),role==null?null:role.getId(),role==null?"岗位异常":role.getName(),u.getStatus(),auth.owner(u)?"ALL":u.getStoreScope(),scope,
            personal.stream().filter(g->Boolean.TRUE.equals(g.getAllowed())).map(WorkspaceGrantEntity::getMenuId).toList(),personal.stream().filter(g->!Boolean.TRUE.equals(g.getAllowed())).map(WorkspaceGrantEntity::getMenuId).toList());
    }
    public Long saveAccount(Long actorId,AccountInput p){
        var a=actor(actorId,"account:manage");Long m=merchant(a,p.merchantId());
        var u=p.id()==null?new BaseSysUserEntity():users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId,p.id()).forUpdate());
        if(p.id()!=null && (u==null||!accessible(a,u)||Objects.equals(actorId,p.id())))throw new CoolException("不能管理此账号或修改自己的岗位权限",403);
        if(!Set.of("MERCHANT","STORE").contains(Objects.toString(p.accountType(),"")))throw new CoolException("请选择账号类型");
        if(p.id()!=null && (!Objects.equals(u.getMerchantId(),m)||!WorkspaceAuthorization.type(u).equals(p.accountType())))throw new CoolException("账号类型和商家归属不能直接变更");
        if(a!=null&&"STORE".equals(WorkspaceAuthorization.type(a))&&(!"STORE".equals(p.accountType())||!Objects.equals(auth.boundStore(a).getId(),p.storeId())))throw new CoolException("只能管理本店账号",403);
        if(p.roleId()==null)throw new CoolException("必须选择且只能选择一个岗位");
        var chosen=roles.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getId,p.roleId()).forUpdate());
        if(assignableRoles(actorId,m,p.accountType(),p.storeId()).stream().noneMatch(r->r.id().equals(p.roleId())))throw new CoolException("无权分配此岗位",403);
        if(p.name()==null||p.name().isBlank()||p.name().length()>50)throw new CoolException("姓名必填且不超过50字");
        if("STORE".equals(p.accountType())){
            if(p.storeId()==null)throw new CoolException("请选择唯一所属门店");validStores(a,m,List.of(p.storeId()));
            var target=stores.selectOneByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getId,p.storeId()).forUpdate());if(target==null||!m.equals(target.getMerchantId()))throw new CoolException("门店归属无效");
        }
        Set<Long> scopeIds=ids(p.storeIds());validStores(a,m,scopeIds);
        if(!Set.of("ALL","SELECTED","STORE","SELF").contains(Objects.toString(p.storeScope(),"")))throw new CoolException("请选择有效数据范围");
        if("MERCHANT".equals(p.accountType())&&!Set.of("ALL","SELECTED").contains(p.storeScope()))throw new CoolException("商家账号请选择全部或指定门店");
        if("STORE".equals(p.accountType())&&!Set.of("STORE","SELF").contains(p.storeScope()))throw new CoolException("门店账号只能选择本店或本人记录");
        if(a!=null&&!auth.owner(a)&&"ALL".equals(p.storeScope()))throw new CoolException("只有商家老板能分配全部门店范围",403);
        Set<Long> allow=ids(p.allowMenuIds()),deny=ids(p.denyMenuIds());var valid=auth.catalog().stream().filter(x->"SHARED".equals(x.getAccountType())||p.accountType().equals(x.getAccountType())).map(BaseSysMenuEntity::getId).collect(Collectors.toSet());subset(allow,valid);subset(deny,valid);
        if(a!=null){var prior=p.id()==null?List.<WorkspaceGrantEntity>of():grants.selectListByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").eq(WorkspaceGrantEntity::getSubjectId,p.id()));
            var additions=new HashSet<>(allow);prior.stream().filter(g->Boolean.TRUE.equals(g.getAllowed())).forEach(g->additions.remove(g.getMenuId()));subset(additions,auth.grantable(a));
            var removals=new HashSet<>(deny);prior.stream().filter(g->!Boolean.TRUE.equals(g.getAllowed())).forEach(g->removals.remove(g.getMenuId()));subset(removals,auth.grantable(a));
        }
        String legacy="ws_owner".equals(chosen.getLabel())?"OWNER":"MERCHANT".equals(p.accountType())?"MEMBER":"ws_manager".equals(chosen.getLabel())?"MANAGER":"STAFF";
        if(p.id()==null){
            if(p.username()==null||!p.username().matches("1[0-9]{10}"))throw new CoolException("新账号请输入11位登录手机号");
            if(users.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername,p.username()))>0)throw new CoolException("登录账号已存在");
            u.setUsername(p.username());u.setPhone(p.username());u.setPassword(hash(p.password()));u.setPasswordV(1);
        }else{
            if(!Objects.equals(u.getUsername(),p.username()))throw new CoolException("登录账号不可修改");
            if(p.password()!=null&&!p.password().isBlank())u.setPassword(hash(p.password()));u.setPasswordV(u.getPasswordV()==null?1:u.getPasswordV()+1);
        }
        u.setName(p.name().trim());u.setNickName(p.name().trim());u.setMerchantId(m);u.setMerchantRole(legacy);u.setAccountType(p.accountType());u.setStatus(state(p.status()));u.setStoreScope("OWNER".equals(legacy)?"ALL":p.storeScope());
        if(p.id()==null)users.insert(u);else users.update(u);
        userRoles.deleteByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getUserId,u.getId()));var link=new BaseSysUserRoleEntity();link.setUserId(u.getId());link.setRoleId(p.roleId());userRoles.insert(link);
        memberships.deleteByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId,u.getId()));
        if("STORE".equals(p.accountType())){var b=new MerchantUserStoreEntity();b.setUserId(u.getId());b.setMerchantId(m);b.setStoreId(p.storeId());memberships.insert(b);}
        scopes.deleteByQuery(QueryWrapper.create().eq(WorkspaceUserScopeEntity::getUserId,u.getId()));for(Long id:scopeIds){var s=new WorkspaceUserScopeEntity();s.setUserId(u.getId());s.setStoreId(id);scopes.insert(s);}
        grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").eq(WorkspaceGrantEntity::getSubjectId,u.getId()));
        allow.removeAll(deny);for(Long id:allow)putGrant("USER",u.getId(),id,true);for(Long id:deny)putGrant("USER",u.getId(),id,false);history.record("WORKSPACE_ACCOUNT",u.getId(),m,1,p.id()==null?"CREATE":"UPDATE",null,cn.hutool.json.JSONUtil.toJsonStr(accountView(u)));return u.getId();
    }
    public AccountPreview previewAccount(Long actorId,AccountInput p){
        var a=actor(actorId,"account:manage");Long mid=merchant(a,p.merchantId());
        if(p.id()!=null){var target=users.selectOneById(p.id());if(target==null||!Objects.equals(target.getMerchantId(),mid)||!accessible(a,target))throw new CoolException("无权查看此账号",403);}
        var role=assignableRoles(actorId,mid,p.accountType(),p.storeId()).stream().filter(r->r.id().equals(p.roleId())).findFirst().orElseThrow(()->new CoolException("请选择有权分配的岗位"));
        if("STORE".equals(p.accountType())){if(p.storeId()==null)throw new CoolException("请选择所属门店");var target=stores.selectOneById(p.storeId());if(target==null||!Objects.equals(mid,target.getMerchantId()))throw new CoolException("门店归属无效");validStores(a,mid,List.of(p.storeId()));}
        Set<Long> configured=new HashSet<>(role.menuIds());configured.addAll(ids(p.allowMenuIds()));configured.removeAll(ids(p.denyMenuIds()));
        var ceiling=auth.cap("MERCHANT",mid,Set.of());if("STORE".equals(p.accountType()))ceiling.retainAll(auth.cap("STORE",p.storeId(),ceiling));
        var catalog=auth.catalog().stream().filter(m->"SHARED".equals(m.getAccountType())||p.accountType().equals(m.getAccountType())).filter(m->configured.contains(m.getId())).toList();
        return new AccountPreview(role.name(),catalog.stream().filter(m->ceiling.contains(m.getId())).toList(),catalog.stream().filter(m->!ceiling.contains(m.getId())).toList());
    }
    private String hash(String password){if(password==null||password.length()<6||password.length()>64||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new CoolException("初始密码需6至64位且不超过72字节");return passwords.encode(password);}
    private int state(Integer s){if(s==null||s!=0&&s!=1)throw new CoolException("请选择启用或停用");return s;}
    public void accountState(Long actorId,StateInput p){var a=actor(actorId,"account:manage");var target=users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId,p.id()).forUpdate());if(target==null||target.getMerchantId()==null||!accessible(a,target)||Objects.equals(actorId,p.id()))throw new CoolException("无权调整此账号",403);target.setStatus(state(p.status()));target.setPasswordV(target.getPasswordV()==null?1:target.getPasswordV()+1);users.update(target);history.record("WORKSPACE_ACCOUNT",target.getId(),target.getMerchantId(),1,"STATUS",null,cn.hutool.json.JSONUtil.toJsonStr(accountView(target)));}
    private void putGrant(String type,Long subject,Long menu,boolean allowed){var g=new WorkspaceGrantEntity();g.setSubjectType(type);g.setSubjectId(subject);g.setMenuId(menu);g.setAllowed(allowed);grants.insert(g);}
    public GrantView grant(Long actorId,String type,Long subject){var a=actor(actorId,"grant:manage");
        Set<Long> available;
        if("MERCHANT".equals(type)){if(a!=null)throw new CoolException("只有运营可设置商家功能上限",403);merchant(null,subject);available=auth.catalog().stream().map(BaseSysMenuEntity::getId).collect(Collectors.toSet());}
        else if("STORE".equals(type)){var s=stores.selectOneById(subject);if(s==null)throw new CoolException("门店不存在");merchant(a,s.getMerchantId());if(a!=null)auth.requireStore(actorId,subject);available=auth.cap("MERCHANT",s.getMerchantId(),Set.of());available.retainAll(auth.catalog().stream().filter(menu->"SHARED".equals(menu.getAccountType())||"STORE".equals(menu.getAccountType())).map(BaseSysMenuEntity::getId).collect(Collectors.toSet()));if(a!=null)available.retainAll(auth.grantable(a));}
        else throw new CoolException("不支持的授权主体");
        var selected=auth.cap(type,subject,"STORE".equals(type)?available:Set.of());var visible=new HashSet<>(available);visible.addAll(selected);return new GrantView(type,subject,selected.stream().sorted().toList(),auth.catalog().stream().filter(m->visible.contains(m.getId())).toList());
    }
    private Set<Long> currentCap(String type,Long subject,Collection<Long> fallback){
        var rows=grants.selectListByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,type).eq(WorkspaceGrantEntity::getSubjectId,subject).forUpdate());
        if(rows.isEmpty())return new HashSet<>(fallback);
        return rows.stream().filter(g->Boolean.TRUE.equals(g.getAllowed())&&g.getMenuId()!=null&&g.getMenuId()>0).map(WorkspaceGrantEntity::getMenuId).collect(Collectors.toSet());
    }
    public void saveGrant(Long actorId,GrantInput p){
        grant(actorId,p.subjectType(),p.subjectId()); // Validate identity and subject ownership before taking locks.
        Long mid="MERCHANT".equals(p.subjectType())?p.subjectId():stores.selectOneById(p.subjectId()).getMerchantId();
        merchants.selectOneByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getId,mid).forUpdate());
        if("STORE".equals(p.subjectType()))stores.selectOneByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getId,p.subjectId()).forUpdate());
        Set<Long> allowed="MERCHANT".equals(p.subjectType())?auth.catalog().stream().map(BaseSysMenuEntity::getId).collect(Collectors.toSet()):currentCap("MERCHANT",mid,Set.of());
        if("STORE".equals(p.subjectType()))allowed.retainAll(auth.catalog().stream().filter(menu->"SHARED".equals(menu.getAccountType())||"STORE".equals(menu.getAccountType())).map(BaseSysMenuEntity::getId).collect(Collectors.toSet()));
        if(actorId!=null)allowed.retainAll(auth.grantable(auth.user(actorId)));
        var previous=currentCap(p.subjectType(),p.subjectId(),"STORE".equals(p.subjectType())?allowed:Set.of());
        var selected=ids(p.menuIds());var additions=new HashSet<>(selected);additions.removeAll(previous);subset(additions,allowed);
        grants.deleteByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,p.subjectType()).eq(WorkspaceGrantEntity::getSubjectId,p.subjectId()));
        putGrant(p.subjectType(),p.subjectId(),0L,false);for(Long id:selected)putGrant(p.subjectType(),p.subjectId(),id,true);
        history.record("WORKSPACE_GRANT",p.subjectId(),mid,1,p.subjectType(),null,cn.hutool.json.JSONUtil.toJsonStr(p));
    }
    public MerchantSettings settings(Long actorId,Long merchantId){var a=actor(actorId,"profile:view");var m=merchants.selectOneById(merchant(a,merchantId));return new MerchantSettings(m.getDefaultStoreId(),m.getLicenseNumber());}
    public void personal(Long actorId,PersonalInput p){if(actorId==null)throw new CoolException("请使用运营个人信息入口",403);auth.context(actorId);var u=auth.user(actorId);if(p.name()==null||p.name().isBlank()||p.name().trim().length()>50)throw new CoolException("姓名必填且不超过50字");var change=new BaseSysUserEntity();change.setId(actorId);change.setName(p.name().trim());change.setNickName(p.name().trim());users.update(change);history.record("WORKSPACE_ACCOUNT",actorId,u.getMerchantId(),1,"PROFILE",null,cn.hutool.json.JSONUtil.toJsonStr(p));}
    public void saveSettings(Long actorId,Long merchantId,MerchantSettings p){var a=actor(actorId,"profile:edit");Long mid=merchant(a,merchantId);var m=merchants.selectOneByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getId,mid).forUpdate());
        if(p.defaultStoreId()!=null){validStores(a,mid,List.of(p.defaultStoreId()));var s=stores.selectOneById(p.defaultStoreId());if(s==null||!Integer.valueOf(1).equals(s.getStatus()))throw new CoolException("默认门店必须属于本商家且正在营业");}
        String license=p.licenseNumber()==null?null:p.licenseNumber().trim().toUpperCase(Locale.ROOT);
        if(m.getLicenseNumber()!=null&&!m.getLicenseNumber().isBlank()&&!Objects.equals(m.getLicenseNumber(),license))throw new CoolException("营业执照号已锁定，不能修改");
        if(license!=null&&!license.isBlank()&&!license.matches("[A-Z0-9]{15}|[A-Z0-9]{18}"))throw new CoolException("营业执照号需15或18位字母数字");
        m.setDefaultStoreId(p.defaultStoreId());m.setLicenseNumber(license);merchants.update(m,false);history.record("MERCHANT_SETTINGS",mid,mid,1,"UPDATE",null,cn.hutool.json.JSONUtil.toJsonStr(p));
    }
}
