package com.cool.modules.ai.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysParamEntity;
import com.cool.modules.base.mapper.sys.BaseSysParamMapper;
import com.cool.modules.ai.entity.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.core.util.CoolSecurityUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.time.*;
/**
* @Author: 蘇世睿
* @Class: 移動互聯2412
* @SeatNo: 21
* @Description: 持久化各项AI开关额度并记录受理用量
* @Version: v1.1
* @Date: 2026-11-13
* @Time: 15:00:00
*/
@Service @RequiredArgsConstructor
public class AiFeatureControl {
    private final BaseSysParamMapper params;
    private final AiSettingsMapper settings;
    private final AiAuditMapper audits;
    private final ObjectMapper json;
    private final SupportAccess access;
    @org.springframework.beans.factory.annotation.Value("${perkly.ai.merchant-chat.enabled:false}") private boolean merchantEnabled;
    @org.springframework.beans.factory.annotation.Value("${perkly.ai.merchant-chat.daily-limit:0}") private int merchantLimit;
    @org.springframework.beans.factory.annotation.Value("${perkly.profile.worker-enabled:false}") private boolean profileWorkerEnabled;
    @org.springframework.beans.factory.annotation.Value("${perkly.support.ai-dispatch-enabled:true}") private boolean supportWorkerEnabled;
    public static final Set<String> KEYS=Set.of("user","merchant","support","admin","profile");
    public record Policy(String id,boolean enabled,Integer dailyLimit) {
    }
    public record View(String id,boolean enabled,Integer dailyLimit,long todayUsed,String runtimeNote) {
    }
    private void valid(String id){
        if(id==null || !KEYS.contains(id))throw new CoolException("未知AI功能",400);
    }
    private String runtimeNote(String id){
        if(id.equals("profile") && !profileWorkerEnabled)return "后台画像任务未启动，需部署时启用 worker";
        if(id.equals("support") && !supportWorkerEnabled)return "客服自动接待任务未启动";
        return null;
    }
    public boolean hasCapacity(String id,Long subject){
        var p=policy(id);
        return p.enabled() && (p.dailyLimit()==null || count(id,subject)<p.dailyLimit());
    }
    private void lock(){
        if(settings.selectOneByQuery(QueryWrapper.create().eq(AiSettingsEntity::getId,1L).forUpdate())==null)throw new CoolException("AI配置尚未初始化",503);
    }
    private BaseSysParamEntity row(String id){
        return params.selectOneByQuery(QueryWrapper.create().eq(BaseSysParamEntity::getKeyName,"perkly.ai.control."+id));
    }
    public Policy policy(String id){
        valid(id);
        var r=row(id);
        if(r==null)return id.equals("merchant")?new Policy(id,merchantEnabled,merchantLimit):new Policy(id,true,null);
        try{
            return json.readValue(r.getData(),Policy.class);
        }
        catch(Exception e){
            throw new CoolException("AI配置读取失败",503);
        }
    }
    public void check(String id){
        if(!policy(id).enabled())throw new CoolException("此AI功能已被平台关闭",503);
    }
    private long count(String id,Long actor){
        var q=QueryWrapper.create().eq(AiAuditEntity::getAction,"USE_"+id).ge(AiAuditEntity::getCreateTime,Date.from(LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));
        if(actor!=null)q.eq(AiAuditEntity::getActorId,actor);
        return audits.selectCountByQuery(q);
    }
    public List<View> list(){
        access.platform("ai:manage");
        return List.of("user","merchant","support","admin","profile").stream().map(id->{
            var p=policy(id);
            return new View(id,p.enabled(),p.dailyLimit(),count(id,null),runtimeNote(id));
        }
        ).toList();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void save(Policy p){
        access.platform("ai:manage");
        if(p==null)throw new CoolException("缺少配置",400);
        valid(p.id());
        if(p.dailyLimit()!=null&&(p.dailyLimit()<0||p.dailyLimit()>100000))throw new CoolException("额度须为0至100000，留空不限",400);
        lock();
        var r=row(p.id());
        if(r==null){
            r=new BaseSysParamEntity();
            r.setKeyName("perkly.ai.control."+p.id());
            r.setName("AI功能控制 "+p.id());
            r.setDataType(2);
        }
        try{
            r.setData(json.writeValueAsString(p));
        }
        catch(Exception e){
            throw new CoolException("配置保存失败",500);
        }
        if(r.getId()==null)params.insert(r);
        else params.update(r);
        var a=new AiAuditEntity();
        a.setActorId(CoolSecurityUtil.getCurrentUserId());
        a.setAction("FEATURE_SETTINGS");
        a.setDetail(r.getData());
        audits.insert(a);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void consume(String id,Long subject){
        valid(id);
        if(subject==null)throw new CoolException("缺少AI使用主体",403);
        lock();
        var p=policy(id);
        if(!p.enabled())throw new CoolException("此AI功能已被平台关闭",503);
        if(p.dailyLimit()!=null&&count(id,subject)>=p.dailyLimit())throw new CoolException("今日AI额度已用完",429);
        var a=new AiAuditEntity();
        a.setActorId(subject);
        a.setAction("USE_"+id);
        a.setDetail("受理一次；北京时间每日计额");
        audits.insert(a);
    }
}
