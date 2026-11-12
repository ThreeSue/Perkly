package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class ContentRiskJobs {
 private final ContentRiskJobMapper jobs;private final ContentRiskSources sources;private final ContentRiskModel model;
 private final SearchRiskService detection;private final SearchRiskMapper risks;private final SearchRiskAuditMapper audits;
 private final SupportAccess access;private final TransactionTemplate tx;private final SearchRiskController statistics;
 private final ContentRiskScheduleState budget;
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"content-risk-review");t.setDaemon(true);return t;});
 @org.springframework.beans.factory.annotation.Value("${perkly.content-risk.dispatch-enabled:true}") private boolean dispatchEnabled;
 public record Start(String source,Long afterId,Integer limitCount,Integer modelBudget){}
 public record Status(String state,ContentRiskJob task,long pendingAccounts){}
 public ContentRiskJob start(Start p){
  access.platform("search:risk:manage");
  return submit(p,CoolSecurityUtil.getCurrentUserId(),false);
 }
 public ContentRiskJob submit(Start p,Long operator,boolean scheduled){
  if(p==null||!ContentRiskSources.SOURCES.contains(Objects.toString(p.source(),""))||p.afterId()==null||p.afterId()<0||p.limitCount()==null||p.limitCount()<1||p.limitCount()>100||p.modelBudget()==null||p.modelBudget()<0||p.modelBudget()>20)throw new CoolException("任务范围或预算无效",400);
  var j=new ContentRiskJob();j.setOperatorId(operator);j.setScheduled(scheduled);j.setPhase("QUEUED");j.setSource(p.source());j.setAfterId(p.afterId());j.setCursorId(p.afterId());j.setLimitCount(p.limitCount());j.setModelBudget(p.modelBudget());j.setCheckedCount(0);j.setModelCalls(0);j.setStatus("QUEUED");
  tx.executeWithoutResult(t->{jobs.insert(j);audit(j,"JOB_START");dispatchAfterCommit(j.getId());});return j;
 }
 private void dispatchAfterCommit(Long id){if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){dispatch(id);}});else dispatch(id);}
 public void expire(Long id){fail(id,"任务执行心跳超时，进度已保留");}
 public void retryScheduled(Long id){var j=jobs.selectOneByQuery(QueryWrapper.create().eq(ContentRiskJob::getId,id).forUpdate());if(j!=null&&"FAILED".equals(j.getStatus())){j.setStatus("QUEUED");j.setPhase("QUEUED");j.setError(null);j.setFinishedAt(null);jobs.update(j,false);dispatchAfterCommit(id);}}
 private void audit(ContentRiskJob j,String action){var a=new SearchRiskAudit();a.setOperatorId(Boolean.TRUE.equals(j.getScheduled())?null:CoolSecurityUtil.getCurrentUserId());a.setTargetId(j.getId());a.setAction(action);a.setDetail(cn.hutool.json.JSONUtil.toJsonStr(Map.of("source",j.getSource(),"limit",j.getLimitCount(),"modelBudget",j.getModelBudget())));audits.insert(a);}
 private void dispatch(Long id){if(!dispatchEnabled)return;try{executor.execute(()->run(id));}catch(RejectedExecutionException busy){fail(id,"执行队列已满，请稍后重试");}}
 private void fail(Long id,String message){tx.executeWithoutResult(t->{var j=jobs.selectOneByQuery(QueryWrapper.create().eq(ContentRiskJob::getId,id).forUpdate());if(j==null||"COMPLETED".equals(j.getStatus()))return;j.setStatus("FAILED");j.setPhase("FAILED");j.setError(message);j.setFinishedAt(new Date());jobs.update(j);});}
 public void run(Long id){
  var j=tx.execute(t->{var row=jobs.selectOneByQuery(QueryWrapper.create().eq(ContentRiskJob::getId,id).forUpdate());if(row==null||!"QUEUED".equals(row.getStatus()))return null;row.setStatus("RUNNING");row.setStartedAt(new Date());row.setError(null);row.setFinishedAt(null);row.setPhase("RULE_SCAN");jobs.update(row,false);return row;});
  if(j==null)return;
  try{
   while(j.getCheckedCount()<j.getLimitCount()){
    var batch=sources.next(j.getSource(),j.getCursorId(),Math.min(10,j.getLimitCount()-j.getCheckedCount()));if(batch.isEmpty())break;
    for(var item:batch){
     j.setPhase("RULE_SCAN");jobs.update(j);
     if(item.text()!=null&&!item.text().isBlank()&&detection.enabled(item.scene())&&!detection.whitelisted(item.text())){
      String advice=null;
      if(!"RISKS".equals(j.getSource()))detection.inspect(item.text(),item.scene(),item.businessType(),item.businessId(),item.subject(),j.getId(),null);
      if(j.getModelBudget()>0){
       if(j.getModelCalls()>=j.getModelBudget())throw new IllegalStateException("Task model budget exhausted; unchecked content must not advance");
       if(!budget.consume())throw new IllegalStateException("Daily model budget exhausted");
       j.setPhase("AI_RUNNING");
       j.setModelCalls(j.getModelCalls()+1);jobs.update(j);
       var result=model.review(item.text());j.setPhase("RULE_SCAN");jobs.update(j);if(result.review())advice=result.reason();
       if("RISKS".equals(j.getSource())){var risk=risks.selectOneById(item.id());if(risk!=null){risk.setAiAdvice(result.review()?result.reason():"AI未发现明确风险，仍须人工复核");risks.update(risk);}}
       else if(advice!=null)detection.inspect(item.text(),item.scene(),item.businessType(),item.businessId(),item.subject(),j.getId(),advice);
      }
     }
     j.setCursorId(item.id());j.setCheckedCount(j.getCheckedCount()+1);jobs.update(j);
    }
   }
   j.setStatus("COMPLETED");j.setPhase("COMPLETED");j.setFinishedAt(new Date());jobs.update(j);
  }catch(Exception failed){fail(id,"扫描或模型调用失败；已完成进度已保留，请检查服务配置后重试");}
 }
 public ContentRiskJob retry(Long id){
  access.platform("search:risk:manage");if(id==null)throw new CoolException("缺少任务编号",400);
  var row=tx.execute(t->{var j=jobs.selectOneByQuery(QueryWrapper.create().eq(ContentRiskJob::getId,id).forUpdate());if(j==null)throw new CoolException("任务不存在",404);if(!"FAILED".equals(j.getStatus()))throw new CoolException("仅失败任务可重试",409);j.setStatus("QUEUED");j.setPhase("QUEUED");j.setError(null);j.setFinishedAt(null);jobs.update(j,false);audit(j,"JOB_RETRY");return j;});
  dispatch(id);return row;
 }
 public Status status(Long id){
  access.platform("search:risk:view");
  var j=id==null?jobs.selectOneByQuery(QueryWrapper.create().orderBy(ContentRiskJob::getId,false).limit(1)):jobs.selectOneById(id);
  if(id!=null&&j==null)throw new CoolException("任务不存在",404);
  return new Status(j==null?"IDLE":j.getStatus(),j,statistics.accountCount("PENDING"));
 }
 @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
 public void interrupted(){
  if(!dispatchEnabled)return;
  // Only expire stale leases; startup must not interrupt another healthy application instance.
  for(var j:jobs.selectListByQuery(QueryWrapper.create().in(ContentRiskJob::getStatus,List.of("RUNNING","QUEUED")).lt(ContentRiskJob::getUpdateTime,new Date(System.currentTimeMillis()-600000)).limit(100)))fail(j.getId(),"任务心跳超时，进度已保留");
 }
 @jakarta.annotation.PreDestroy public void close(){executor.shutdownNow();}
}

