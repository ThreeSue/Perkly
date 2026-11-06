package com.cool.modules.wallet.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.support.service.*;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.*;
import java.math.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理平台折扣与商家活动申请并按门店计算最大单项优惠
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:45:39
 */
@Service
@RequiredArgsConstructor
public class WalletActivityService {
    private final WalletActivityMapper activities;
    private final MerchantStoreMapper stores;
    private final MerchantInfoMapper merchants;
    private final WorkspaceAuthorization auth;
    private final SupportAccess access;
    private final BaseSysUserMapper users;
    private final ActivityAudienceService audience;
    private final ActivityNoticeService notices;

    public record Input(String title, String description,
                        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") Date startAt,
                        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") Date endAt,
                        List<Long> storeIds, BigDecimal discountRate) {
    }

    public record Review(Long id, boolean approve, BigDecimal discountRate, String note) {
    }
    public record Schedule(Long id, Boolean enabled,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date startAt,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date endAt) {}

    @Transactional(rollbackFor=Exception.class)
    public void schedule(Schedule input) {
        access.platform("activity:manage");
        if(input==null || input.enabled()==null || input.startAt()==null || input.endAt()==null
            || !input.endAt().after(input.startAt()))throw new CoolException("请填写开关及有效起止时间");
        var row=activities.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEntity::getId,input.id()).forUpdate());
        if(row==null || !discount(row) || !"PUBLISHED".equals(row.getStatus()))throw new CoolException("仅可调整已发布折扣活动",409);
        if(Objects.equals(row.getEnabled()==null?true:row.getEnabled(),input.enabled())
            && row.getStartAt().equals(input.startAt()) && row.getEndAt().equals(input.endAt()))return;
        var affected=audience.participating(row);
        audience.freeze(row);
        row.setActivityVersion(audience.version(row)+1);
        row.setEnabled(input.enabled());
        row.setStartAt(input.startAt());
        row.setEndAt(input.endAt());
        activities.update(row);
        notices.changed(row,affected,"CHANGED");
    }

    @io.swagger.v3.oas.annotations.media.Schema(name="WalletPaymentQuote")
    public record Quote(Long originalAmount, Long amount, Long discountAmount, Long activityId, String activityTitle,
                        BigDecimal discountRate,Integer activityVersion) {
        public Quote(Long originalAmount,Long amount,Long discountAmount,Long activityId,String activityTitle,BigDecimal discountRate) {
            this(originalAmount,amount,discountAmount,activityId,activityTitle,discountRate,null);
        }
    }

    public record ActivityView(Long id, String title, String description, String status, BigDecimal discountRate,
                               Date startAt, Date endAt, List<StoreName> stores, String reviewNote,
                               @JsonInclude(JsonInclude.Include.NON_NULL) String submittedBy, boolean enabled, Integer activityVersion,boolean platformActivity,String kind) {
    }

    public record StoreName(Long id, String name) {
    }

    public record StoreOption(Long id, String name, String merchantName) {
    }

    public List<StoreOption> storeOptions(Long actor, boolean platform) {
        if (platform) access.platform("activity:manage");
        else merchant(actor);
        var allowed = platform ? null : auth.storeIds(auth.user(actor));
        return stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getStatus, 1)).stream()
                .filter(s -> allowed == null || allowed.contains(s.getId()))
                .filter(s -> {
                    var m = merchants.selectOneById(s.getMerchantId());
                    return m != null && Integer.valueOf(1).equals(m.getStatus());
                })
                .map(s -> new StoreOption(s.getId(), s.getName(), merchants.selectOneById(s.getMerchantId()).getName())).toList();
    }

    public MerchantStoreEntity normalStore(Long id) {
        var s = stores.selectOneById(id);
        if (s == null || !Integer.valueOf(1).equals(s.getStatus())) throw new CoolException("门店不在营业", 403);
        var m = merchants.selectOneById(s.getMerchantId());
        if (m == null || !Integer.valueOf(1).equals(m.getStatus())) throw new CoolException("商家已停用", 403);
        return s;
    }

    private void rate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.TEN) >= 0 || rate.scale() > 2)
            throw new CoolException("折扣须大于0且小于10，最多两位小数");
    }

    private Long merchant(Long actor) {
        var u = auth.user(actor);
        auth.context(actor);
        auth.require(actor, "activity:apply");
        if (!"MERCHANT".equals(WorkspaceAuthorization.type(u))) throw new CoolException("仅商家账号可申请活动", 403);
        return u.getMerchantId();
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long create(Long actor, boolean platform, Input p) {
        Long merchant = platform ? null : merchant(actor);
        if (platform) access.platform("activity:manage");
        if (p == null || p.title() == null || p.title().isBlank() || p.title().length() > 100 || p.description() != null && p.description().length() > 5000 ||
                p.startAt() == null || p.endAt() == null || !p.endAt().after(p.startAt()))
            throw new CoolException("活动标题说明或起止时间无效");
        var selected = p.storeIds() == null ? List.<Long>of() : p.storeIds().stream().distinct().toList();
        if (selected.size() > 100 || selected.stream().anyMatch(Objects::isNull) || !platform && selected.isEmpty())
            throw new CoolException("商家须选择1至100个本商家门店，平台最多选100店");
        for (Long id : selected) {
            var s = normalStore(id);
            if (!platform && (!merchant.equals(s.getMerchantId()) || !auth.storeIds(auth.user(actor)).contains(id)))
                throw new CoolException("包含无权申请活动的门店", 403);
        }
        if (platform) rate(p.discountRate());
        else if (p.discountRate() != null) throw new CoolException("折扣由运营审批时设定");
        var row = new WalletActivityEntity();
        row.setMerchantId(merchant);
        row.setCreatorId(actor);
        row.setTitle(p.title().trim());
        row.setDescription(p.description());
        row.setStartAt(p.startAt());
        row.setEndAt(p.endAt());
        row.setStoreIds(selected);
        row.setStatus(platform ? "PUBLISHED" : "PENDING");
        row.setActivityVersion(1);
        if (platform) {
            row.setDiscountRate(p.discountRate());
            row.setReviewedBy(actor);
            row.setReviewedAt(new Date());
        }
        if(platform)audience.freeze(row);
        activities.insert(row);
        if(platform)notices.published(row);
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void review(Long actor, Review p) {
        access.platform("activity:manage");
        if (p == null || p.id() == null || p.note() != null && p.note().length() > 500)
            throw new CoolException("审核参数无效");
        var row = activities.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEntity::getId, p.id()).forUpdate());
        if (row == null || !discount(row) || !"PENDING".equals(row.getStatus())) throw new CoolException("活动不存在或已处理", 409);
        if (p.approve()) {
            rate(p.discountRate());
            for (Long id : row.getStoreIds()) normalStore(id);
            row.setDiscountRate(p.discountRate());
            audience.freeze(row);
        } else if (p.note() == null || p.note().isBlank()) throw new CoolException("请填写驳回原因");
        row.setStatus(p.approve() ? "PUBLISHED" : "REJECTED");
        row.setReviewedBy(actor);
        row.setReviewedAt(new Date());
        row.setReviewNote(p.note());
        activities.update(row);
        if(p.approve())notices.published(row);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void offline(Long actor, boolean platform, Long id) {
        Long merchant = platform ? null : merchant(actor);
        if (platform) access.platform("activity:manage");
        var row = activities.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEntity::getId, id).forUpdate());
        if (row == null || !discount(row) || !platform && !Objects.equals(merchant, row.getMerchantId()))
            throw new CoolException("无权处理此活动", 403);
        if (!platform && !new HashSet<>(auth.storeIds(auth.user(actor))).containsAll(row.getStoreIds()))
            throw new CoolException("活动包含无权管理的门店", 403);
        if("OFFLINE".equals(row.getStatus()))return;
        boolean published="PUBLISHED".equals(row.getStatus());
        var affected=audience.participating(row);
        row.setActivityVersion(audience.version(row)+1);
        row.setStatus("OFFLINE");
        activities.update(row);
        if(published)notices.changed(row,affected,"CLOSED");
    }

    public ActivityView view(WalletActivityEntity row) {
        var names = row.getAudienceSnapshot()!=null ? audience.definition(row).stream().map(s->new StoreName(s.id(),s.name())).toList() : row.getStoreIds().stream().map(id -> {
            var s = stores.selectOneById(id);
            return new StoreName(id, s == null ? "历史门店" : s.getName());
        }).toList();
        return new ActivityView(row.getId(), row.getTitle(), row.getDescription(), row.getStatus(), row.getDiscountRate(), row.getStartAt(), row.getEndAt(), names, row.getReviewNote(), submitterName(row.getCreatorId()),!Boolean.FALSE.equals(row.getEnabled()),audience.version(row),row.getMerchantId()==null,Objects.toString(row.getKind(),"DISCOUNT"));
    }

    private String submitterName(Long creatorId) {
        var user = creatorId == null ? null : users.selectOneById(creatorId);
        if (user == null) return null;
        var name = user.getName();
        if (name != null && !name.isBlank()) return name;
        var nickname = user.getNickName();
        return nickname == null || nickname.isBlank() ? null : nickname;
    }

    private ActivityView publicView(WalletActivityEntity row) {
        var names=audience.participating(row).stream().map(s->new StoreName(s.id(),s.name())).toList();
        if(names.isEmpty())return null;
        return new ActivityView(row.getId(),row.getTitle(),row.getDescription(),row.getStatus(),row.getDiscountRate(),
            row.getStartAt(),row.getEndAt(),names,null,null,!Boolean.FALSE.equals(row.getEnabled()),audience.version(row),row.getMerchantId()==null,Objects.toString(row.getKind(),"DISCOUNT"));
    }

    public ActivityView detail(Long actor,Long id) {
        var context=auth.context(actor);
        var row=activities.selectOneById(id);
        if(row==null || !Set.of("PUBLISHED","OFFLINE").contains(row.getStatus()))throw new CoolException("活动不存在或无权查看",403);
        var allowed=auth.storeIds(auth.user(actor));
        var visible=audience.definition(row).stream().filter(s->s.merchantId().equals(context.merchantId()) && allowed.contains(s.id())).toList();
        if(visible.isEmpty())throw new CoolException("活动不在当前授权门店范围",403);
        return new ActivityView(row.getId(),row.getTitle(),row.getDescription(),row.getStatus(),row.getDiscountRate(),
            row.getStartAt(),row.getEndAt(),visible.stream().map(s->new StoreName(s.id(),s.name())).toList(),row.getReviewNote(),
            null,!Boolean.FALSE.equals(row.getEnabled()),audience.version(row),row.getMerchantId()==null,Objects.toString(row.getKind(),"DISCOUNT"));
    }

    public ActivityAudienceService.Participation participation(Long actor,Long id) {
        var row=activities.selectOneById(id);
        if(row==null || !"PUBLISHED".equals(row.getStatus()))throw new CoolException("活动不存在或已结束",403);
        return audience.participation(actor,row);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ActivityAudienceService.Participation participate(Long actor,ActivityAudienceService.ParticipationInput input) {
        var row=activities.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEntity::getId,input.id()).forUpdate());
        if(row==null || !"PUBLISHED".equals(row.getStatus()) || !row.getEndAt().after(new Date()))
            throw new CoolException("活动不存在或已结束",409);
        audience.freeze(row);
        var changed=audience.changeParticipation(actor,row,input);
        if(!changed.isEmpty()){
            row.setActivityVersion(audience.version(row)+1);
            activities.update(row);
            notices.changed(row,changed,"PARTICIPATION");
        }
        return audience.participation(actor,row);
    }

    public UserFeedbackService.PageResult<ActivityView> page(Long actor, boolean platform, int page, int size) {
        SupportAccess.page(page, size);
        var q = QueryWrapper.create();
        if(platform)discountQuery(q);
        if (platform) access.platform("activity:manage");
        else {
            var context=auth.context(actor);
            if(!"MERCHANT".equals(context.accountType()) || Collections.disjoint(new HashSet<>(context.permissions()),
                Set.of("activity:apply","activity:participate")))throw new CoolException("没有活动查看权限",403);
        }
        if (!platform) {
            var allowed = new HashSet<>(auth.storeIds(auth.user(actor)));
            Long merchant=auth.user(actor).getMerchantId();
            var rows = activities.selectListByQuery(q.orderBy(WalletActivityEntity::getId, false)).stream()
                .filter(a -> merchant.equals(a.getMerchantId()) ? allowed.containsAll(a.getStoreIds())
                    : a.getMerchantId()==null && Set.of("PUBLISHED","OFFLINE").contains(a.getStatus())
                        && audience.definition(a).stream().anyMatch(s->merchant.equals(s.merchantId()) && allowed.contains(s.id())))
                .toList();
            int start = (int) Math.min((long) (page - 1) * size, rows.size());
            return new UserFeedbackService.PageResult<>(rows.subList(start, Math.min(start + size, rows.size())).stream()
                .map(a->a.getMerchantId()==null?detail(actor,a.getId()):view(a)).toList(),rows.size(),page,size);
        }
        var result = activities.paginate(page, size, q.orderBy(WalletActivityEntity::getId, false));
        return new UserFeedbackService.PageResult<>(result.getRecords().stream().map(this::view).toList(), result.getTotalRow(), page, size);
    }

    public List<ActivityView> publicList(Long storeId) {
        return publicList(storeId,false);
    }
    private List<ActivityView> publicList(Long storeId,boolean lock) {
        var requestedStore = storeId == null ? null : normalStore(storeId);
        Date now = new Date();
        var query=QueryWrapper.create().eq(WalletActivityEntity::getStatus, "PUBLISHED").le(WalletActivityEntity::getStartAt, now).gt(WalletActivityEntity::getEndAt, now).orderBy(WalletActivityEntity::getId, false);
        discountQuery(query);
        return activities.selectListByQuery(lock?query.forUpdate():query)
                .stream().filter(a->!Boolean.FALSE.equals(a.getEnabled())).filter(a -> a.getMerchantId() == null || Optional.ofNullable(merchants.selectOneById(a.getMerchantId())).map(m -> Integer.valueOf(1).equals(m.getStatus())).orElse(false))
                .filter(a -> storeId == null || (a.getMerchantId() == null || a.getMerchantId().equals(requestedStore.getMerchantId()))
                        && audience.participates(a,storeId)).map(this::publicView).filter(Objects::nonNull).toList();
    }

    public Quote quote(Long storeId, long amount) {
        normalStore(storeId);
        Quote best = new Quote(amount, amount, 0L, null, null, BigDecimal.TEN);
        for (var a : publicList(storeId,true)) {
            long payable = Math.max(1, BigDecimal.valueOf(amount).multiply(a.discountRate()).divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).longValueExact());
            if (payable < best.amount())
                best = new Quote(amount, payable, amount - payable, a.id(), a.title(), a.discountRate(),a.activityVersion());
        }
        return best;
    }
    private boolean discount(WalletActivityEntity row) {
        return row.getKind()==null || "DISCOUNT".equals(row.getKind());
    }
    private void discountQuery(QueryWrapper query) {
        query.and((QueryWrapper q)->{q.isNull(WalletActivityEntity::getKind)
            .or((QueryWrapper other)->{other.eq(WalletActivityEntity::getKind,"DISCOUNT");});});
    }
}
