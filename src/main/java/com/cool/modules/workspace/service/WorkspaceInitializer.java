package com.cool.modules.workspace.service;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.*;
import com.cool.modules.workspace.entity.WorkspaceGrantEntity;
import com.cool.modules.workspace.mapper.WorkspaceGrantMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 幂等建立下级菜单和内置岗位并保留合并前账号功能
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:26:00
 */
@Component @Order(2000) @RequiredArgsConstructor
public class WorkspaceInitializer implements ApplicationRunner {
    private final BaseSysMenuMapper menus;
    private final BaseSysRoleMapper roles;
    private final BaseSysRoleMenuMapper roleMenus;
    private final BaseSysUserMapper users;
    private final BaseSysUserRoleMapper userRoles;
    private final WorkspaceGrantMapper grants;
    private final BaseSysConfMapper configs;
    @Override @Transactional(rollbackFor=Exception.class)
    public void run(ApplicationArguments arguments){
        var migrated = configs.selectOneByQuery(QueryWrapper.create().eq(BaseSysConfEntity::getCKey,"workspace.migration.v1")) != null;
        page("工作台","dashboard","dashboard:view","modules/business/views/dashboard.vue",0,"SHARED");
        var store=page("门店信息","stores","store:view","modules/business/views/stores.vue",1,"SHARED");
        action(store,"编辑门店","store:edit",1);action(store,"删除空门店","store:delete",2);
        var coupon=page("优惠券管理","coupons","coupon:view","modules/business/views/coupons.vue",2,"MERCHANT");
        action(coupon,"编辑及发布优惠券","coupon:edit",1);
        page("购券订单","orders","purchase:view","modules/business/views/orders.vue",3,"MERCHANT");
        page("消费与核销账单","consumption","bill:view","modules/business/views/consumption.vue",4,"SHARED");
        page("验券与核销","redemption","redeem:use","modules/workspace/views/redemption.vue",5,"SHARED");
        page("员工账号","accounts","account:manage","modules/workspace/views/accounts.vue",6,"SHARED");
        page("岗位权限","roles","role:manage","modules/workspace/views/roles.vue",7,"SHARED");
        page("门店功能授权","authorization","grant:manage","modules/workspace/views/grants.vue",8,"MERCHANT");
        var profile=page("商家资料","profile","profile:view","modules/business/views/profile.vue",9,"MERCHANT");
        action(profile,"默认门店及执照资料","profile:edit",1);
        var all=menus.selectListByQuery(QueryWrapper.create().in(BaseSysMenuEntity::getAccountType,List.of("SHARED","MERCHANT","STORE")));
        var owner=role("ws_owner","商家老板","MERCHANT",all,Set.of(),true);
        var manager=role("ws_manager","店长","STORE",all,Set.of("dashboard:view","store:view","bill:view","redeem:use","account:manage","role:manage"),false);
        var staff=role("ws_staff","店员","STORE",all,Set.of("dashboard:view","bill:view","redeem:use"),false);
        for(var u:users.selectListByQuery(QueryWrapper.create().isNotNull(BaseSysUserEntity::getMerchantId))){
            if(!Set.of("OWNER","MANAGER","STAFF").contains(Objects.toString(u.getMerchantRole(),"")))continue;
            var r="OWNER".equals(u.getMerchantRole())?owner:"MANAGER".equals(u.getMerchantRole())?manager:staff;
            if(u.getAccountType()==null){var change=new BaseSysUserEntity();change.setId(u.getId());change.setAccountType(r.getAccountType());change.setStoreScope("OWNER".equals(u.getMerchantRole())?"ALL":"STAFF".equals(u.getMerchantRole())?"SELF":"STORE");users.update(change);}
            if(userRoles.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserRoleEntity::getUserId,u.getId()))==0){var link=new BaseSysUserRoleEntity();link.setUserId(u.getId());link.setRoleId(r.getId());userRoles.insert(link);}
            // 仅首次合并保留旧商家的既有功能；有显式配置的商家绝不因重启重新获得权限。
            if(!migrated && grants.selectCountByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,"MERCHANT").eq(WorkspaceGrantEntity::getSubjectId,u.getMerchantId()))==0){
                grant(u.getMerchantId(),0L,false);for(var m:all)grant(u.getMerchantId(),m.getId(),true);
            }
        }
        if(!migrated){var marker=new BaseSysConfEntity();marker.setCKey("workspace.migration.v1");marker.setCValue("2026-10-30");configs.insert(marker);}
        platformPage("下级账号管理","accounts","modules/workspace/views/accounts.vue",1);
        platformPage("下级岗位权限","roles","modules/workspace/views/roles.vue",2);
        platformPage("商家功能授权","authorization","modules/workspace/views/grants.vue",3);
        if(menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getPerms,"merchant:map:translateGps"))==0){var m=new BaseSysMenuEntity();m.setName("地图定位坐标转换");m.setType(2);m.setPerms("merchant:map:translateGps");m.setIsShow(false);menus.insert(m);}
    }
    private void grant(Long merchant,Long menu,boolean allowed){var g=new WorkspaceGrantEntity();g.setSubjectType("MERCHANT");g.setSubjectId(merchant);g.setMenuId(menu);g.setAllowed(allowed);grants.insert(g);}
    private BaseSysMenuEntity page(String name,String route,String permission,String view,int order,String type){
        String path="/merchant/"+route;var m=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,path).eq(BaseSysMenuEntity::getAccountType,type));
        if(m!=null)return m;m=new BaseSysMenuEntity();m.setName(name);m.setType(1);m.setRouter(path);m.setViewPath(view);m.setPerms(permission);m.setAccountType(type);m.setIsShow(true);m.setKeepAlive(false);m.setOrderNum(order);m.setIcon("icon-menu");menus.insert(m);return m;
    }
    private void action(BaseSysMenuEntity parent,String name,String permission,int order){
        if(menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getParentId,parent.getId()).eq(BaseSysMenuEntity::getPerms,permission))>0)return;
        var m=new BaseSysMenuEntity();m.setParentId(parent.getId());m.setName(name);m.setType(2);m.setPerms(permission);m.setAccountType(parent.getAccountType());m.setOrderNum(order);m.setIsShow(true);menus.insert(m);
    }
    private BaseSysRoleEntity role(String label,String name,String type,List<BaseSysMenuEntity> all,Set<String> defaults,boolean full){
        var existing=roles.selectOneByQuery(QueryWrapper.create().eq(BaseSysRoleEntity::getLabel,label));if(existing!=null)return existing;
        var r=new BaseSysRoleEntity();r.setUserId(1L);r.setName(name);r.setLabel(label);r.setAccountType(type);r.setBuiltin(true);r.setStatus(1);r.setRelevance(0);
        var ids=all.stream().filter(m->("SHARED".equals(m.getAccountType())||type.equals(m.getAccountType())) && (full||defaults.contains(m.getPerms()))).map(BaseSysMenuEntity::getId).toList();
        r.setGrantMenuIdList("ws_staff".equals(label)?List.of():full?all.stream().map(BaseSysMenuEntity::getId).toList():all.stream().filter(m->Set.of("dashboard:view","bill:view","redeem:use","store:view").contains(m.getPerms())).map(BaseSysMenuEntity::getId).toList());
        roles.insert(r);for(Long id:ids){var link=new BaseSysRoleMenuEntity();link.setRoleId(r.getId());link.setMenuId(id);roleMenus.insert(link);}return r;
    }
    private void platformPage(String name,String route,String view,int order){
        String path="/workspace-admin/"+route;if(menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,path))>0)return;
        var m=new BaseSysMenuEntity();m.setName(name);m.setRouter(path);m.setViewPath(view);m.setType(1);m.setPerms("workspace:administration:manage");m.setIsShow(true);m.setKeepAlive(false);m.setOrderNum(60+order);m.setIcon("icon-menu");menus.insert(m);
    }
}
