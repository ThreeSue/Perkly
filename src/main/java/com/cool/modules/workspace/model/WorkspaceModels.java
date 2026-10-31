package com.cool.modules.workspace.model;
import java.util.List;
import com.cool.modules.base.entity.sys.BaseSysMenuEntity;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 三端账号角色菜单及逐级授权的请求响应契约
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:22:00
 */
public final class WorkspaceModels {
    private WorkspaceModels() {}
    public record Context(Long id,String username,String name,String accountType,Long merchantId,String merchantName,
        Long storeId,String storeName,Long roleId,String roleName,String storeScope,List<Long> storeIds,
        Long defaultStoreId,boolean owner,List<String> permissions,List<Long> menuIds,List<Long> grantMenuIds) {}
    public record PermMenu(List<BaseSysMenuEntity> menus,List<String> perms) {}
    public record Option(Long id,String name,Integer status) {}
    public record Filter(Long merchantId,Long storeId,String accountType,String keyword,Integer page,Integer size,Boolean builtin,Long roleId) {}
    public record RoleInput(Long id,Long merchantId,String name,String label,String remark,String accountType,
        Integer status,List<Long> menuIds,List<Long> grantMenuIds,List<Long> applicableStoreIds) {}
    public record RoleView(Long id,String name,String label,String remark,String accountType,Long merchantId,String merchantName,
        Long storeId,Boolean builtin,Integer status,List<Long> menuIds,List<Long> grantMenuIds,List<Long> applicableStoreIds,long accountCount,Long globalAccountCount) {}
    public record AccountInput(Long id,Long merchantId,Long storeId,String username,String name,String password,String accountType,
        Long roleId,Integer status,String storeScope,List<Long> storeIds,List<Long> allowMenuIds,List<Long> denyMenuIds) {}
    public record AccountPreview(String roleName,List<BaseSysMenuEntity> effectiveMenus,List<BaseSysMenuEntity> suppressedMenus) {}
    public record AccountView(Long id,String username,String name,String accountType,Long merchantId,String merchantName,
        Long storeId,String storeName,Long roleId,String roleName,Integer status,String storeScope,List<Long> storeIds,
        List<Long> allowMenuIds,List<Long> denyMenuIds) {}
    public record GrantInput(String subjectType,Long subjectId,List<Long> menuIds) {}
    public record GrantView(String subjectType,Long subjectId,List<Long> menuIds,List<BaseSysMenuEntity> availableMenus) {}
    public record IdInput(Long id,Long merchantId) {}
    public record StateInput(Long id,Integer status) {}
    public record MerchantSettings(Long defaultStoreId,String licenseNumber) {}
    public record PersonalInput(String name) {}
    public record PageResult<T>(List<T> list,Pagination pagination) {}
    public record Pagination(int page,int size,long total) {}
}
