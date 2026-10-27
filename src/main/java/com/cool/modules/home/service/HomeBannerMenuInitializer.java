package com.cool.modules.home.service;

import com.cool.modules.base.entity.sys.BaseSysMenuEntity;
import com.cool.modules.base.mapper.sys.BaseSysMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 幂等建立首页运营广告菜单和操作权限且不修改现有角色授权
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:15:34
 */
@Component
@RequiredArgsConstructor
public class HomeBannerMenuInitializer implements ApplicationRunner {
    private final BaseSysMenuMapper menus;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        var root = menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter, "/home").eq(BaseSysMenuEntity::getType, 0));
        if (root == null) { root = menu(null, "首页运营", "/home", null, 0, null); menus.insertSelective(root); }
        var page = menus.selectOneByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getRouter, "/home/banner").eq(BaseSysMenuEntity::getType, 1));
        if (page == null) {
            page = menu(root.getId(), "首页广告", "/home/banner", "modules/home/views/banner.vue", 1,
                    "home:banner:page,home:banner:info,home:banner:targetOptions");
            menus.insertSelective(page);
        }
        String[][] actions = {{"add", "新增广告"}, {"update", "编辑广告"}, {"status", "广告上下架"}, {"delete", "删除广告"}};
        for (String[] action : actions) {
            String permission = "home:banner:" + action[0];
            if (menus.selectCountByQuery(QueryWrapper.create().eq(BaseSysMenuEntity::getParentId, page.getId()).eq(BaseSysMenuEntity::getPerms, permission)) == 0)
                menus.insertSelective(menu(page.getId(), action[1], null, null, 2, permission));
        }
    }
    private BaseSysMenuEntity menu(Long parent, String name, String router, String view, int type, String perms) {
        var menu = new BaseSysMenuEntity(); menu.setParentId(parent); menu.setName(name); menu.setRouter(router);
        menu.setViewPath(view); menu.setType(type); menu.setPerms(perms); menu.setOrderNum(80);
        menu.setKeepAlive(true); menu.setIsShow(true); menu.setIcon("icon-menu"); return menu;
    }
}
