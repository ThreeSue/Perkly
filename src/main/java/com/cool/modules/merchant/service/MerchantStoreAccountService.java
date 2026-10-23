package com.cool.modules.merchant.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 复用现有账号和唯一门店授权管理店长员工并撤销旧会话
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 11:55:52
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class MerchantStoreAccountService {
    private final BaseSysUserMapper users;
    private final MerchantUserStoreMapper links;
    private final MerchantStoreMapper stores;
    private final MerchantAccountService accounts;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private static final Set<String> ROLES = Set.of("MANAGER", "STAFF");
    public record SaveInput(Long id, Long storeId, Long targetStoreId, String username, String name, String role, Integer status, String password) {}
    public record PageInput(Long storeId, Integer page, Integer size, String keyWord) {}
    public record ActionInput(Long storeId, Long id, Integer status) {}
    public record AccountView(Long id, String username, String name, String role, Integer status, Long storeId, String storeName) {}
    public record Pagination(int page, int size, long total) {}
    public record AccountPage(List<AccountView> list, Pagination pagination) {}
    public record StoreOption(Long id, String name, Integer status) {}

    private MerchantStoreEntity scope(Long ownerId, Long storeId) {
        var store = storeId == null ? null : stores.selectOneById(storeId);
        if (store == null) throw new CoolException("门店不存在");
        if (ownerId != null) {
            var owner = accounts.current(ownerId);
            if (!"OWNER".equals(owner.getMerchantRole()) || !Objects.equals(owner.getMerchantId(), store.getMerchantId()))
                throw new CoolException("无权管理该门店账号", 403);
        }
        return store;
    }
    private BaseSysUserEntity target(Long id, MerchantStoreEntity store, boolean lock) {
        var query = QueryWrapper.create().eq(BaseSysUserEntity::getId, id)
                .eq(BaseSysUserEntity::getMerchantId, store.getMerchantId()).in(BaseSysUserEntity::getMerchantRole, ROLES);
        if (lock) query.forUpdate();
        var user = id == null ? null : users.selectOneByQuery(query);
        if (user == null) throw new CoolException("门店账号不存在或无权操作", 403);
        var bindings = links.selectListByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId, id));
        if (bindings.size() != 1 || !Objects.equals(bindings.get(0).getStoreId(), store.getId())
                || !Objects.equals(bindings.get(0).getMerchantId(), store.getMerchantId()))
            throw new CoolException("账号不属于此门店或授权异常，请先核对", 403);
        return user;
    }
    private AccountView view(BaseSysUserEntity user, MerchantStoreEntity store) {
        return new AccountView(user.getId(), user.getUsername(), user.getName(), user.getMerchantRole(), user.getStatus(), store.getId(), store.getName());
    }
    @Transactional(readOnly = true)
    public AccountPage page(Long ownerId, PageInput input) {
        var store = scope(ownerId, input.storeId());
        int page = input.page() == null ? 1 : input.page(), size = input.size() == null ? 20 : input.size();
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new CoolException("分页参数不正确");
        String word = input.keyWord() == null ? "" : input.keyWord().trim();
        if (word.length() > 100) throw new CoolException("搜索内容不能超过100字");
        var ids = links.selectListByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getStoreId, store.getId())
                .eq(MerchantUserStoreEntity::getMerchantId, store.getMerchantId())).stream().map(MerchantUserStoreEntity::getUserId).toList();
        if (ids.isEmpty()) return new AccountPage(List.of(), new Pagination(page, size, 0));
        var query = QueryWrapper.create().in(BaseSysUserEntity::getId, ids).eq(BaseSysUserEntity::getMerchantId, store.getMerchantId())
                .in(BaseSysUserEntity::getMerchantRole, ROLES);
        if (!word.isEmpty()) query.and((QueryWrapper q) -> { q.like(BaseSysUserEntity::getUsername, word).or((QueryWrapper names) -> { names.like(BaseSysUserEntity::getName, word); }); });
        var result = users.paginate(page, size, query.orderBy(BaseSysUserEntity::getId, false));
        return new AccountPage(result.getRecords().stream().map(u -> view(u, store)).toList(), new Pagination(page, size, result.getTotalRow()));
    }
    @Transactional(readOnly = true)
    public AccountView info(Long ownerId, Long storeId, Long id) {
        var store = scope(ownerId, storeId); return view(target(id, store, false), store);
    }
    @Transactional(readOnly = true)
    public List<StoreOption> options(Long ownerId, Long storeId) {
        var store = scope(ownerId, storeId);
        return stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId, store.getMerchantId())
                .orderBy(MerchantStoreEntity::getId, true)).stream().map(s -> new StoreOption(s.getId(), s.getName(), s.getStatus())).toList();
    }
    public Long save(Long ownerId, SaveInput input) {
        var source = scope(ownerId, input.storeId());
        var destination = scope(ownerId, input.targetStoreId() == null ? input.storeId() : input.targetStoreId());
        if (!Objects.equals(source.getMerchantId(), destination.getMerchantId())) throw new CoolException("账号只能绑定同一商家的一家门店", 403);
        if (input.role() == null || !ROLES.contains(input.role())) throw new CoolException("角色只能选择店长或员工");
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 50) throw new CoolException("姓名必填且不超过50字");
        if (!Integer.valueOf(0).equals(input.status()) && !Integer.valueOf(1).equals(input.status())) throw new CoolException("请选择启用或停用");
        if (input.id() == null) {
            if (input.username() == null || !input.username().matches("[A-Za-z0-9_]{4,40}") || "admin".equalsIgnoreCase(input.username()))
                throw new CoolException("账号需4至40位字母、数字或下划线");
            if (users.selectCountByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getUsername, input.username())) > 0)
                throw new CoolException("账号已存在，请更换账号，不能覆盖已有身份");
            String password = input.password() == null || input.password().isEmpty() ? "123456" : input.password();
            if (password.length() < 6 || password.length() > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw new CoolException("密码需6至64位且不超过72字节");
            var user = new BaseSysUserEntity(); user.setUsername(input.username()); user.setName(input.name().trim()); user.setNickName(input.name().trim());
            user.setPassword(passwords.encode(password)); user.setPasswordV(1); user.setStatus(input.status());
            user.setMerchantId(source.getMerchantId()); user.setMerchantRole(input.role());
            try { users.insertSelective(user); }
            catch (org.springframework.dao.DuplicateKeyException duplicate) { throw new CoolException("账号已存在，请更换账号"); }
            var link = new MerchantUserStoreEntity(); link.setUserId(user.getId()); link.setMerchantId(source.getMerchantId()); link.setStoreId(destination.getId()); links.insert(link);
            return user.getId();
        }
        var user = target(input.id(), source, true);
        if (input.username() != null && !input.username().equals(user.getUsername())) throw new CoolException("登录账号不可修改");
        if (input.password() != null && !input.password().isEmpty()) throw new CoolException("请使用重置密码操作");
        var change = new BaseSysUserEntity(); change.setId(user.getId()); change.setName(input.name().trim()); change.setNickName(input.name().trim());
        change.setMerchantRole(input.role()); change.setStatus(input.status());
        if (!Objects.equals(user.getMerchantRole(), input.role()) || !Objects.equals(user.getStatus(), input.status()) || !source.getId().equals(destination.getId()))
            change.setPasswordV(version(user));
        users.update(change);
        if (!source.getId().equals(destination.getId())) {
            var link = links.selectOneByQuery(QueryWrapper.create().eq(MerchantUserStoreEntity::getUserId, user.getId()));
            link.setStoreId(destination.getId()); links.update(link);
        }
        return user.getId();
    }
    public void status(Long ownerId, ActionInput input) {
        var store = scope(ownerId, input.storeId()); var user = target(input.id(), store, true);
        if (!Integer.valueOf(0).equals(input.status()) && !Integer.valueOf(1).equals(input.status())) throw new CoolException("请选择启用或停用");
        var change = new BaseSysUserEntity(); change.setId(user.getId()); change.setStatus(input.status()); change.setPasswordV(version(user)); users.update(change);
    }
    public void resetPassword(Long ownerId, ActionInput input) {
        var store = scope(ownerId, input.storeId()); var user = target(input.id(), store, true);
        var change = new BaseSysUserEntity(); change.setId(user.getId()); change.setPassword(passwords.encode("123456")); change.setPasswordV(version(user)); users.update(change);
    }
    private int version(BaseSysUserEntity user) { return user.getPasswordV() == null ? 1 : user.getPasswordV() + 1; }
}
