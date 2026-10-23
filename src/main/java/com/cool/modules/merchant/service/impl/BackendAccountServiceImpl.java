package com.cool.modules.merchant.service.impl;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.controller.admin.params.BackendAccountParam.*;
import com.cool.modules.merchant.resp.BackendAccountResp.*;
import com.cool.modules.merchant.service.BackendAccountService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按商家门店查询后台账号并安全维护商家管理员且撤销旧身份
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 22:10:23
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class BackendAccountServiceImpl implements BackendAccountService {
    private final BaseSysUserMapper users;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final MerchantUserStoreMapper links;
    private final MerchantCategoryMapper categories;
    private static final Set<String> ROLES = Set.of("OWNER", "MANAGER", "STAFF");

    @Override
    @Transactional(readOnly = true)
    public List<Group> groups() {
        var storeList = stores.selectListByQuery(QueryWrapper.create().orderBy(MerchantStoreEntity::getId, true));
        var grouped = storeList.stream().collect(Collectors.groupingBy(MerchantStoreEntity::getMerchantId));
        return merchants.selectListByQuery(QueryWrapper.create().orderBy(MerchantInfoEntity::getId, true)).stream()
                .map(m -> new Group(m.getId(), m.getName(), grouped.getOrDefault(m.getId(), List.of()).stream()
                        .map(s -> new Store(s.getId(), s.getName())).toList(), m.getCategoryId())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPage page(PageInput input) {
        int page = input.page() == null ? 1 : input.page(), size = input.size() == null ? 20 : input.size();
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new CoolException("分页参数不正确");
        if (input.role() != null && !ROLES.contains(input.role())) throw new CoolException("账号身份不正确");
        if (input.status() != null && input.status() != 0 && input.status() != 1) throw new CoolException("账号状态不正确");
        String word = input.keyWord() == null ? "" : input.keyWord().trim();
        if (word.length() > 100) throw new CoolException("搜索内容不能超过100字");
        var query = QueryWrapper.create().isNotNull(BaseSysUserEntity::getMerchantId)
                .in(BaseSysUserEntity::getMerchantRole, ROLES);
        if (input.merchantId() != null) query.eq(BaseSysUserEntity::getMerchantId, input.merchantId());
        if (input.role() != null) query.eq(BaseSysUserEntity::getMerchantRole, input.role());
        if (input.status() != null) query.eq(BaseSysUserEntity::getStatus, input.status());
        if (input.categoryId() != null) {
            var categoryList = categories.selectListByQuery(QueryWrapper.create());
            if (categoryList.stream().noneMatch(c -> c.getId().equals(input.categoryId()))) throw new CoolException("商家分类不存在");
            Set<Long> categoryIds = new HashSet<>(); categoryIds.add(input.categoryId());
            boolean changed;
            do {
                changed = false;
                for (var category : categoryList) {
                    if (categoryIds.contains(category.getParentId()) && categoryIds.add(category.getId())) changed = true;
                }
            } while (changed);
            var merchantIds = merchants.selectListByQuery(QueryWrapper.create().in(MerchantInfoEntity::getCategoryId, categoryIds))
                    .stream().map(MerchantInfoEntity::getId).toList();
            if (merchantIds.isEmpty()) return new AccountPage(List.of(), new Pagination(page, size, 0));
            query.in(BaseSysUserEntity::getMerchantId, merchantIds);
        }
        if (!word.isEmpty()) query.and((QueryWrapper q) -> {
            q.like(BaseSysUserEntity::getUsername, word).or((QueryWrapper name) -> {
                name.like(BaseSysUserEntity::getName, word);
            });
        });
        if (input.storeId() != null) {
            var store = stores.selectOneById(input.storeId());
            if (store == null || (input.merchantId() != null && !input.merchantId().equals(store.getMerchantId())))
                throw new CoolException("门店不存在或不属于所选商家");
            var ids = links.selectListByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getStoreId, store.getId())
                    .eq(MerchantUserStoreEntity::getMerchantId, store.getMerchantId())).stream().map(MerchantUserStoreEntity::getUserId).toList();
            if (ids.isEmpty()) return new AccountPage(List.of(), new Pagination(page, size, 0));
            query.in(BaseSysUserEntity::getId, ids).eq(BaseSysUserEntity::getMerchantId, store.getMerchantId());
        }
        var result = users.paginate(page, size, query.orderBy(BaseSysUserEntity::getId, false));
        if (result.getRecords().isEmpty()) return new AccountPage(List.of(), new Pagination(page, size, result.getTotalRow()));
        var merchantIds = result.getRecords().stream().map(BaseSysUserEntity::getMerchantId).distinct().toList();
        var merchantMap = merchants.selectListByQuery(QueryWrapper.create().in(MerchantInfoEntity::getId, merchantIds))
                .stream().collect(Collectors.toMap(MerchantInfoEntity::getId, Function.identity()));
        var userIds = result.getRecords().stream().map(BaseSysUserEntity::getId).toList();
        var bindings = links.selectListByQuery(QueryWrapper.create().in(MerchantUserStoreEntity::getUserId, userIds))
                .stream().collect(Collectors.groupingBy(MerchantUserStoreEntity::getUserId));
        var storeIds = bindings.values().stream().flatMap(Collection::stream).map(MerchantUserStoreEntity::getStoreId).distinct().toList();
        Map<Long, MerchantStoreEntity> storeMap = storeIds.isEmpty() ? Map.of() : stores.selectListByQuery(QueryWrapper.create()
                .in(MerchantStoreEntity::getId, storeIds)).stream().collect(Collectors.toMap(MerchantStoreEntity::getId, Function.identity()));
        var rows = result.getRecords().stream().map(u -> {
            var merchant = merchantMap.get(u.getMerchantId());
            var membership = bindings.getOrDefault(u.getId(), List.of());
            var link = membership.size() == 1 ? membership.get(0) : null;
            var store = link == null ? null : storeMap.get(link.getStoreId());
            boolean valid = merchant != null && ("OWNER".equals(u.getMerchantRole()) || (store != null
                    && u.getMerchantId().equals(link.getMerchantId()) && u.getMerchantId().equals(store.getMerchantId())));
            return new Account(u.getId(), u.getUsername(), u.getName(), u.getMerchantRole(), u.getStatus(), u.getMerchantId(),
                    merchant == null ? "商家资料缺失" : merchant.getName(), valid && store != null ? store.getId() : null,
                    "OWNER".equals(u.getMerchantRole()) ? "商家全部门店" : valid ? store.getName() : "门店授权异常", valid);
        }).toList();
        return new AccountPage(rows, new Pagination(page, size, result.getTotalRow()));
    }

    private BaseSysUserEntity owner(Long id) {
        var user = id == null ? null : users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId, id).forUpdate());
        if (user == null || user.getMerchantId() == null || !"OWNER".equals(user.getMerchantRole()))
            throw new CoolException("只能操作商家管理员账号", 403);
        return user;
    }

    @Override
    public void saveOwner(OwnerInput input) {
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 50) throw new CoolException("姓名必填且不超过50字");
        if (input.status() == null || (input.status() != 0 && input.status() != 1)) throw new CoolException("请选择启用或停用");
        var user = owner(input.id());
        var change = new BaseSysUserEntity(); change.setId(user.getId()); change.setName(input.name().trim()); change.setNickName(input.name().trim());
        change.setStatus(input.status());
        if (!Objects.equals(user.getStatus(), input.status())) change.setPasswordV(user.getPasswordV() == null ? 1 : user.getPasswordV() + 1);
        users.update(change);
    }

    @Override
    public void resetOwner(Long id) {
        var user = owner(id);
        var change = new BaseSysUserEntity(); change.setId(user.getId()); change.setPassword(new BCryptPasswordEncoder().encode("123456"));
        change.setPasswordV(user.getPasswordV() == null ? 1 : user.getPasswordV() + 1); users.update(change);
    }
}
