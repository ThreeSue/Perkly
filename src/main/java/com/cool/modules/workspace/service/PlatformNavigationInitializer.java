package com.cool.modules.workspace.service;

import com.cool.modules.base.entity.sys.BaseSysConfEntity;
import com.cool.modules.base.entity.sys.BaseSysMenuEntity;
import com.cool.modules.base.mapper.sys.BaseSysConfMapper;
import com.cool.modules.base.mapper.sys.BaseSysMenuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 参照教师结构一次性整理运营菜单并保留现有菜单标识及角色授权
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 10:30:58
 */
@Component
@Order(2010)
@RequiredArgsConstructor
public class PlatformNavigationInitializer implements ApplicationRunner {
    private final BaseSysMenuMapper menus;
    private final BaseSysConfMapper configs;
    private final ObjectMapper json;

    public record Group(String router, String legacyName, String name, String icon, int orderNum) {}
    public record Page(String router, String parent, String name, String viewPath, String perms,
                       boolean create, int orderNum, String icon) {}
    public record Plan(String marker, List<Group> groups, List<Page> pages,
                       List<String> hiddenRouters, List<String> visibleDirectories) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments arguments) throws Exception {
        Plan plan;
        try (var stream = new ClassPathResource("cool/data/menu/platform-navigation-20261030.json").getInputStream()) {
            plan = json.readValue(stream, Plan.class);
        }
        if (configs.selectCountByQuery(QueryWrapper.create().eq(BaseSysConfEntity::getCKey, plan.marker())) > 0) return;
        for (var group : plan.groups()) {
            var node = route(group.router());
            if (node == null) node = menus.selectOneByQuery(platform().eq(BaseSysMenuEntity::getType, 0)
                .eq(BaseSysMenuEntity::getName, group.legacyName()));
            if (node == null) {
                node = new BaseSysMenuEntity(); node.setType(0); node.setRouter(group.router());
                node.setName(group.name()); menus.insert(node);
            }
            node.setParentId(null); node.setName(group.name()); node.setRouter(group.router());
            node.setIcon(group.icon()); node.setOrderNum(group.orderNum()); node.setIsShow(true);
            menus.update(node, false);
        }
        for (var page : plan.pages()) {
            var node = route(page.router());
            if (node == null && !page.create()) continue;
            if (node == null) {
                node = new BaseSysMenuEntity(); node.setType(1); node.setRouter(page.router());
                node.setName(page.name()); node.setViewPath(page.viewPath()); node.setPerms(page.perms());
                node.setKeepAlive(false); menus.insert(node);
            }
            if (page.parent() != null) node.setParentId(route(page.parent()).getId());
            if (page.name() != null) node.setName(page.name());
            if (page.viewPath() != null) node.setViewPath(page.viewPath());
            if (page.icon() != null) node.setIcon(page.icon());
            node.setOrderNum(page.orderNum()); node.setIsShow(true); menus.update(node, false);
        }
        for (var node : menus.selectListByQuery(platform())) {
            if (plan.hiddenRouters().contains(node.getRouter())) {
                node.setIsShow(false); menus.update(node);
            }
            if (Integer.valueOf(0).equals(node.getType()) && plan.visibleDirectories().contains(node.getName())) {
                node.setIsShow(true); menus.update(node);
            }
        }
        var marker = new BaseSysConfEntity(); marker.setCKey(plan.marker()); marker.setCValue("2026-10-30");
        configs.insert(marker);
    }

    private QueryWrapper platform() { return QueryWrapper.create().isNull(BaseSysMenuEntity::getAccountType); }
    private BaseSysMenuEntity route(String route) {
        return menus.selectOneByQuery(platform().eq(BaseSysMenuEntity::getRouter, route));
    }
}
