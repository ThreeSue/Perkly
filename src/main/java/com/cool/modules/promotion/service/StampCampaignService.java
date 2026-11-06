package com.cool.modules.promotion.service;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.promotion.entity.*;
import com.cool.modules.promotion.mapper.*;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper;
import com.cool.modules.coupon.service.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.support.service.UserFeedbackService.PageResult;
import com.cool.modules.wallet.entity.WalletActivityEntity;
import com.cool.modules.wallet.mapper.WalletActivityMapper;
import com.cool.modules.wallet.service.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 配置跨商家集章活动冻结目标奖励规则并管理库存
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 23:49:28
 */
@Service @RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class StampCampaignService {
    private final WalletActivityMapper activities;
    private final StampCampaignMapper configs;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final CouponTemplateMapper couponRows;
    private final CouponTemplateService templates;
    private final CouponPublicationService publications;
    private final CouponTemplateRelationsMapper relations;
    private final StampStockEntryMapper stockEntries;
    private final ActivityAudienceService audience;
    private final ActivityNoticeService notices;
    private final SupportAccess access;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    @io.swagger.v3.oas.annotations.media.Schema(name="StampGoal")
    public record Goal(Long merchantId,List<Long> storeIds) {}
    public record GoalView(Long merchantId,String merchantName,List<WalletActivityService.StoreName> stores) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="StampCampaignInput")
    public record Input(Long id,Integer version,String title,String description,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date startAt,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date endAt,
        List<Goal> goals,Long rewardTemplateId,Long rewardStock) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="StampCampaignCommand")
    public record Command(Long id,Integer version) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="StampCampaignSchedule")
    public record Schedule(Long id,Integer version,Boolean enabled,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date startAt,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") Date endAt) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="StampStockAddition")
    public record Stock(Long id,String requestId,Long amount) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="StampCampaignView")
    public record View(Long id,String title,String description,String status,boolean enabled,Integer version,
        Date startAt,Date endAt,List<GoalView> goals,Long rewardStock,Long grantedCount,CouponTemplateEntity reward) {}
    public record MerchantOption(Long id,String name,List<WalletActivityService.StoreName> stores) {}
    public record RewardReview(Long id,Integer version,Boolean approved,String reason) {}
    public void permission(){access.platform("stamp:manage");}
    public WalletActivityEntity header(Long id,boolean lock) {
        if(id==null)throw new CoolException("请选择集章活动");
        var q=QueryWrapper.create().eq(WalletActivityEntity::getId,id).eq(WalletActivityEntity::getKind,"STAMP");
        var row=activities.selectOneByQuery(lock?q.forUpdate():q);
        if(row==null)throw new CoolException("集章活动不存在",404);
        return row;
    }
    public StampCampaignEntity config(Long id) {
        var row=configs.selectOneByQuery(QueryWrapper.create().eq(StampCampaignEntity::getActivityId,id));
        if(row==null)throw new CoolException("集章配置不存在",404);return row;
    }
    public List<Goal> goals(StampCampaignEntity config){
        try{return json.readValue(config.getGoals(),new com.fasterxml.jackson.core.type.TypeReference<List<Goal>>(){});}
        catch(java.io.IOException e){throw new CoolException("集章目标快照无效");}
    }
    private String goalsJson(List<Goal> goals){
        try{return json.writeValueAsString(goals);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new CoolException("集章目标无法保存");}
    }
    private void version(WalletActivityEntity a,Integer version) {
        if(!Objects.equals(a.getActivityVersion(),version))throw new CoolException("活动版本已变化，请刷新后重试",409);
    }
    private void dates(Date from,Date to) {
        if(from==null || to==null || !to.after(from))throw new CoolException("请选择有效的活动起止时间");
    }
    public List<MerchantOption> options() {
        permission();
        return merchants.selectListByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getStatus,1))
            .stream().map(m->new MerchantOption(m.getId(),m.getName(),stores.selectListByQuery(QueryWrapper.create()
                .eq(MerchantStoreEntity::getMerchantId,m.getId()).eq(MerchantStoreEntity::getStatus,1))
                .stream().map(s->new WalletActivityService.StoreName(s.getId(),s.getName())).toList()))
            .filter(m->!m.stores().isEmpty()).toList();
    }
    private List<Goal> normalize(List<Goal> selected) {
        if(selected==null || selected.size()<2 || selected.size()>20)throw new CoolException("请选择2至20个不同商家");
        var seen=new HashSet<Long>();var result=new ArrayList<Goal>();
        for(var goal:selected) {
            if(goal==null || goal.merchantId()==null || !seen.add(goal.merchantId()))throw new CoolException("商家不可重复");
            var m=merchants.selectOneById(goal.merchantId());
            if(m==null || !Integer.valueOf(1).equals(m.getStatus()))throw new CoolException("商家已失效");
            var available=stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,m.getId()).eq(MerchantStoreEntity::getStatus,1));
            var ids=goal.storeIds()==null || goal.storeIds().isEmpty()?available.stream().map(MerchantStoreEntity::getId).toList():goal.storeIds().stream().distinct().toList();
            if(ids.isEmpty() || ids.size()>100 || ids.contains(null) || !available.stream().map(MerchantStoreEntity::getId).toList().containsAll(ids))
                throw new CoolException("请选择该商家的正常门店",403);
            result.add(new Goal(m.getId(),ids));
        }
        return result;
    }
    public Long save(Long actor,Input input) {
        permission();
        if(input==null || input.title()==null || input.title().isBlank() || input.title().length()>100
            || input.description()!=null && input.description().length()>5000)throw new CoolException("请填写活动名称与有效说明");
        dates(input.startAt(),input.endAt());
        if(input.rewardStock()==null || input.rewardStock()<1 || input.rewardStock()>1000000)throw new CoolException("奖励库存须为1至1000000");
        var selected=normalize(input.goals());
        reward(input.rewardTemplateId(),false);
        WalletActivityEntity a;StampCampaignEntity c;
        if(input.id()==null) {
            a=new WalletActivityEntity();a.setKind("STAMP");a.setCreatorId(actor);a.setStatus("DRAFT");a.setEnabled(true);a.setActivityVersion(1);
            c=new StampCampaignEntity();c.setGrantedCount(0L);
        } else {
            a=header(input.id(),true);version(a,input.version());
            if(!"DRAFT".equals(a.getStatus()))throw new CoolException("发布后目标和奖励不可修改，请新建活动",409);
            c=config(a.getId());a.setActivityVersion(a.getActivityVersion()+1);
        }
        a.setTitle(input.title().trim());a.setDescription(input.description());a.setStartAt(input.startAt());a.setEndAt(input.endAt());
        a.setStoreIds(selected.stream().flatMap(g->g.storeIds().stream()).toList());
        if(a.getId()==null)activities.insert(a);else activities.update(a);
        c.setActivityId(a.getId());c.setGoals(goalsJson(selected));c.setRewardTemplateId(input.rewardTemplateId());c.setRewardStock(input.rewardStock());
        if(c.getId()==null)configs.insert(c);else configs.update(c);
        return a.getId();
    }
    private CouponTemplateEntity reward(Long id,boolean published) {
        if(id==null)throw new CoolException("请选择任务专用奖励券");
        var coupon=(CouponTemplateEntity)templates.info(id);
        if(!"TASK_ONLY".equals(coupon.getIssueChannel()))throw new CoolException("公开券不能直接作任务奖励，请制作任务专用券");
        if(published) {
            coupon=templates.validateForPublication(id,coupon.getRuleVersion());
            var state=relations.publicationState(id);
            if(!Integer.valueOf(2).equals(state.getInt("audit_status")) || !Integer.valueOf(1).equals(state.getInt("publish_status"))
                || !Objects.equals(state.getInt("published_version"),coupon.getRuleVersion()))throw new CoolException("奖励券须先通过审核并上架");
            if("FIXED".equals(coupon.getValidityType()) && !coupon.getValidTo().after(new Date()))throw new CoolException("奖励券已过期");
        }
        return coupon;
    }
    public void publish(Long actor,Command input) {
        permission();var a=header(input.id(),true);version(a,input.version());
        if(!"DRAFT".equals(a.getStatus()))throw new CoolException("只能发布草稿",409);
        if(!a.getEndAt().after(new Date()))throw new CoolException("活动结束时间须晚于当前时间");
        var c=configs.selectOneByQuery(QueryWrapper.create().eq(StampCampaignEntity::getActivityId,a.getId()).forUpdate());
        var selected=normalize(goals(c));var reward=reward(c.getRewardTemplateId(),true);
        c.setRewardRuleVersion(reward.getRuleVersion());
        var snapshot=BeanUtil.beanToMap(reward);
        for(var key:List.of("publication","queryWrapper","createTime","updateTime","issuedCount"))snapshot.remove(key);
        c.setRewardSnapshot(JSONUtil.toJsonStr(snapshot));configs.update(c);
        a.setStoreIds(selected.stream().flatMap(g->g.storeIds().stream()).toList());
        audience.freeze(a);a.setStatus("PUBLISHED");a.setReviewedBy(actor);a.setReviewedAt(new Date());
        a.setActivityVersion(a.getActivityVersion()+1);activities.update(a);notices.published(a);
    }
    public void schedule(Schedule input) {
        permission();dates(input.startAt(),input.endAt());
        var a=header(input.id(),true);version(a,input.version());
        if(!"PUBLISHED".equals(a.getStatus()) || input.enabled()==null)throw new CoolException("只能调整已发布活动",409);
        var targets=audience.participating(a);a.setEnabled(input.enabled());a.setStartAt(input.startAt());a.setEndAt(input.endAt());
        a.setActivityVersion(a.getActivityVersion()+1);activities.update(a);notices.changed(a,targets,"CHANGED");
    }
    public void close(Command input) {
        permission();var a=header(input.id(),true);version(a,input.version());
        if("OFFLINE".equals(a.getStatus()))return;
        var targets=audience.participating(a);boolean published="PUBLISHED".equals(a.getStatus());
        a.setStatus("OFFLINE");a.setActivityVersion(a.getActivityVersion()+1);activities.update(a);
        if(published)notices.changed(a,targets,"CLOSED");
    }
    public void stock(Long actor,Stock input) {
        permission();SupportAccess.request(input.requestId());
        if(input.amount()==null || input.amount()<1 || input.amount()>1000000)throw new CoolException("补充库存须为1至1000000");
        header(input.id(),false);
        var c=configs.selectOneByQuery(QueryWrapper.create().eq(StampCampaignEntity::getActivityId,input.id()).forUpdate());
        var previous=stockEntries.selectOneByQuery(QueryWrapper.create().eq(StampStockEntryEntity::getActivityId,input.id()).eq(StampStockEntryEntity::getRequestId,input.requestId()));
        if(previous!=null) {
            if(!Objects.equals(previous.getAmount(),input.amount()))throw new CoolException("同一请求不可更改补库存数量",409);return;
        }
        // 专用奖励券仅补容量，不改变已审核优惠规则；操作记录与两份库存同事务保存。
        relations.lockTemplate(c.getRewardTemplateId());
        var template=couponRows.selectOneById(c.getRewardTemplateId());
        if(!"TASK_ONLY".equals(template.getIssueChannel()))throw new CoolException("仅可补任务专用券库存");
        if(template.getIssueLimit()!=null) {
            template.setIssueLimit(Math.addExact(template.getIssueLimit(),input.amount()));couponRows.update(template);
        }
        c.setRewardStock(Math.addExact(c.getRewardStock(),input.amount()));configs.update(c);
        var row=new StampStockEntryEntity();row.setActivityId(input.id());row.setRequestId(input.requestId());
        row.setOperatorId(actor);row.setAmount(input.amount());stockEntries.insert(row);
    }
    public View view(Long id) {
        var a=header(id,false);var c=config(id);
        var goals=goals(c).stream().map(g->{
            var m=merchants.selectOneById(g.merchantId());
            return new GoalView(g.merchantId(),m==null?"历史商家":m.getName(),g.storeIds().stream().map(sid->{
                var s=stores.selectOneById(sid);return new WalletActivityService.StoreName(sid,s==null?"历史门店":s.getName());
            }).toList());
        }).toList();
        var r=(CouponTemplateEntity)templates.info(c.getRewardTemplateId());
        if(c.getRewardSnapshot()!=null) {
            var frozen=JSONUtil.toBean(c.getRewardSnapshot(),CouponTemplateEntity.class);
            frozen.setPublication(r.getPublication());frozen.setIssuedCount(r.getIssuedCount());
            frozen.setIssueLimit(r.getIssueLimit());r=frozen;
        }
        return new View(a.getId(),a.getTitle(),a.getDescription(),a.getStatus(),!Boolean.FALSE.equals(a.getEnabled()),
            a.getActivityVersion(),a.getStartAt(),a.getEndAt(),goals,c.getRewardStock(),c.getGrantedCount(),r);
    }
    public PageResult<View> page(int page,int size) {
        permission();SupportAccess.page(page,size);
        var rows=activities.paginate(page,size,QueryWrapper.create().eq(WalletActivityEntity::getKind,"STAMP").orderBy(WalletActivityEntity::getId,false));
        return new PageResult<>(rows.getRecords().stream().map(a->view(a.getId())).toList(),rows.getTotalRow(),page,size);
    }
    public List<CouponTemplateEntity> rewards() {
        permission();return couponRows.selectListByQuery(QueryWrapper.create().eq(CouponTemplateEntity::getIssueChannel,"TASK_ONLY")
            .orderBy(CouponTemplateEntity::getId,false).limit(100)).stream().map(c->(CouponTemplateEntity)templates.info(c.getId())).toList();
    }
    public Object makeReward(CouponTemplateEntity input) {
        permission();access.platform("coupon:template:add");access.platform("coupon:template:update");
        input.setIssueChannel("TASK_ONLY");input.setSalePrice(java.math.BigDecimal.ZERO);input.setStatus(1);
        Long id=templates.add(input);publications.submitAudit(id,1);return templates.info(id);
    }
    public Object reviewReward(RewardReview input) {
        permission();access.platform("coupon:template:update");reward(input.id(),false);
        publications.audit(input.id(),input.version(),input.approved(),input.reason());
        if(Boolean.TRUE.equals(input.approved()))publications.publish(input.id(),input.version());
        return templates.info(input.id());
    }
}
