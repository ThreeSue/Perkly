package com.cool.modules.ai.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.ai.entity.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.core.util.CoolSecurityUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理AI图片策略商家额度并审计操作且不对外返回密钥
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:38:52
 */
@Service @RequiredArgsConstructor
public class AiSettingsService {
    private final AiSettingsMapper settings;
    private final AiMerchantPolicyMapper policies;
    private final AiAuditMapper audits;
    private final MerchantInfoMapper merchants;
    private final AiProperties properties;
    private final SupportAccess access;
    private final ObjectMapper json;
    @io.swagger.v3.oas.annotations.media.Schema(name="AiSettingsInput")
    public record SettingsInput(Boolean enabled,List<String> allowedModels,Integer defaultDailyLimit,BigDecimal estimatedUnitPrice) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiMerchantPolicyInput")
    public record PolicyInput(Long merchantId,Boolean enabled,Integer dailyLimit) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiSettingsView")
    public record SettingsView(boolean enabled,List<String> allowedModels,List<String> supportedModels,int defaultDailyLimit,
        BigDecimal estimatedUnitPrice,boolean keyConfigured,String textModel) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiMerchantPolicyView")
    public record PolicyView(Long merchantId,String merchantName,Boolean enabled,Integer dailyLimit) {}
    @Transactional(rollbackFor=Exception.class)
    public void initialize() {
        if(settings.selectOneById(1L)!=null)return;
        var row=new AiSettingsEntity();row.setId(1L);row.setEnabled(false);
        row.setAllowedModels(encode(List.of(properties.getImageModel())));row.setDefaultDailyLimit(10);
        try{settings.insert(row);}catch(org.springframework.dao.DuplicateKeyException concurrent){/* 另一实例已完成初始化 */}
    }
    public AiSettingsEntity current(){
        var row=settings.selectOneById(1L);
        if(row==null)throw new CoolException("AI配置尚未初始化，请重新启动后端",503);
        return row;
    }
    public List<String> models(AiSettingsEntity row){
        try{return json.readValue(row.getAllowedModels(),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});}
        catch(java.io.IOException e){throw new CoolException("AI模型配置无效",503);}
    }
    private String encode(Object value){
        try{return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new CoolException("无法保存AI配置");}
    }
    public SettingsView view(){
        access.platform("ai:manage");var row=current();
        return new SettingsView(Boolean.TRUE.equals(row.getEnabled()),models(row),List.of(properties.getImageModel()),
            row.getDefaultDailyLimit(),row.getEstimatedUnitPrice(),properties.configured(),properties.getTextModel());
    }
    @Transactional(rollbackFor=Exception.class)
    public void update(SettingsInput input){
        access.platform("ai:manage");
        if(input==null || input.enabled()==null || input.defaultDailyLimit()==null || input.defaultDailyLimit()<0
            || input.defaultDailyLimit()>10000 || input.allowedModels()==null || input.allowedModels().size()>10
            || input.allowedModels().stream().anyMatch(m->!properties.getImageModel().equals(m))
            || input.enabled() && input.allowedModels().isEmpty()
            || input.estimatedUnitPrice()!=null && (input.estimatedUnitPrice().signum()<0 || input.estimatedUnitPrice().compareTo(new BigDecimal("10000"))>0
                || input.estimatedUnitPrice().scale()>6))throw new CoolException("请填写有效的模型白名单、额度和估算单价");
        var row=settings.selectOneByQuery(QueryWrapper.create().eq(AiSettingsEntity::getId,1L).forUpdate());
        row.setEnabled(input.enabled());row.setAllowedModels(encode(input.allowedModels().stream().distinct().toList()));
        row.setDefaultDailyLimit(input.defaultDailyLimit());row.setEstimatedUnitPrice(input.estimatedUnitPrice());settings.update(row,false);
        audit(CoolSecurityUtil.getCurrentUserId(),null,null,"SETTINGS",encode(input));
    }
    @Transactional(rollbackFor=Exception.class)
    public void policy(PolicyInput input){
        access.platform("ai:manage");
        if(input==null || input.merchantId()==null || input.enabled()==null || input.dailyLimit()!=null && (input.dailyLimit()<0 || input.dailyLimit()>10000))
            throw new CoolException("商家额度无效");
        if(merchants.selectOneByQuery(QueryWrapper.create().eq(MerchantInfoEntity::getId,input.merchantId()).forUpdate())==null)
            throw new CoolException("商家不存在",404);
        var row=policies.selectOneByQuery(QueryWrapper.create().eq(AiMerchantPolicyEntity::getMerchantId,input.merchantId()));
        if(row==null){row=new AiMerchantPolicyEntity();row.setMerchantId(input.merchantId());}
        row.setEnabled(input.enabled());row.setDailyLimit(input.dailyLimit());
        if(row.getId()==null)policies.insert(row);else policies.update(row,false);
        audit(CoolSecurityUtil.getCurrentUserId(),input.merchantId(),null,"POLICY",encode(input));
    }
    public List<PolicyView> policies(){
        access.platform("ai:manage");
        return merchants.selectListByQuery(QueryWrapper.create().orderBy(MerchantInfoEntity::getId,false)).stream().map(m->{
            var p=policies.selectOneByQuery(QueryWrapper.create().eq(AiMerchantPolicyEntity::getMerchantId,m.getId()));
            return new PolicyView(m.getId(),m.getName(),p==null?true:p.getEnabled(),p==null?null:p.getDailyLimit());
        }).toList();
    }
    public void audit(Long actor,Long merchant,Long task,String action,String detail){
        var row=new AiAuditEntity();row.setActorId(actor);row.setMerchantId(merchant);row.setTaskId(task);
        row.setAction(action);row.setDetail(detail);audits.insert(row);
    }
}
