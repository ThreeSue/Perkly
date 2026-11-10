package com.cool.modules.ai.management;
import com.cool.modules.ai.entity.ManagementAssistantTaskEntity;
import com.cool.modules.ai.mapper.ManagementAssistantTaskMapper;
import com.cool.core.exception.CoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 逐步事务执行已预览任务并复用持久结果恢复失败步骤
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@Service
public class ManagementAssistantTasks {
    private final ManagementAssistantTaskMapper mapper;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Map<Scope,ManagementAssistantTool> tools;
    private final ManagementAssistantAccess access;
    public ManagementAssistantTasks(ManagementAssistantTaskMapper mapper,ObjectMapper json,PlatformTransactionManager manager,
        List<ManagementAssistantTool> tools,ManagementAssistantAccess access) {
        this.mapper=mapper;this.json=json;this.tx=new TransactionTemplate(manager);this.access=access;
        this.tools=new EnumMap<>(Scope.class);tools.forEach(t->{if(this.tools.put(t.scope(),t)!=null)throw new IllegalStateException("重复工具");});
    }
    public Preview create(Actor actor,String session,String login,List<Step> steps,List<Result> previews) {
        var row=new ManagementAssistantTaskEntity();row.setToken(UUID.randomUUID().toString());row.setActorId(actor.id());
        row.setActorKind(actor.platform()?"PLATFORM":"WORKSPACE");row.setSessionId(session);row.setLoginFingerprint(login);
        row.setState("PENDING");row.setSteps(encode(steps));row.setPreviews(encode(previews));row.setResults("[]");row.setCompleted(0);
        row.setExpiresAt(System.currentTimeMillis()+1800000);mapper.insert(row);
        return new Preview(row.getToken(),previews);
    }
    public List<Result> confirm(Actor actor,String session,String login,String token) {
        access.identity(actor);
        for(int i=0;i<6;i++) {
            Boolean done=tx.execute(status->{
                var row=owned(actor,session,login,token,true);
                if("CANCELLED".equals(row.getState()))throw new CoolException("任务已取消，请重新提出需求",409);
                List<Step> steps=decodeList(row.getSteps(),Step.class);List<Result> results=decodeList(row.getResults(),Result.class);
                if(row.getCompleted()>=steps.size())return true;
                if(row.getExpiresAt()<System.currentTimeMillis())throw new CoolException("预览已过期，请重新核对",409);
                int next=row.getCompleted();Step step=steps.get(next);
                var raw=decodeList(row.getPreviews(),Result.class).get(next);
                Result expected=new Result(raw.module(),raw.state(),raw.note(),json.convertValue(raw.data(),CatalogAssistantTool.Change.class));
                ManagementAssistantTool tool=tools.get(step.module());if(tool==null)throw new CoolException("工具不可用",409);
                Result result=tool.execute(actor,step,expected);
                results.add(result);row.setCompleted(next+1);row.setResults(encode(results));
                row.setState(next+1==steps.size()?"COMPLETED":"PENDING");mapper.update(row);
                return next+1==steps.size();
            });
            if(Boolean.TRUE.equals(done))break;
        }
        return decodeList(owned(actor,session,login,token,false).getResults(),Result.class);
    }
    public List<Result> progress(Actor actor,String session,String login,String token) {
        return decodeList(owned(actor,session,login,token,false).getResults(),Result.class);
    }
    public void cancel(Actor actor,String session,String login,String token) {
        tx.executeWithoutResult(status->{var row=owned(actor,session,login,token,true);
            if(!"COMPLETED".equals(row.getState())){row.setState("CANCELLED");mapper.update(row);}});
    }
    private ManagementAssistantTaskEntity owned(Actor actor,String session,String login,String token,boolean lock) {
        access.identity(actor);
        if(token==null)throw new CoolException("请先生成待确认任务",400);
        var q=QueryWrapper.create().eq(ManagementAssistantTaskEntity::getToken,token);
        if(lock)q.forUpdate();var row=mapper.selectOneByQuery(q);
        if(row==null || !actor.id().equals(row.getActorId()) || !(actor.platform()?"PLATFORM":"WORKSPACE").equals(row.getActorKind())
            || !Objects.equals(session,row.getSessionId()) || !Objects.equals(login,row.getLoginFingerprint()))
            throw new CoolException("任务不存在或不属于当前登录会话",404);
        return row;
    }
    private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new CoolException("任务无法保存",500);}}
    private <T> List<T> decodeList(String s,Class<T> type){try{return json.readValue(s,json.getTypeFactory().constructCollectionType(ArrayList.class,type));}
        catch(Exception e){throw new CoolException("任务记录无法读取，请联系管理员核对",500);}}
}
