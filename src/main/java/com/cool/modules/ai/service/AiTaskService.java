package com.cool.modules.ai.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.ai.entity.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.space.mapper.SpaceInfoMapper;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.support.service.UserFeedbackService.PageResult;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 以商家锁和请求唯一约束原子预占每日额度并持久化图片任务与历史
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:44:40
 */
@Service @RequiredArgsConstructor
public class AiTaskService {
    private final AiImageTaskMapper tasks;
    private final AiDailyUsageMapper usages;
    private final AiMerchantPolicyMapper policies;
    private final MerchantInfoMapper merchants;
    private final SpaceInfoMapper materials;
    private final com.cool.modules.space.mapper.SpaceTypeMapper materialCategories;
    private final BaseSysUserMapper users;
    private final AiSettingsService settings;
    private final AiAccessService access;
    private final SupportAccess security;
    private final AiProperties properties;
    private final ObjectMapper json;
    @io.swagger.v3.oas.annotations.media.Schema(name="AiImageCreate")
    public record Create(String requestId,String mode,String model,String prompt,Long parentTaskId,AiAccessService.Draft draft) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiImageQuota")
    public record Quota(Long merchantId,String day,int limit,int used,int remaining,boolean enabled,boolean keyConfigured,List<String> models) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiImageTask")
    public record TaskView(Long id,Long merchantId,String merchantName,String actorName,String requestId,Long couponId,Long parentTaskId,
        Long parentMaterialId,String mode,String model,String prompt,String status,Long materialId,String url,boolean archived,
        Long durationMs,java.math.BigDecimal estimatedCost,String errorMessage,Date createTime,Date completedAt) {}
    public static String day(){return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();}
    public String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new CoolException("AI任务无法保存");}
    }
    public Quota quota(Long actor,boolean platform,Long merchant) {
        access.libraryContext(actor,platform,merchant);
        var s=settings.current();var p=policies.selectOneByQuery(QueryWrapper.create().eq(AiMerchantPolicyEntity::getMerchantId,merchant));
        int limit=p!=null && p.getDailyLimit()!=null?p.getDailyLimit():s.getDefaultDailyLimit();
        var u=usages.selectOneByQuery(QueryWrapper.create().eq(AiDailyUsageEntity::getMerchantId,merchant).eq(AiDailyUsageEntity::getDayKey,day()));
        int used=u==null?0:u.getReservedCount();
        return new Quota(merchant,day(),limit,used,Math.max(0,limit-used),
            Boolean.TRUE.equals(s.getEnabled()) && (p==null || !Boolean.FALSE.equals(p.getEnabled())),
            properties.configured(),settings.models(s));
    }
    @Transactional(rollbackFor=Exception.class)
    public TaskView create(Long actor,boolean platform,Create input) {
        if(input==null)throw new CoolException("请填写AI生成请求");
        SupportAccess.request(input.requestId());
        if(input.draft()==null || input.draft().merchantId()==null)throw new CoolException("请先选择优惠券所属商家");
        // FlexTransactionManager不应用Spring声明的隔离级别。先锁现存商家行，再建立一致性读快照，
        // 同商家的下一请求会读到前次提交；对不存在的任务/额度行不加锁，避免跨商家空索引间隙死锁。
        var owner=merchants.selectOneByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getId,input.draft().merchantId()).forUpdate());
        if(owner==null || !Integer.valueOf(1).equals(owner.getStatus()))throw new CoolException("商家已失效",403);
        var prepared=access.prepare(actor,platform,input.draft());var context=prepared.context();
        if(!Set.of("GENERATE","EDIT").contains(Objects.toString(input.mode(),"")))throw new CoolException("请选择重新生成或基于原图完善");
        if("GENERATE".equals(input.mode()) && input.parentTaskId()!=null || "EDIT".equals(input.mode()) && input.parentTaskId()==null)
            throw new CoolException("重新生成不传参考图，完善图片须选中已生成原图");
        String prompt=input.prompt()==null || input.prompt().isBlank()?prepared.suggestedPrompt():input.prompt().strip();
        if(prompt.length()<5 || prompt.length()>4000)throw new CoolException("提示词须为5至4000字，请适当精简");
        var existing=tasks.selectOneByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,context.merchantId())
            .eq(AiImageTaskEntity::getActorKind,context.actorKind()).eq(AiImageTaskEntity::getActorId,actor).eq(AiImageTaskEntity::getRequestId,input.requestId()));
        String hash=cn.hutool.crypto.SecureUtil.sha256(encode(new Create(input.requestId(),input.mode(),input.model(),prompt,input.parentTaskId(),prepared.draft())));
        if(existing!=null) {
            if(!hash.equals(existing.getRequestHash()))throw new CoolException("同一requestId不能用于不同AI请求",409);
            return view(existing);
        }
        var s=settings.current();
        if(!Boolean.TRUE.equals(s.getEnabled()))throw new CoolException("AI图片功能已关闭",409);
        if(!properties.configured())throw new CoolException("尚未配置AI服务密钥，请联系运营",503);
        if(!settings.models(s).contains(input.model()))throw new CoolException("此图片模型尚未开放",403);
        var p=policies.selectOneByQuery(QueryWrapper.create().eq(AiMerchantPolicyEntity::getMerchantId,context.merchantId()));
        if(p!=null && Boolean.FALSE.equals(p.getEnabled()))throw new CoolException("本商家AI图片已暂停",403);
        int limit=p!=null && p.getDailyLimit()!=null?p.getDailyLimit():s.getDefaultDailyLimit();
        var usage=usages.selectOneByQuery(QueryWrapper.create().eq(AiDailyUsageEntity::getMerchantId,context.merchantId()).eq(AiDailyUsageEntity::getDayKey,day()));
        if(usage==null) {
            usage=new AiDailyUsageEntity();usage.setMerchantId(context.merchantId());usage.setDayKey(day());
            usage.setReservedCount(0);usage.setSuccessCount(0);usage.setFailedCount(0);usage.setUncertainCount(0);
            usages.insert(usage);
        } else usage=usages.selectOneByQuery(QueryWrapper.create().eq(AiDailyUsageEntity::getId,usage.getId()).forUpdate());
        if(usage.getReservedCount()>=limit)throw new CoolException("本商家今日AI图片额度已用完",429);
        Long parentMaterial=null;
        if(input.parentTaskId()!=null) {
            var parent=tasks.selectOneById(input.parentTaskId());
            if(parent==null || !context.merchantId().equals(parent.getMerchantId()) || !"STORED".equals(parent.getStatus()) || parent.getMaterialId()==null)
                throw new CoolException("参考图不存在、未入库或不属于本商家",403);
            var material=materials.selectOneById(parent.getMaterialId());
            if(material==null || !context.merchantId().equals(material.getMerchantId()))throw new CoolException("参考图素材归属无效",403);
            parentMaterial=parent.getMaterialId();
        }
        usage.setReservedCount(usage.getReservedCount()+1);usages.update(usage);
        var row=new AiImageTaskEntity();row.setMerchantId(context.merchantId());row.setActorKind(context.actorKind());row.setActorId(actor);
        row.setRequestId(input.requestId());row.setRequestHash(hash);row.setCouponId(input.draft().couponId());
        row.setParentTaskId(input.parentTaskId());row.setParentMaterialId(parentMaterial);row.setMode(input.mode());row.setModel(input.model());
        row.setPrompt(prompt);row.setDraftSnapshot(encode(prepared));row.setStatus("QUEUED");row.setQuotaDay(usage.getDayKey());
        row.setImageCount(0);row.setEstimatedCost(s.getEstimatedUnitPrice());row.setNextAttemptAt(new Date());tasks.insert(row);
        row.setImageKey("ai-"+UUID.randomUUID().toString().replace("-","")+".png");tasks.update(row);
        settings.audit(actor,context.merchantId(),row.getId(),"CREATE",input.mode()+" "+input.model());
        return view(row);
    }
    public TaskView info(Long actor,boolean platform,Long merchant,Long id) {
        access.libraryContext(actor,platform,merchant);
        var row=tasks.selectOneById(id);
        if(row==null || !merchant.equals(row.getMerchantId()))throw new CoolException("AI任务不存在或无权访问",403);
        return view(row);
    }
    public PageResult<TaskView> page(Long actor,boolean platform,Long merchant,int page,int size) {
        access.libraryContext(actor,platform,merchant);SupportAccess.page(page,size);
        var rows=tasks.paginate(page,size,QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,merchant).orderBy(AiImageTaskEntity::getId,false));
        return new PageResult<>(rows.getRecords().stream().map(this::view).toList(),rows.getTotalRow(),page,size);
    }
    public PageResult<TaskView> adminPage(Long merchant,String status,int page,int size) {
        security.platform("ai:manage");SupportAccess.page(page,size);
        var q=QueryWrapper.create();
        if(merchant!=null)q.eq(AiImageTaskEntity::getMerchantId,merchant);
        if(status!=null && !status.isBlank()) {
            if(!Set.of("QUEUED","RUNNING","UNKNOWN","GENERATED","STORING","STORAGE_FAILED","STORED","FAILED").contains(status))throw new CoolException("任务状态无效");
            q.eq(AiImageTaskEntity::getStatus,status);
        }
        var rows=tasks.paginate(page,size,q.orderBy(AiImageTaskEntity::getId,false));
        return new PageResult<>(rows.getRecords().stream().map(this::view).toList(),rows.getTotalRow(),page,size);
    }
    public TaskView view(AiImageTaskEntity row) {
        var merchant=merchants.selectOneById(row.getMerchantId());var actor=users.selectOneById(row.getActorId());
        var material=row.getMaterialId()==null?null:materials.selectOneById(row.getMaterialId());
        return new TaskView(row.getId(),row.getMerchantId(),merchant==null?"历史商家":merchant.getName(),
            actor==null?"历史人员":Objects.toString(actor.getName(),actor.getUsername()),row.getRequestId(),row.getCouponId(),
            row.getParentTaskId(),row.getParentMaterialId(),row.getMode(),row.getModel(),row.getPrompt(),row.getStatus(),
            row.getMaterialId(),material==null?null:material.getUrl(),material!=null && Boolean.TRUE.equals(material.getMaterialDeleted()),
            row.getDurationMs(),row.getImageCount()!=null && row.getImageCount()>0?row.getEstimatedCost():null,row.getErrorMessage(),
            row.getCreateTime(),row.getCompletedAt());
    }
    public PageResult<com.cool.modules.space.service.MerchantMaterialService.Material> materials(Long actor,boolean platform,Long merchant,
        int page,int size,String keyword,Long category,String source) {
        access.libraryContext(actor,platform,merchant);SupportAccess.page(page,size);
        var q=QueryWrapper.create().eq(com.cool.modules.space.entity.SpaceInfoEntity::getMerchantId,merchant)
            .eq(com.cool.modules.space.entity.SpaceInfoEntity::getMaterialDeleted,false);
        com.cool.modules.space.service.MerchantMaterialService.filterSource(q,source);
        if(keyword!=null && !keyword.isBlank()) {
            if(keyword.length()>100)throw new CoolException("素材搜索词过长");
            q.like(com.cool.modules.space.entity.SpaceInfoEntity::getName,keyword.strip());
        }
        if(category!=null) {
            if(category==0)q.isNull(com.cool.modules.space.entity.SpaceInfoEntity::getClassifyId);
            else {
                var c=materialCategories.selectOneById(category);
                if(c==null || !merchant.equals(c.getMerchantId()))throw new CoolException("素材分类不属于当前商家",403);
                q.eq(com.cool.modules.space.entity.SpaceInfoEntity::getClassifyId,category);
            }
        }
        var rows=materials.paginate(page,size,q.orderBy(com.cool.modules.space.entity.SpaceInfoEntity::getId,false));
        var list=rows.getRecords().stream().map(m->new com.cool.modules.space.service.MerchantMaterialService.Material(m.getId(),m.getUrl(),
            m.getName(),m.getSize(),m.getClassifyId(),m.getUploaderId(),m.getCreateTime(),Objects.toString(m.getSource(),"UPLOAD"),m.getAiTaskId(),m.getParentMaterialId())).toList();
        return new PageResult<>(list,rows.getTotalRow(),page,size);
    }
    public java.util.List<com.cool.modules.space.service.MerchantMaterialService.Category> categories(Long actor,boolean platform,Long merchant){
        access.libraryContext(actor,platform,merchant);
        return materialCategories.selectListByQuery(QueryWrapper.create()
            .eq(com.cool.modules.space.entity.SpaceTypeEntity::getMerchantId,merchant))
            .stream().map(c->new com.cool.modules.space.service.MerchantMaterialService.Category(c.getId(),c.getName())).toList();
    }
    @Transactional(rollbackFor=Exception.class)
    public TaskView select(Long actor,boolean platform,Long merchant,Long coupon,Long id) {
        access.context(actor,platform,merchant,coupon);
        var row=tasks.selectOneByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getId,id).eq(AiImageTaskEntity::getMerchantId,merchant).forUpdate());
        if(row==null || !"STORED".equals(row.getStatus()))throw new CoolException("图片尚未成功保存到素材库",409);
        var material=materials.selectOneById(row.getMaterialId());
        if(material==null || !merchant.equals(material.getMerchantId()))throw new CoolException("图片素材不可用",403);
        if(Boolean.TRUE.equals(material.getMaterialDeleted())){material.setMaterialDeleted(false);materials.update(material);}
        settings.audit(actor,merchant,id,"SELECT","选用仅回填表单，保存优惠券后生效");
        return view(row);
    }
}
