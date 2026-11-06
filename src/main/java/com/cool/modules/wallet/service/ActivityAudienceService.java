package com.cool.modules.wallet.service;

import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 校验活动适用范围商家退出及额外负责人配置
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:21:03
 */
@Service @RequiredArgsConstructor
public class ActivityAudienceService {
    private final MerchantStoreMapper stores;
    private final MerchantInfoMapper merchants;
    private final BaseSysUserMapper users;
    private final WorkspaceAuthorization auth;
    private final SupportAccess access;
    private final WalletActivityExclusionMapper exclusions;
    private final WalletActivityContactMapper contacts;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public record StoreTarget(Long id, Long merchantId, String name) {}
    public record ParticipationInput(Long id, Integer version, Boolean exited, List<Long> excludedStoreIds) {}
    public record Participation(Long id, int version, boolean exited, boolean canExit,
                                List<StoreTarget> stores, List<Long> excludedStoreIds) {}
    public record ContactInput(Long merchantId, Long storeId, List<Long> recipientIds) {}
    public record ContactOption(Long id, String name, String roleName) {}
    public record Recipient(Long id, Long merchantId, Long storeId) {}
    public record Audience(List<Recipient> recipients, List<String> gaps) {}

    public int version(WalletActivityEntity activity) {
        return activity.getActivityVersion()==null ? 1 : activity.getActivityVersion();
    }

    private List<StoreTarget> declared(WalletActivityEntity activity) {
        List<StoreTarget> snapshot;
        if(activity.getAudienceSnapshot()!=null) {
            try {
                snapshot=json.readValue(activity.getAudienceSnapshot(),new com.fasterxml.jackson.core.type.TypeReference<List<StoreTarget>>() {});
            } catch(java.io.IOException invalid) { throw new CoolException("活动门店快照无效"); }
        } else {
            var query=QueryWrapper.create().eq(MerchantStoreEntity::getStatus,1);
            if(activity.getMerchantId()!=null)query.eq(MerchantStoreEntity::getMerchantId,activity.getMerchantId());
            if(!activity.getStoreIds().isEmpty())query.in(MerchantStoreEntity::getId,activity.getStoreIds());
            snapshot=stores.selectListByQuery(query).stream()
                .map(s->new StoreTarget(s.getId(),s.getMerchantId(),s.getName())).toList();
        }
        return snapshot;
    }

    public List<StoreTarget> definition(WalletActivityEntity activity) {
        return declared(activity).stream().filter(target->{
            var s=stores.selectOneById(target.id());
            var m=merchants.selectOneById(target.merchantId());
            return s!=null && Integer.valueOf(1).equals(s.getStatus())
                && target.merchantId().equals(s.getMerchantId())
                && m!=null && Integer.valueOf(1).equals(m.getStatus());
        }).toList();
    }

    public void freeze(WalletActivityEntity activity) {
        if(activity.getAudienceSnapshot()==null) {
            try { activity.setAudienceSnapshot(json.writeValueAsString(definition(activity))); }
            catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new CoolException("活动门店快照无法保存"); }
        }
    }

    public List<StoreTarget> participating(WalletActivityEntity activity) {
        var rows=exclusions.selectListByQuery(QueryWrapper.create().eq(WalletActivityExclusionEntity::getActivityId,activity.getId()));
        return definition(activity).stream().filter(s->rows.stream().noneMatch(e->
            s.merchantId().equals(e.getMerchantId()) && (e.getStoreId()==0 || s.id().equals(e.getStoreId())))).toList();
    }

    public boolean participates(WalletActivityEntity activity,Long store) {
        return participating(activity).stream().anyMatch(s->s.id().equals(store));
    }

    public Long merchantActor(Long actor,String permission) {
        var context=auth.context(actor);
        if(!"MERCHANT".equals(context.accountType()))throw new CoolException("请使用商家身份",403);
        auth.require(actor,permission);
        return context.merchantId();
    }

    public Participation participation(Long actor,WalletActivityEntity activity) {
        Long merchant=merchantActor(actor,"activity:participate");
        var all=definition(activity).stream().filter(s->merchant.equals(s.merchantId())).toList();
        var allowed=auth.storeIds(auth.user(actor));
        var visible=all.stream().filter(s->allowed.contains(s.id())).toList();
        if(visible.isEmpty())throw new CoolException("活动不在当前授权范围",403);
        boolean canExit=declared(activity).stream().filter(s->merchant.equals(s.merchantId())).filter(s->{
            var current=stores.selectOneById(s.id());
            return current!=null && merchant.equals(current.getMerchantId());
        }).allMatch(s->allowed.contains(s.id()));
        var rows=exclusions.selectListByQuery(QueryWrapper.create().eq(WalletActivityExclusionEntity::getActivityId,activity.getId())
            .eq(WalletActivityExclusionEntity::getMerchantId,merchant));
        return new Participation(activity.getId(),version(activity),rows.stream().anyMatch(e->e.getStoreId()==0),
            canExit,visible,
            rows.stream().map(WalletActivityExclusionEntity::getStoreId).filter(allowed::contains).toList());
    }

    public List<StoreTarget> changeParticipation(Long actor,WalletActivityEntity activity,ParticipationInput input) {
        var current=participation(actor,activity);
        if(!Objects.equals(input.version(),current.version()))throw new CoolException("活动版本已变化，请刷新后重新调整",409);
        var selected=input.excludedStoreIds()==null ? Set.<Long>of() : new HashSet<>(input.excludedStoreIds());
        var visible=current.stores().stream().map(StoreTarget::id).toList();
        if(selected.size()>1000 || !visible.containsAll(selected))throw new CoolException("包含范围外门店",403);
        if(input.exited()!=null && input.exited()!=current.exited() && !current.canExit())
            throw new CoolException("整商家退出或恢复需要全部适用门店授权",403);
        Long merchant=auth.user(actor).getMerchantId();
        boolean targetExit=input.exited()==null ? current.exited() : input.exited();
        var changed=current.stores().stream().filter(s->current.exited()!=targetExit
            || selected.contains(s.id())!=current.excludedStoreIds().contains(s.id())).toList();
        if(changed.isEmpty())return List.of();
        exclusions.deleteByQuery(QueryWrapper.create().eq(WalletActivityExclusionEntity::getActivityId,activity.getId())
            .eq(WalletActivityExclusionEntity::getMerchantId,merchant).in(WalletActivityExclusionEntity::getStoreId,visible));
        for(Long store:selected)exclude(activity.getId(),merchant,store);
        if(input.exited()!=null && current.canExit()) {
            exclusions.deleteByQuery(QueryWrapper.create().eq(WalletActivityExclusionEntity::getActivityId,activity.getId())
                .eq(WalletActivityExclusionEntity::getMerchantId,merchant).eq(WalletActivityExclusionEntity::getStoreId,0L));
            if(targetExit)exclude(activity.getId(),merchant,0L);
        }
        return changed;
    }

    private void exclude(Long activity,Long merchant,Long store) {
        var row=new WalletActivityExclusionEntity();
        row.setActivityId(activity);row.setMerchantId(merchant);row.setStoreId(store);
        exclusions.insert(row);
    }

    private Long configurationMerchant(Long actor,boolean platform,Long merchant,Long store) {
        if(store!=null && store<0)throw new CoolException("门店参数无效",403);
        if(platform)access.platform("activity:manage");
        else {
            Long own=merchantActor(actor,"activity:recipients:manage");
            if(merchant!=null && !own.equals(merchant))throw new CoolException("不能配置其他商家",403);
            merchant=own;
        }
        var m=merchant==null?null:merchants.selectOneById(merchant);
        if(m==null || !Integer.valueOf(1).equals(m.getStatus()))throw new CoolException("商家不存在或已停用",403);
        if(store!=null && store>0) {
            var s=stores.selectOneById(store);
            if(s==null || !merchant.equals(s.getMerchantId()))throw new CoolException("门店不属于此商家",403);
            if(!platform)auth.requireStore(actor,store);
        } else if(!platform) {
            var all=stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,merchant));
            var allowed=auth.storeIds(auth.user(actor));
            if(!all.stream().allMatch(s->allowed.contains(s.getId())))throw new CoolException("商家级负责人配置需要全部门店授权",403);
        }
        return merchant;
    }

    public boolean valid(Recipient recipient) {
        try {
            var context=auth.context(recipient.id());
            return recipient.merchantId().equals(context.merchantId())
                && Objects.equals(recipient.storeId(),context.storeId());
        } catch(CoolException invalid) { return false; }
    }
    public List<StoreTarget> recipientStores(Recipient recipient,List<StoreTarget> targets) {
        if(!valid(recipient))return List.of();
        var allowed=auth.context(recipient.id()).storeIds();
        return targets.stream().filter(s->s.merchantId().equals(recipient.merchantId()) && allowed.contains(s.id())).toList();
    }

    public List<ContactOption> contactOptions(Long actor,boolean platform,Long merchant,Long store) {
        merchant=configurationMerchant(actor,platform,merchant,store);
        var result=new ArrayList<ContactOption>();
        for(var u:users.selectListByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getMerchantId,merchant).eq(BaseSysUserEntity::getStatus,1))) {
            Long bound=store==null || store==0?null:store;
            if(valid(new Recipient(u.getId(),merchant,bound))) {
                var c=auth.context(u.getId());
                result.add(new ContactOption(u.getId(),c.name(),c.roleName()));
            }
        }
        return result;
    }

    public List<StoreTarget> contactStores(Long actor,boolean platform,Long merchant) {
        if(platform)access.platform("activity:manage");
        else {
            Long own=merchantActor(actor,"activity:recipients:manage");
            if(merchant!=null && !own.equals(merchant))throw new CoolException("无权配置其他商家",403);
            merchant=own;
        }
        if(merchant==null)throw new CoolException("请选择商家");
        Long selected=merchant;
        var allowed=platform?null:auth.storeIds(auth.user(actor));
        return stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,selected)).stream()
            .filter(s->allowed==null || allowed.contains(s.getId()))
            .map(s->new StoreTarget(s.getId(),selected,s.getName())).toList();
    }

    public List<Long> contactIds(Long actor,boolean platform,Long merchant,Long store) {
        merchant=configurationMerchant(actor,platform,merchant,store);
        return contacts.selectListByQuery(QueryWrapper.create().eq(WalletActivityContactEntity::getMerchantId,merchant)
            .eq(WalletActivityContactEntity::getStoreId,store==null?0L:store)).stream().map(WalletActivityContactEntity::getRecipientId).toList();
    }

    @Transactional(rollbackFor=Exception.class)
    public void saveContacts(Long actor,boolean platform,ContactInput input) {
        Long merchant=configurationMerchant(actor,platform,input.merchantId(),input.storeId());
        // 与同一商家其他配置更新串行，避免并发替换产生丢失或重复。
        merchants.selectOneByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getId,merchant).forUpdate());
        var allowed=contactOptions(actor,platform,merchant,input.storeId()).stream().map(ContactOption::id).toList();
        var selected=input.recipientIds()==null?Set.<Long>of():new HashSet<>(input.recipientIds());
        if(selected.size()>100 || !allowed.containsAll(selected))throw new CoolException("负责人包含无效或范围外账号",403);
        Long store=input.storeId()==null?0L:input.storeId();
        contacts.deleteByQuery(QueryWrapper.create().eq(WalletActivityContactEntity::getMerchantId,merchant).eq(WalletActivityContactEntity::getStoreId,store));
        for(Long id:selected) {
            var row=new WalletActivityContactEntity();row.setMerchantId(merchant);row.setStoreId(store);row.setRecipientId(id);
            contacts.insert(row);
        }
    }

    public Audience recipients(List<StoreTarget> targets) {
        var result=new LinkedHashMap<Long,Recipient>();
        var gaps=new ArrayList<String>();
        for(Long merchant:targets.stream().map(StoreTarget::merchantId).distinct().toList()) {
            var merchantStores=targets.stream().filter(s->merchant.equals(s.merchantId())).toList();
            boolean ownerFound=false;
            var managerStores=new HashSet<Long>();
            for(var u:users.selectListByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getMerchantId,merchant).eq(BaseSysUserEntity::getStatus,1))) {
                try {
                    var c=auth.context(u.getId());
                    boolean owner="MERCHANT".equals(c.accountType()) && "OWNER".equals(u.getMerchantRole());
                    boolean manager="STORE".equals(c.accountType()) && merchantStores.stream().anyMatch(s->s.id().equals(c.storeId()))
                        && ("MANAGER".equals(u.getMerchantRole()) || "ws_manager".equals(auth.role(u).getLabel()));
                    if(owner || manager)result.put(u.getId(),new Recipient(u.getId(),merchant,c.storeId()));
                    if(owner)ownerFound=true;
                    if(manager)managerStores.add(c.storeId());
                } catch(CoolException invalid) { /* 无效账号不投递。 */ }
            }
            if(!ownerFound) {
                var business=merchants.selectOneById(merchant);
                gaps.add((business==null?"该商家":business.getName())+"无有效老板账号");
            }
            for(var s:merchantStores)if(!managerStores.contains(s.id()))gaps.add(s.name()+"无有效店长账号");
            var extra=contacts.selectListByQuery(QueryWrapper.create().eq(WalletActivityContactEntity::getMerchantId,merchant));
            for(var c:extra) {
                Long store=c.getStoreId()==0?null:c.getStoreId();
                if(store!=null && merchantStores.stream().noneMatch(s->s.id().equals(store)))continue;
                var recipient=new Recipient(c.getRecipientId(),merchant,store);
                if(!recipientStores(recipient,targets).isEmpty())result.putIfAbsent(recipient.id(),recipient);
            }
        }
        return new Audience(new ArrayList<>(result.values()),gaps);
    }
}
