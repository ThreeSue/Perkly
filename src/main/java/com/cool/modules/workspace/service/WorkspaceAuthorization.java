package com.cool.modules.workspace.service;

import com.cool.core.exception.CoolException;
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
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 实时计算三端单角色个人调整上级功能上限及门店数据范围
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:24:00
 */
@Service @RequiredArgsConstructor
public class WorkspaceAuthorization {
    private final BaseSysUserMapper users;
    private final BaseSysRoleMapper roles;
    private final BaseSysUserRoleMapper userRoles;
    private final BaseSysRoleMenuMapper roleMenus;
    private final BaseSysMenuMapper menus;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final MerchantUserStoreMapper memberships;
    private final WorkspaceGrantMapper grants;
    private final WorkspaceUserScopeMapper scopes;
    private final WorkspaceRoleStoreMapper roleStores;

    public static String type(BaseSysUserEntity user) {
        if(user.getMerchantId()==null)return "PLATFORM";
        if(Set.of("MERCHANT","STORE").contains(Objects.toString(user.getAccountType(),"")))return user.getAccountType();
        return "OWNER".equals(user.getMerchantRole()) ? "MERCHANT":"STORE";
    }
    public boolean owner(BaseSysUserEntity user){return "OWNER".equals(user.getMerchantRole()) && "MERCHANT".equals(type(user));}
    public BaseSysUserEntity user(Long id){
        var u=id==null?null:users.selectOneById(id);
        if(u==null || !Integer.valueOf(1).equals(u.getStatus()) || u.getMerchantId()==null)throw new CoolException("账号已停用或身份无效",403);
        var m=merchants.selectOneById(u.getMerchantId());
        if(m==null || !Integer.valueOf(1).equals(m.getStatus()))throw new CoolException("商家已停用",403);
        return u;
    }
    public BaseSysRoleEntity role(BaseSysUserEntity user){
        var links=userRoles.selectListByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getUserId,user.getId()));
        if(links.size()>1)throw new CoolException("账号存在多个角色，请联系上级调整",403);
        BaseSysRoleEntity role;
        if(links.isEmpty()){
            String label=owner(user)?"ws_owner":"MANAGER".equals(user.getMerchantRole())?"ws_manager":"ws_staff";
            role=roles.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getLabel,label));
        }else role=roles.selectOneById(links.get(0).getRoleId());
        if(role==null || !Integer.valueOf(1).equals(role.getStatus()) || !type(user).equals(role.getAccountType())
            || (!Boolean.TRUE.equals(role.getBuiltin()) && !Objects.equals(user.getMerchantId(),role.getMerchantId())))
            throw new CoolException("岗位已失效或不属于当前账号",403);
        if("STORE".equals(type(user))){
            Long storeId=boundStore(user).getId();
            if(role.getStoreId()!=null && !role.getStoreId().equals(storeId))throw new CoolException("岗位不适用于本门店",403);
            var allowed=roleStores.selectListByQuery(QueryWrapper.create().eq(WorkspaceRoleStoreEntity::getRoleId,role.getId()));
            if(!allowed.isEmpty() && allowed.stream().noneMatch(r->storeId.equals(r.getStoreId())))throw new CoolException("岗位不适用于本门店",403);
        }
        return role;
    }
    public MerchantStoreEntity boundStore(BaseSysUserEntity user){
        var links=memberships.selectListByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId,user.getId()));
        if(links.size()!=1 || !Objects.equals(user.getMerchantId(),links.get(0).getMerchantId()))throw new CoolException("账号未绑定唯一有效门店",403);
        var s=stores.selectOneById(links.get(0).getStoreId());
        if(s==null || !Objects.equals(user.getMerchantId(),s.getMerchantId()))throw new CoolException("门店归属无效",403);
        return s;
    }
    public List<BaseSysMenuEntity> catalog(){
        return menus.selectListByQuery(QueryWrapper.create().in(BaseSysMenuEntity::getAccountType,List.of("MERCHANT","STORE","SHARED"))
            .orderBy(BaseSysMenuEntity::getOrderNum,true).orderBy(BaseSysMenuEntity::getId,true));
    }
    public Set<Long> cap(String subject,Long id,Collection<Long> fallback){
        var rows=grants.selectListByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,subject).eq(WorkspaceGrantEntity::getSubjectId,id));
        if(rows.isEmpty())return new HashSet<>(fallback);
        return rows.stream().filter(g->Boolean.TRUE.equals(g.getAllowed()) && g.getMenuId()!=null && g.getMenuId()>0)
            .map(WorkspaceGrantEntity::getMenuId).collect(Collectors.toSet());
    }
    public Set<Long> ceiling(BaseSysUserEntity u){
        // 未被平台开通的商家不默认获得任何业务功能；旧商家由迁移初始化显式保留原能力。
        Set<Long> result=cap("MERCHANT",u.getMerchantId(),Set.of());
        if("STORE".equals(type(u)))result.retainAll(cap("STORE",boundStore(u).getId(),result));
        Set<Long> valid=catalog().stream().filter(m->"SHARED".equals(m.getAccountType()) || type(u).equals(m.getAccountType()))
            .map(BaseSysMenuEntity::getId).collect(Collectors.toSet());
        result.retainAll(valid);return result;
    }
    public Set<Long> effective(BaseSysUserEntity u){
        var r=role(u);
        Set<Long> selected=roleMenus.selectListByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId,r.getId()))
            .stream().map(BaseSysRoleMenuEntity::getMenuId).collect(Collectors.toSet());
        var personal=grants.selectListByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"USER").eq(WorkspaceGrantEntity::getSubjectId,u.getId()));
        personal.stream().filter(g->Boolean.TRUE.equals(g.getAllowed())).map(WorkspaceGrantEntity::getMenuId).forEach(selected::add);
        personal.stream().filter(g->!Boolean.TRUE.equals(g.getAllowed())).map(WorkspaceGrantEntity::getMenuId).forEach(selected::remove);
        selected.retainAll(ceiling(u));return selected;
    }
    public Set<Long> grantable(BaseSysUserEntity u){
        if(owner(u))return ceiling(u);
        var r=role(u);var ids=new HashSet<>(r.getGrantMenuIdList()==null?List.<Long>of():r.getGrantMenuIdList());
        ids.retainAll(ceiling(u));return ids;
    }
    public Set<String> permissions(Long userId){
        var u=user(userId);var ids=effective(u);Set<String> result=new HashSet<>();
        catalog().stream().filter(m->ids.contains(m.getId()) && m.getPerms()!=null).forEach(m->result.addAll(Arrays.asList(m.getPerms().split(","))));
        return result;
    }
    public void require(Long userId,String permission){if(!permissions(userId).contains(permission))throw new CoolException("没有此操作权限",403);}
    public List<Long> storeIds(BaseSysUserEntity u){
        if("STORE".equals(type(u)))return List.of(boundStore(u).getId());
        var query=QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,u.getMerchantId());
        List<Long> all=stores.selectListByQuery(query).stream().map(MerchantStoreEntity::getId).toList();
        if(owner(u) || "ALL".equals(u.getStoreScope()))return all;
        var selected=scopes.selectListByQuery(QueryWrapper.create().eq(WorkspaceUserScopeEntity::getUserId,u.getId()))
            .stream().map(WorkspaceUserScopeEntity::getStoreId).collect(Collectors.toSet());
        return all.stream().filter(selected::contains).toList();
    }
    public void requireStore(Long userId,Long storeId){
        if(storeId==null || !storeIds(user(userId)).contains(storeId))throw new CoolException("无权访问该门店",403);
    }
    public boolean ownRecordsOnly(BaseSysUserEntity u){return "SELF".equals(u.getStoreScope()) || (u.getStoreScope()==null && "STAFF".equals(u.getMerchantRole()));}
    public Context context(Long id){
        var u=user(id);var r=role(u);var m=merchants.selectOneById(u.getMerchantId());
        MerchantStoreEntity s="STORE".equals(type(u))?boundStore(u):null;
        if(s!=null && !Set.of(1,2).contains(s.getStatus()))throw new CoolException("所属门店已禁用或关闭",403);
        List<Long> allowed=storeIds(u);Long def=m.getDefaultStoreId();
        if(def!=null && !allowed.contains(def))def=null;
        if(def!=null){var d=stores.selectOneById(def);if(d==null || !Integer.valueOf(1).equals(d.getStatus()))def=null;}
        return new Context(id,u.getUsername(),u.getName(),type(u),u.getMerchantId(),m.getName(),s==null?null:s.getId(),s==null?null:s.getName(),
            r.getId(),r.getName(),owner(u)?"ALL":u.getStoreScope(),allowed,def,owner(u),permissions(id).stream().sorted().toList(),effective(u).stream().sorted().toList(),grantable(u).stream().sorted().toList());
    }
    public PermMenu permmenu(Long id){
        var u=user(id);Set<Long> selected=effective(u);var list=catalog();
        // 按钮有权但页面没有权时，不自动扩大页面权限；配置界面负责明确勾选。
        List<BaseSysMenuEntity> result=list.stream().filter(m->selected.contains(m.getId())).toList();
        for(var menu:result){
            if(menu.getRouter()!=null && "STORE".equals(type(u)))menu.setRouter(menu.getRouter().replaceFirst("^/merchant/","/store/"));
        }
        return new PermMenu(result,permissions(id).stream().sorted().toList());
    }
    public void checkRoute(Long id,String route){
        if(Set.of("/merchant/auth/logout","/merchant/auth/changePassword","/merchant/staff/me","/merchant/workspace/me",
            "/merchant/governance/me","/merchant/governance/permmenu").contains(route)){context(id);return;}
        String permission=null;
        if(route.equals("/merchant/workspace/category/options")||route.equals("/merchant/workspace/store/options")){context(id);var p=permissions(id);if(!p.contains("store:view")&&!p.contains("store:edit")&&!p.contains("coupon:view"))throw new CoolException("没有选项读取权限",403);return;}
        if(route.equals("/merchant/consumption/info")){context(id);var p=permissions(id);if(!p.contains("bill:view")&&!p.contains("redeem:use"))throw new CoolException("没有账单查看权限",403);return;}
        if(route.startsWith("/merchant/governance/"))return; // 专用控制器逐操作验证，不以此放行到其他资源。
        if(route.startsWith("/merchant/consumption/"))permission=route.endsWith("/page")||route.endsWith("/info")?"bill:view":"redeem:use";
        else if(route.startsWith("/merchant/redemption/"))permission=route.endsWith("/page")?"bill:view":"redeem:use";
        else if(route.startsWith("/merchant/workspace/storeAccount/"))permission="account:manage";
        else if(route.startsWith("/merchant/workspace/store/"))permission=route.endsWith("/delete")?"store:delete":route.endsWith("/save")||route.endsWith("/update")?"store:edit":"store:view";
        else if(route.startsWith("/merchant/workspace/map/"))permission="store:view";
        else if(route.startsWith("/merchant/workspace/coupon/"))permission=route.endsWith("/page")||route.endsWith("/info")?"coupon:view":"coupon:edit";
        else if(route.startsWith("/merchant/workspace/order/"))permission="purchase:view";
        else if(route.equals("/merchant/workspace/profile"))permission="profile:view";
        else if(route.equals("/merchant/workspace/category/options") || route.equals("/merchant/workspace/product/options"))permission="coupon:view";
        else if(route.equals("/merchant/workspace/image/upload")){
            var p=permissions(id);if(!p.contains("store:edit")&&!p.contains("coupon:edit")&&!p.contains("profile:edit"))throw new CoolException("没有图片上传权限",403);return;
        }
        // New business actions can use their exact route permission in the platform catalog.
        if(permission==null && route.startsWith("/merchant/"))permission=route.substring("/merchant/".length()).replace('/',':');
        if(permission==null)throw new CoolException("该账号没有此接口权限",403);
        require(id,permission);context(id);
    }
}
