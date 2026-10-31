package com.cool.modules.workspace;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.*;
import com.cool.modules.base.mapper.sys.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.workspace.entity.*;
import com.cool.modules.workspace.mapper.*;
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
 * @Description: 验证单角色个人权限逐级上限及门店隔离且不写真实数据库
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:58:00
 */
class WorkspaceAuthorizationTest {
    BaseSysUserMapper users=mock(BaseSysUserMapper.class);
    BaseSysRoleMapper roles=mock(BaseSysRoleMapper.class);
    BaseSysUserRoleMapper userRoles=mock(BaseSysUserRoleMapper.class);
    BaseSysRoleMenuMapper roleMenus=mock(BaseSysRoleMenuMapper.class);
    BaseSysMenuMapper menus=mock(BaseSysMenuMapper.class);
    MerchantInfoMapper merchants=mock(MerchantInfoMapper.class);
    MerchantStoreMapper stores=mock(MerchantStoreMapper.class);
    MerchantUserStoreMapper bindings=mock(MerchantUserStoreMapper.class);
    WorkspaceGrantMapper grants=mock(WorkspaceGrantMapper.class);
    WorkspaceUserScopeMapper scopes=mock(WorkspaceUserScopeMapper.class);
    WorkspaceRoleStoreMapper roleStores=mock(WorkspaceRoleStoreMapper.class);
    WorkspaceAuthorization auth=spy(new WorkspaceAuthorization(users,roles,userRoles,roleMenus,menus,merchants,stores,bindings,grants,scopes,roleStores));
    BaseSysUserEntity user;
    @BeforeEach void setup(){user=new BaseSysUserEntity();user.setId(10L);user.setMerchantId(20L);user.setAccountType("MERCHANT");user.setMerchantRole("MEMBER");user.setStoreScope("SELECTED");user.setStatus(1);}
    BaseSysMenuEntity menu(long id,String type){var m=new BaseSysMenuEntity();m.setId(id);m.setAccountType(type);return m;}
    WorkspaceGrantEntity grant(long id,boolean allow){var g=new WorkspaceGrantEntity();g.setMenuId(id);g.setAllowed(allow);return g;}
    MerchantStoreEntity store(long id){var s=new MerchantStoreEntity();s.setId(id);s.setMerchantId(20L);s.setStatus(1);return s;}
    @Test void legacyIdentityIsDerivedAndExplicitIdentityIsRespected(){user.setAccountType(null);user.setMerchantRole("OWNER");assertEquals("MERCHANT",WorkspaceAuthorization.type(user));user.setMerchantRole("STAFF");assertEquals("STORE",WorkspaceAuthorization.type(user));user.setMerchantId(null);assertEquals("PLATFORM",WorkspaceAuthorization.type(user));}
    @Test void noMerchantGrantFailsClosed(){when(grants.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());doReturn(List.of(menu(1,"SHARED"))).when(auth).catalog();assertTrue(auth.ceiling(user).isEmpty());}
    @Test void explicitEmptyCapDoesNotInheritFallback(){when(grants.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(grant(0,false)));assertTrue(auth.cap("STORE",30L,Set.of(1L,2L)).isEmpty());}
    @Test void unconfiguredStoreInheritsItsMerchantCap(){when(grants.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());assertEquals(Set.of(1L,2L),auth.cap("STORE",30L,Set.of(1L,2L)));}
    @Test void parentRevocationAndRestorationPreserveChildSelection(){user.setAccountType("STORE");doReturn(store(30)).when(auth).boundStore(user);doReturn(List.of(menu(1,"SHARED"),menu(2,"SHARED"))).when(auth).catalog();doReturn(new HashSet<>(Set.of(1L))).when(auth).cap(eq("MERCHANT"),eq(20L),anyCollection());doReturn(new HashSet<>(Set.of(1L,2L))).when(auth).cap(eq("STORE"),eq(30L),anyCollection());assertEquals(Set.of(1L),auth.ceiling(user));doReturn(new HashSet<>(Set.of(1L,2L))).when(auth).cap(eq("MERCHANT"),eq(20L),anyCollection());assertEquals(Set.of(1L,2L),auth.ceiling(user));verify(grants,never()).deleteByQuery(any(QueryWrapper.class));}
    @Test void anotherIdentityCatalogNeverLeaksIntoStoreCap(){user.setAccountType("STORE");doReturn(store(30)).when(auth).boundStore(user);doReturn(List.of(menu(1,"SHARED"),menu(2,"MERCHANT"))).when(auth).catalog();doReturn(new HashSet<>(Set.of(1L,2L))).when(auth).cap(anyString(),anyLong(),anyCollection());assertEquals(Set.of(1L),auth.ceiling(user));}
    @Test void personalDenyWinsAndCannotExceedParentCap(){var r=new BaseSysRoleEntity();r.setId(4L);doReturn(r).when(auth).role(user);var link=new BaseSysRoleMenuEntity();link.setMenuId(1L);when(roleMenus.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(link));when(grants.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(grant(1,false),grant(2,true),grant(3,true)));doReturn(Set.of(1L,2L)).when(auth).ceiling(user);assertEquals(Set.of(2L),auth.effective(user));}
    @Test void disabledAccountCannotEnter(){when(users.selectOneById(10L)).thenReturn(user);user.setStatus(0);assertThrows(CoolException.class,()->auth.user(10L));}
    @Test void multipleRolesAreRejected(){when(userRoles.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(new BaseSysUserRoleEntity(),new BaseSysUserRoleEntity()));assertThrows(CoolException.class,()->auth.role(user));}
    @Test void storeIdentityRequiresExactlyOneBinding(){when(bindings.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());assertThrows(CoolException.class,()->auth.boundStore(user));}
    @Test void foreignMerchantBindingIsRejected(){var b=new MerchantUserStoreEntity();b.setMerchantId(99L);b.setStoreId(30L);when(bindings.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(b));assertThrows(CoolException.class,()->auth.boundStore(user));}
    @Test void selectedStoresIgnoreStaleOrForeignReferences(){when(stores.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(store(30),store(31)));var s=new WorkspaceUserScopeEntity();s.setStoreId(31L);var foreign=new WorkspaceUserScopeEntity();foreign.setStoreId(99L);when(scopes.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(s,foreign));assertEquals(List.of(31L),auth.storeIds(user));}
    @Test void storeAccountsNeverAggregateOtherStores(){user.setAccountType("STORE");doReturn(store(30)).when(auth).boundStore(user);assertEquals(List.of(30L),auth.storeIds(user));verifyNoInteractions(stores,scopes);}
    @Test void delegationIsBoundedByParentGrant(){var r=new BaseSysRoleEntity();r.setGrantMenuIdList(List.of(1L,2L));doReturn(r).when(auth).role(user);doReturn(Set.of(1L)).when(auth).ceiling(user);assertEquals(Set.of(1L),auth.grantable(user));}
    @Test void staffPersonalHistorySettingIsIndependentOfRoleName(){user.setAccountType("STORE");user.setMerchantRole("STAFF");user.setStoreScope("STORE");assertFalse(auth.ownRecordsOnly(user));user.setStoreScope("SELF");assertTrue(auth.ownRecordsOnly(user));}
    @Test void newCatalogActionCanAuthorizeItsExactBusinessRoute(){doReturn(Set.of("custom:page")).when(auth).permissions(10L);doReturn(null).when(auth).context(10L);assertDoesNotThrow(()->auth.checkRoute(10L,"/merchant/custom/page"));}
    @Test void newUnassignedActionIsDenied(){doReturn(Set.of("custom:page")).when(auth).permissions(10L);assertThrows(CoolException.class,()->auth.checkRoute(10L,"/merchant/custom/delete"));}
}
