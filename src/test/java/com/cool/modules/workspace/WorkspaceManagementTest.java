package com.cool.modules.workspace;
import com.cool.core.exception.CoolException;
import com.cool.modules.approval.service.ApprovalHistoryService;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.workspace.mapper.*;
import com.cool.modules.workspace.model.WorkspaceModels.*;
import com.cool.modules.workspace.service.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证岗位引用保护授权保留账号停用和后端有效权限预览
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 01:31:00
 */
class WorkspaceManagementTest {
    WorkspaceAuthorization auth=mock(WorkspaceAuthorization.class);
    ApprovalHistoryService history=mock(ApprovalHistoryService.class);
    BaseSysUserMapper users=mock(BaseSysUserMapper.class);
    BaseSysRoleMapper roles=mock(BaseSysRoleMapper.class);
    BaseSysMenuMapper menus=mock(BaseSysMenuMapper.class);
    BaseSysRoleMenuMapper roleMenus=mock(BaseSysRoleMenuMapper.class);
    BaseSysUserRoleMapper userRoles=mock(BaseSysUserRoleMapper.class);
    MerchantInfoMapper merchants=mock(MerchantInfoMapper.class);
    MerchantStoreMapper stores=mock(MerchantStoreMapper.class);
    MerchantUserStoreMapper bindings=mock(MerchantUserStoreMapper.class);
    WorkspaceGrantMapper grants=mock(WorkspaceGrantMapper.class);
    WorkspaceUserScopeMapper scopes=mock(WorkspaceUserScopeMapper.class);
    WorkspaceRoleStoreMapper roleStores=mock(WorkspaceRoleStoreMapper.class);
    WorkspaceManagementService service=new WorkspaceManagementService(auth,history,users,roles,menus,roleMenus,userRoles,merchants,stores,bindings,grants,scopes,roleStores);
    BaseSysUserEntity actor; MerchantStoreEntity store;
    @BeforeEach void setup(){actor=new BaseSysUserEntity();actor.setId(10L);actor.setMerchantId(20L);actor.setMerchantRole("OWNER");actor.setAccountType("MERCHANT");when(auth.user(10L)).thenReturn(actor);when(auth.owner(actor)).thenReturn(true);when(auth.storeIds(actor)).thenReturn(List.of(30L));store=new MerchantStoreEntity();store.setId(30L);store.setMerchantId(20L);store.setStatus(1);when(stores.selectOneById(30L)).thenReturn(store);when(stores.selectOneByQuery(any(QueryWrapper.class))).thenReturn(store);}
    BaseSysMenuEntity menu(long id){var m=new BaseSysMenuEntity();m.setId(id);m.setAccountType("SHARED");m.setName("功能"+id);return m;}
    void grantState(Set<Long> original){when(auth.catalog()).thenReturn(List.of(menu(1),menu(2)));when(auth.cap(eq("MERCHANT"),eq(20L),anyCollection())).thenAnswer(i->new HashSet<>(Set.of(1L)));when(auth.cap(eq("STORE"),eq(30L),anyCollection())).thenAnswer(i->new HashSet<>(original));when(auth.grantable(actor)).thenReturn(Set.of(1L));
        when(grants.selectListByQuery(any(QueryWrapper.class))).thenReturn(grantRows(Set.of(1L)),grantRows(original));
    }
    List<com.cool.modules.workspace.entity.WorkspaceGrantEntity> grantRows(Set<Long> ids){var result=new ArrayList<com.cool.modules.workspace.entity.WorkspaceGrantEntity>();var sentinel=new com.cool.modules.workspace.entity.WorkspaceGrantEntity();sentinel.setMenuId(0L);sentinel.setAllowed(false);result.add(sentinel);for(Long id:ids){var g=new com.cool.modules.workspace.entity.WorkspaceGrantEntity();g.setMenuId(id);g.setAllowed(true);result.add(g);}return result;}
    @Test void cannotDeleteRoleUsedByAccounts(){var role=new BaseSysRoleEntity();role.setId(5L);role.setAccountType("STORE");role.setMerchantId(20L);when(roles.selectOneByQuery(any(QueryWrapper.class))).thenReturn(role);when(userRoles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(new BaseSysUserRoleEntity()));assertThrows(CoolException.class,()->service.deleteRole(10L,5L));verify(roles,never()).deleteById(5L);}
    @Test void retainsDormantChildGrantWhileParentIsOff(){grantState(Set.of(1L,2L));service.saveGrant(10L,new GrantInput("STORE",30L,List.of(1L,2L)));verify(grants,times(3)).insert(any(com.cool.modules.workspace.entity.WorkspaceGrantEntity.class));verify(history).record(eq("WORKSPACE_GRANT"),eq(30L),eq(20L),eq(1),eq("STORE"),isNull(),anyString());}
    @Test void revokedDormantPermissionCannotBeAddedBackWithoutParentGrant(){grantState(Set.of(1L));assertThrows(CoolException.class,()->service.saveGrant(10L,new GrantInput("STORE",30L,List.of(1L,2L))));verify(grants,never()).deleteByQuery(any(QueryWrapper.class));}
    @Test void cannotGrantFeaturesOutsideDelegationRange(){grantState(Set.of());assertThrows(CoolException.class,()->service.saveGrant(10L,new GrantInput("STORE",30L,List.of(2L))));verify(grants,never()).insert(any(com.cool.modules.workspace.entity.WorkspaceGrantEntity.class));}
    @Test void employeeLeavingDisablesInsteadOfDeletingAccount(){var u=new BaseSysUserEntity();u.setId(11L);u.setMerchantId(20L);u.setStatus(1);u.setPasswordV(3);u.setName("离职员工");when(users.selectOneByQuery(any(QueryWrapper.class))).thenReturn(u);service.accountState(10L,new StateInput(11L,0));assertEquals(0,u.getStatus());assertEquals(4,u.getPasswordV());verify(users).update(u);verify(users,never()).deleteById(11L);}
    @Test void personalNameUpdateCannotAlterIdentityOrPermissions(){service.personal(10L,new PersonalInput(" 新姓名 "));var saved=org.mockito.ArgumentCaptor.forClass(BaseSysUserEntity.class);verify(users).update(saved.capture());assertEquals("新姓名",saved.getValue().getName());assertNull(saved.getValue().getMerchantId());assertNull(saved.getValue().getMerchantRole());assertNull(saved.getValue().getAccountType());assertNull(saved.getValue().getPassword());assertNull(saved.getValue().getStoreScope());}
    @Test void previewShowsOnlyEffectivePermissionsAndSuppressedOnes(){grantState(Set.of(1L,2L));var r=new BaseSysRoleEntity();r.setId(5L);r.setName("店员");r.setLabel("ws_staff");r.setAccountType("STORE");r.setStatus(1);r.setBuiltin(true);when(roles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(r));var a=new BaseSysRoleMenuEntity();a.setMenuId(1L);var b=new BaseSysRoleMenuEntity();b.setMenuId(2L);when(roleMenus.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(a,b));var p=new AccountInput(null,20L,30L,"13800000000","测试",null,"STORE",5L,1,"SELF",List.of(),List.of(),List.of());var preview=service.previewAccount(10L,p);assertEquals(List.of(1L),preview.effectiveMenus().stream().map(BaseSysMenuEntity::getId).toList());assertEquals(List.of(2L),preview.suppressedMenus().stream().map(BaseSysMenuEntity::getId).toList());verifyNoInteractions(history);}

    private BaseSysUserRoleEntity assigned(long userId,long roleId){var link=new BaseSysUserRoleEntity();link.setUserId(userId);link.setRoleId(roleId);return link;}
    private BaseSysUserEntity account(long id,long merchantId,String type){var u=new BaseSysUserEntity();u.setId(id);u.setMerchantId(merchantId);u.setAccountType(type);return u;}
    private BaseSysRoleEntity sharedRole(){var r=new BaseSysRoleEntity();r.setId(5L);r.setName("店员");r.setLabel("ws_staff");r.setAccountType("STORE");r.setBuiltin(true);return r;}
    @Test void roleCountsAreRestrictedToMerchantAndDoNotExposeGlobalCounts(){
        var role=sharedRole();when(roles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(role));
        when(users.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(account(11,20,"STORE"),account(12,20,"STORE")));
        when(userRoles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(assigned(11,5),assigned(12,5)));
        var result=service.roleList(10L,new Filter(null,null,null,null,null,null,null,null));
        assertEquals(2,result.get(0).accountCount());assertNull(result.get(0).globalAccountCount());
    }
    @Test void storeRoleCountsOnlyIncludeAccountsAccessibleInThatStore(){
        actor.setAccountType("STORE");actor.setMerchantRole("MANAGER");when(auth.owner(actor)).thenReturn(false);when(auth.boundStore(actor)).thenReturn(store);
        var same=account(11,20,"STORE");var other=account(12,20,"STORE");var otherStore=new MerchantStoreEntity();otherStore.setId(31L);when(auth.boundStore(same)).thenReturn(store);when(auth.boundStore(other)).thenReturn(otherStore);
        when(roles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(sharedRole()));when(users.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(same,other));
        when(userRoles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(assigned(11,5)));
        var result=service.roleList(10L,new Filter(null,null,null,null,null,null,null,null));assertEquals(1,result.get(0).accountCount());assertNull(result.get(0).globalAccountCount());
    }
    @Test void platformCanCalculateSeparateSelectedMerchantAndGlobalCounts(){
        when(users.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(account(11,20,"STORE")),List.of(account(11,20,"STORE"),account(12,21,"STORE")));
        when(userRoles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(assigned(11,5)),List.of(assigned(11,5),assigned(12,5)));
        Map<Long,Long> local=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"roleAccountCounts",null,20L);
        Map<Long,Long> global=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"roleAccountCounts",null,null);
        var result=(RoleView)org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"roleView",sharedRole(),local.get(5L),global.get(5L));
        assertEquals(1,result.accountCount());assertEquals(2L,result.globalAccountCount());
    }
}
