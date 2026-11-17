package com.cool.modules.searchrisk;
import com.cool.modules.ai.usage.AiUsageMeter;
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
    @org.springframework.beans.factory.annotation.Autowired
    private AiUsageMeter usageMeter;

 private final ContentRiskJobMapper jobs;private final ContentRiskSources sources;private final ContentRiskModel model;
 private final SearchRiskService detection;private final SearchRiskMapper risks;private final SearchRiskAuditMapper audits;
 private final SupportAccess access;private final TransactionTemplate tx;private final SearchRiskController statistics;
 private final ContentRiskScheduleState budget;
 private static final int PREPROCESS_BATCH_SIZE = 10;
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{var t=new Thread(r,"content-risk-review");t.setDaemon(true);return t;});
 @org.springframework.beans.factory.annotation.Value("${perkly.content-risk.dispatch-enabled:true}") private boolean dispatchEnabled;
 public record Start(String source,Long afterId,Integer limitCount,Integer modelBudget){}
 public record Status(String state,ContentRiskJob task,long pendingAccounts){}
 public ContentRiskJob start(Start p){
  access.platform("search:risk:manage");
  if (p != null && "RISKS".equals(p.source()) && p.modelBudget() != null && p.modelBudget() > 0) {
   return tx.execute(t -> {
    budget.lock();
    var active = activePreprocessing();
    return active != null ? active : submit(p,CoolSecurityUtil.getCurrentUserId(),false);
   });
  }
  return submit(p,CoolSecurityUtil.getCurrentUserId(),false);
 }

 public ContentRiskJob startPreprocessing() {
  access.platform("search:risk:manage");
  return tx.execute(t -> {
   var settings = budget.lock();
   var active = activePreprocessing();
   if (active != null) return active;
   if (!dispatchEnabled || !model.configured()) throw new CoolException("AI 预处理服务未就绪", 503);
   int available = Math.min(PREPROCESS_BATCH_SIZE, budget.remaining(settings));
   if (available == 0) throw new CoolException("今日 AI 调用额度已用完", 429);
   var pending = risks.selectListByQuery(ContentRiskSources.pendingPreprocessing().select(SearchRisk::getId)
       .orderBy(SearchRisk::getId,false).limit(available));
   if (pending.isEmpty()) throw new CoolException("暂无可以预处理的词库命中记录", 409);
   int count = pending.size();
   long afterId = pending.get(count - 1).getId() - 1;
   return submit(new Start("RISKS", afterId, count, count), CoolSecurityUtil.getCurrentUserId(), false);
  });
 }

 public ContentRiskJob preprocessingStatus() {
  access.platform("search:risk:view");
  var active = activePreprocessing();
  return active != null ? active : jobs.selectOneByQuery(preprocessingJobs().orderBy(ContentRiskJob::getId,false).limit(1));
 }

 private ContentRiskJob activePreprocessing() {
  return jobs.selectOneByQuery(preprocessingJobs().in(ContentRiskJob::getStatus,List.of("QUEUED","RUNNING"))
      .orderBy(ContentRiskJob::getId,false).limit(1));
 }

 private QueryWrapper preprocessingJobs() {
  return QueryWrapper.create().eq(ContentRiskJob::getSource,"RISKS").gt(ContentRiskJob::getModelBudget,0);
 }
 public ContentRiskJob submit(Start p,Long operator,boolean scheduled){
  if(p==null||!ContentRiskSources.SOURCES.contains(Objects.toString(p.source(),""))||p.afterId()==null||p.afterId()<0||p.limitCount()==null||p.limitCount()<1||p.limitCount()>100||p.modelBudget()==null||p.modelBudget()<0||p.modelBudget()>20)throw new CoolException("任务范围或预算无效",400);
  var j=new ContentRiskJob();j.setOperatorId(operator);j.setScheduled(scheduled);j.setPhase("QUEUED");j.setSource(p.source());j.setAfterId(p.afterId());j.setCursorId(p.afterId());j.setLimitCount(p.limitCount());j.setModelBudget(p.modelBudget());j.setCheckedCount(0);j.setModelCalls(0);j.setStatus("QUEUED");
  tx.executeWithoutResult(t->{
   jobs.insert(j);
   audit(j,"JOB_START");
   if (usageMeter != null && j.getModelBudget() > 0) usageMeter.accepted(feature(j), actor(j), null);
   dispatchAfterCommit(j.getId());
  });return j;
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
     if ("RISKS".equals(j.getSource())) {
      preprocess(j, item.id());
      advance(j, item.id());
      continue;
     }
     var fields=item.fields().stream().filter(field->field.value()!=null&&!field.value().isBlank()
      &&detection.enabled(field.scene())&&!detection.whitelisted(field.value())).toList();
     if(!fields.isEmpty()){
      String advice=null;
      for(var field:fields)
       detection.inspect(field.value(),field.scene(),field.businessType(),item.id(),field.author(),j.getId(),null);
      if(j.getModelBudget()>0){
       if(j.getModelCalls()>=j.getModelBudget())throw new IllegalStateException("Task model budget exhausted; unchecked content must not advance");
       if(!budget.consume())throw new IllegalStateException("Daily model budget exhausted");
       j.setPhase("AI_RUNNING");
       j.setModelCalls(j.getModelCalls()+1);jobs.update(j);
       String joined=String.join("\n",fields.stream().map(RiskOriginalContent.Field::value).toList());
       var result=AiUsageMeter.as(actor(j), () -> model.review(joined));j.setPhase("RULE_SCAN");jobs.update(j);if(result.review())advice=result.reason();
       var field=fields.get(0);
       if(advice!=null && fields.size()==1)detection.inspect(field.value(),field.scene(),field.businessType(),item.id(),field.author(),j.getId(),advice);
       else if(advice!=null)detection.inspect(joined,field.scene(),j.getSource()+"_COMBINED",item.id(),null,j.getId(),advice);
      }
     }
     advance(j,item.id());
    }
   }
   j.setStatus("COMPLETED");j.setPhase("COMPLETED");j.setFinishedAt(new Date());jobs.update(j);
  }catch(Exception failed){fail(id,"扫描或模型调用失败；已完成进度已保留，请检查服务配置后重试");}
 }

 private void preprocess(ContentRiskJob job, Long id) {
  if (job.getModelBudget() == 0) return;
  var risk = risks.selectOneByQuery(ContentRiskSources.pendingPreprocessing().eq(SearchRisk::getId,id));
  if (risk == null) return;
  if (job.getModelCalls() >= job.getModelBudget() || !budget.consume())
   throw new IllegalStateException("AI 调用额度已用完");
  job.setPhase("AI_RUNNING");
  job.setModelCalls(job.getModelCalls()+1);
  jobs.update(job);
  var advice = AiUsageMeter.as(actor(job), feature(job), () -> model.preprocess(risk));
  var changes = new SearchRisk();
  changes.setAiAdvice(advice.summary());
  risks.updateByQuery(changes, ContentRiskSources.pendingPreprocessing().eq(SearchRisk::getId,id));
 }

 private String feature(ContentRiskJob job) {
  return !Boolean.TRUE.equals(job.getScheduled()) && "RISKS".equals(job.getSource()) ? "riskPreprocess" : "riskScan";
 }

 private AiUsageMeter.Actor actor(ContentRiskJob job) {
  return Boolean.TRUE.equals(job.getScheduled()) || job.getOperatorId() == null
      ? new AiUsageMeter.Actor("SYSTEM", null) : new AiUsageMeter.Actor("PLATFORM", job.getOperatorId());
 }

 private void advance(ContentRiskJob job, Long id) {
  job.setCursorId(id);
  job.setCheckedCount(job.getCheckedCount()+1);
  jobs.update(job);
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

