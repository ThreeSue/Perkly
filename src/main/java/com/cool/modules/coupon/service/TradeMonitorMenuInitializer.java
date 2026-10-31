package com.cool.modules.coupon.service;
import com.cool.modules.base.entity.sys.BaseSysMenuEntity;
import com.cool.modules.base.mapper.sys.BaseSysMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 幂等创建只读交易监管菜单且不扩展既有角色授权
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */
@Component @RequiredArgsConstructor
public class TradeMonitorMenuInitializer implements ApplicationRunner {
    private final BaseSysMenuMapper menus;
    @Override @Transactional(rollbackFor=Exception.class)
    public void run(ApplicationArguments args){
        var root=find("/trade",0);
        if(root==null){root=node(null,"交易监管","/trade",null,0,null,55);menus.insertSelective(root);}
        add(root.getId(),"购券订单","/trade/purchases","modules/coupon/views/purchases.vue","coupon:purchaseOrder",1);
        add(root.getId(),"消费与核销账单","/trade/consumption","modules/coupon/views/consumption.vue","coupon:consumptionOrder",2);
    }
    private BaseSysMenuEntity find(String route,int type){return menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter,route).eq(BaseSysMenuEntity::getType,type));}
    private void add(Long root,String name,String route,String view,String permission,int sort){
        if(find(route,1)==null)menus.insertSelective(node(root,name,route,view,1,permission+":page,"+permission+":info",sort));
    }
    private BaseSysMenuEntity node(Long parent,String name,String route,String view,int type,String perms,int sort){
        var n=new BaseSysMenuEntity();n.setParentId(parent);n.setName(name);n.setRouter(route);n.setViewPath(view);n.setType(type);n.setPerms(perms);n.setOrderNum(sort);n.setKeepAlive(true);n.setIsShow(true);n.setIcon("icon-list");return n;
    }
}
