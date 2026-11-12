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
        couponActions(coupon);
        page("购券订单","orders","purchase:view","modules/business/views/orders.vue",3,"MERCHANT");
        page("消费与核销账单","consumption","bill:view","modules/business/views/consumption.vue",4,"SHARED");
        page("验券与核销","redemption","redeem:use","modules/workspace/views/redemption.vue",5,"SHARED");
        page("员工账号","accounts","account:manage","modules/workspace/views/accounts.vue",6,"SHARED");
        page("岗位权限","roles","role:manage","modules/workspace/views/roles.vue",7,"SHARED");
        page("门店功能授权","authorization","grant:manage","modules/workspace/views/grants.vue",8,"MERCHANT");
        var profile=page("商家资料","profile","profile:view","modules/business/views/profile.vue",9,"MERCHANT");
        action(profile,"默认门店及执照资料","profile:edit",1);
        // 仅注册可授权目录，不追加既有商家上限、岗位或个人授权。
        var notice=page("站内通知","notices","notice:view","modules/workspace/views/notices.vue",10,"SHARED");
        action(notice,"发送下级通知","notice:send",1);
        page("储值消费账单","wallet","bill:view","modules/workspace/views/wallet.vue",11,"SHARED");
        var activity=page("支付优惠活动","activities","activity:apply","modules/workspace/views/activities.vue",12,"MERCHANT");
        action(activity,"调整本商家活动参与","activity:participate",1);
        action(activity,"配置活动通知负责人","activity:recipients:manage",2);
        var chatPage=page("门店客服","chats","chat:view","modules/workspace/views/chats.vue",13,"STORE");
        action(chatPage,"客服回复与接管","chat:reply",1);
        action(chatPage,"客服AI与FAQ配置","chat:manage",2);
        materialPermission("素材库查看与选用", "material:view", 14);
        materialPermission("上传与管理素材", "material:manage", 15);
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
        if(menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,"/workspace-admin/notices"))==0){
            var n=new BaseSysMenuEntity();n.setName("站内通知");n.setRouter("/workspace-admin/notices");
            n.setViewPath("modules/workspace/views/notices.vue");n.setType(1);n.setPerms("notice:view");
            n.setIsShow(true);n.setKeepAlive(false);n.setOrderNum(64);n.setIcon("icon-menu");menus.insert(n);
            var send=new BaseSysMenuEntity();send.setParentId(n.getId());send.setName("发送下级通知");
            send.setType(2);send.setPerms("notice:send");send.setIsShow(true);menus.insert(send);
        }
        if(menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getPerms,"merchant:map:translateGps"))==0){var m=new BaseSysMenuEntity();m.setName("地图定位坐标转换");m.setType(2);m.setPerms("merchant:map:translateGps");m.setIsShow(false);menus.insert(m);}
        featurePage("用户反馈","feedback","feedback:manage","modules/workspace/views/feedback.vue",65);
        featurePage("平台储值账务","wallet","wallet:manage","modules/workspace/views/wallet.vue",66);
        featurePage("支付优惠活动","activities","activity:manage","modules/workspace/views/activities.vue",67);
        featurePage("跨商家消费集章","stamps","stamp:manage","modules/workspace/views/stamps.vue",68);
        featurePage("内容风险管控","search-risk","search:risk:view","modules/workspace/views/search-risk.vue",70);
        var searchRiskPage=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,"/workspace-admin/search-risk"));
        if(searchRiskPage!=null)action(searchRiskPage,"管理内容词库与复核","search:risk:manage",1);
        materialPermission("AI生成与完善优惠券封面","ai:image:generate",16);
        featurePage("AI图片控制台","ai-images","ai:manage","modules/ai/views/console.vue",69);
        var aiPage=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,"/workspace-admin/ai-images"));
        action(aiPage,"AI生成与完善优惠券封面","ai:image:generate",1);
        var customerPage=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getViewPath,"modules/user/views/list.vue"));
        if(customerPage!=null){action(customerPage,"查看用户消费画像","profile:read",10);action(customerPage,"更新用户消费画像","profile:update",11);}
    }
    private void materialPermission(String name, String permission, int order) {
        if (menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getPerms, permission)) > 0) return;
        var menu = new BaseSysMenuEntity();
        menu.setName(name);
        menu.setPerms(permission);
        menu.setAccountType("SHARED");
        menu.setType(2);
        menu.setOrderNum(order);
        menu.setIsShow(true);
        menus.insert(menu);
    }

    private void grant(Long merchant,Long menu,boolean allowed){var g=new WorkspaceGrantEntity();g.setSubjectType("MERCHANT");g.setSubjectId(merchant);g.setMenuId(menu);g.setAllowed(allowed);grants.insert(g);}
    private void featurePage(String name,String route,String permission,String view,int order){
        String path="/workspace-admin/"+route;if(menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,path))>0)return;
        var m=new BaseSysMenuEntity();m.setName(name);m.setRouter(path);m.setViewPath(view);m.setType(1);m.setPerms(permission);m.setIsShow(true);m.setKeepAlive(false);m.setOrderNum(order);m.setIcon("icon-menu");menus.insert(m);
    }
    private BaseSysMenuEntity page(String name,String route,String permission,String view,int order,String type){
        String path="/merchant/"+route;var m=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,path).eq(BaseSysMenuEntity::getAccountType,type));
        if(m!=null)return m;m=new BaseSysMenuEntity();m.setName(name);m.setType(1);m.setRouter(path);m.setViewPath(view);m.setPerms(permission);m.setAccountType(type);m.setIsShow(true);m.setKeepAlive(false);m.setOrderNum(order);m.setIcon("icon-menu");menus.insert(m);return m;
    }
    private BaseSysMenuEntity action(BaseSysMenuEntity parent,String name,String permission,int order){
        var m=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getParentId,parent.getId()).eq(BaseSysMenuEntity::getPerms,permission));
        if(m!=null)return m;
        m=new BaseSysMenuEntity();m.setParentId(parent.getId());m.setName(name);m.setType(2);m.setPerms(permission);m.setAccountType(parent.getAccountType());m.setOrderNum(order);m.setIsShow(true);menus.insert(m);return m;
    }
    private void couponActions(BaseSysMenuEntity coupon){
        var legacy=menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getParentId,coupon.getId())
            .eq(BaseSysMenuEntity::getPerms,"coupon:edit"));
        var actions=List.of(
            action(coupon,"新增优惠券","coupon:create",1),
            action(coupon,"提交优惠券审核","coupon:submitAudit",3),
            action(coupon,"撤回优惠券审核","coupon:withdrawAudit",4),
            action(coupon,"上架优惠券","coupon:publish",5),
            action(coupon,"下架优惠券","coupon:unpublish",6),
            action(coupon,"删除优惠券","coupon:delete",7));
        if(legacy==null){
            action(coupon,"修改优惠券","coupon:update",2);
            return;
        }
        Long oldId=legacy.getId();
        var oldUsage=roleMenus.selectListByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getMenuId,oldId));
        var oldGrants=grants.selectListByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getMenuId,oldId));
        var newIds=actions.stream().map(BaseSysMenuEntity::getId).toList();
        for(var node:actions){
            for(var usage:oldUsage){
                if(roleMenus.selectCountByQuery(QueryWrapper.create().eq(BaseSysRoleMenuEntity::getRoleId,usage.getRoleId())
                    .eq(BaseSysRoleMenuEntity::getMenuId,node.getId()))>0)continue;
                var copy=new BaseSysRoleMenuEntity();
                copy.setRoleId(usage.getRoleId());
                copy.setMenuId(node.getId());
                roleMenus.insert(copy);
            }
            for(var grant:oldGrants){
                if(grants.selectCountByQuery(QueryWrapper.create().eq(WorkspaceGrantEntity::getSubjectType,grant.getSubjectType())
                    .eq(WorkspaceGrantEntity::getSubjectId,grant.getSubjectId()).eq(WorkspaceGrantEntity::getMenuId,node.getId()))>0)continue;
                var copy=new WorkspaceGrantEntity();
                copy.setSubjectType(grant.getSubjectType());
                copy.setSubjectId(grant.getSubjectId());
                copy.setMenuId(node.getId());
                copy.setAllowed(grant.getAllowed());
                grants.insert(copy);
            }
        }
        for(var role:roles.selectListByQuery(QueryWrapper.create().isNotNull(BaseSysRoleEntity::getGrantMenuIdList))){
            if(!role.getGrantMenuIdList().contains(oldId))continue;
            var delegation=new LinkedHashSet<>(role.getGrantMenuIdList());
            delegation.addAll(newIds);
            var change=new BaseSysRoleEntity();
            change.setId(role.getId());
            change.setGrantMenuIdList(new ArrayList<>(delegation));
            roles.update(change);
        }
        // 保留旧节点 ID 作为修改权。事务提交后旧权限码消失，后续启动不再重放迁移。
        legacy.setName("修改优惠券");
        legacy.setPerms("coupon:update");
        legacy.setOrderNum(2);
        menus.update(legacy);
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
