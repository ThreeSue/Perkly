package com.cool.modules.merchant.service;
import cn.hutool.core.lang.Dict;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.MerchantUserStoreEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 实时验证单门店员工归属并返回手机核销端可信身份
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 10:28:14
 */
@Service @RequiredArgsConstructor
public class MerchantStaffService {
    private final MerchantAccountService accounts;
    private final MerchantUserStoreMapper memberships;
    private final MerchantStoreMapper stores;
    private final MerchantInfoMapper merchants;
    private final BaseSysUserMapper users;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.workspace.service.WorkspaceAuthorization workspaceAuthorization;

    public MerchantStoreEntity store(BaseSysUserEntity user) {
        var links = memberships.selectListByQuery(QueryWrapper.create()
            .eq(MerchantUserStoreEntity::getUserId, user.getId()));
        if (links.size() != 1 || !Objects.equals(links.get(0).getMerchantId(), user.getMerchantId()))
            throw new CoolException("账号尚未绑定唯一门店，请联系管理员", 403);
        var store = stores.selectOneById(links.get(0).getStoreId());
        if (store == null || !Objects.equals(store.getMerchantId(), user.getMerchantId())
                || !java.util.Set.of(1,2).contains(store.getStatus()))
            throw new CoolException("所属门店已停用或授权无效", 403);
        return store;
    }
    public Long requireStore(BaseSysUserEntity user, Long requested) {
        // OWNER保留既有商家后台权限，手机核销端只接受已绑定门店的店长和员工。
        if ("OWNER".equals(user.getMerchantRole())) return requested;
        if ("MERCHANT".equals(com.cool.modules.workspace.service.WorkspaceAuthorization.type(user))) {
            if(requested!=null)workspaceAuthorization.requireStore(user.getId(),requested);
            return requested;
        }
        Long actual = store(user).getId();
        if (requested != null && !actual.equals(requested)) throw new CoolException("无权操作其他门店", 403);
        return actual;
    }
    public Object me(Long id) {
        var user = accounts.current(id);
        if (!Set.of("MANAGER", "STAFF").contains(user.getMerchantRole()))
            throw new CoolException("请使用绑定门店的店长或员工账号登录", 403);
        var store = store(user);
        return Dict.create().set("id",id).set("name",user.getName()).set("username",user.getUsername())
            .set("merchantId",user.getMerchantId()).set("merchantName",merchants.selectOneById(user.getMerchantId()).getName())
            .set("storeId",store.getId()).set("storeName",store.getName()).set("role",user.getMerchantRole())
            .set("roleText","MANAGER".equals(user.getMerchantRole()) ? "店长" : "员工")
            .set("canViewStoreRecords",!"SELF".equals(user.getStoreScope()) && (user.getStoreScope()!=null || "MANAGER".equals(user.getMerchantRole())));
    }
    public String operatorName(Long id) {
        var user = users.selectOneById(id);
        return user == null ? "账号已失效" : user.getName();
    }
}
