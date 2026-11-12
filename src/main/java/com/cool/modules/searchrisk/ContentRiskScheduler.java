package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.ContentRiskJobMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 可审计的内容风险词库与增量巡检
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 16:24:41
 */

@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class ContentRiskScheduler {
 private final ContentRiskScheduleState state;private final ContentRiskJobs runner;private final ContentRiskJobMapper jobs;private final TransactionTemplate tx;
 @org.springframework.beans.factory.annotation.Value("${perkly.content-risk.scheduler-enabled:true}") private boolean enabled;
 @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${perkly.content-risk.scheduler-poll-ms:15000}",initialDelay=60000)
 public void scheduled(){if(enabled)tick();}
 public void tick(){tx.executeWithoutResult(t->{
  var s=state.lock();Date now=new Date();
  if(s.getActiveJobId()!=null){
   var job=jobs.selectOneById(s.getActiveJobId());
   if(job==null){s.setActiveJobId(null);s.setEnabled(false);s.setLastResult("FAILED:任务记录缺失");state.save(s);return;}
   if(Set.of("QUEUED","RUNNING").contains(job.getStatus())){
    Date heartbeat=job.getUpdateTime()==null?job.getCreateTime():job.getUpdateTime();
    if(heartbeat!=null&&heartbeat.before(new Date(now.getTime()-600000))){runner.expire(job.getId());}
    return;
   }
   if("FAILED".equals(job.getStatus())&&s.getRetryCount()<2&&Boolean.TRUE.equals(s.getEnabled())&&state.remaining(s)>0){
    if(s.getNextRunAt()!=null&&s.getNextRunAt().after(now))return;
    s.setRetryCount(s.getRetryCount()+1);s.setNextRunAt(new Date(now.getTime()+60000L*s.getRetryCount()));state.save(s);runner.retryScheduled(job.getId());return;
   }
   if("COMPLETED".equals(job.getStatus())){
    // Revisit mutable historical content after reaching the end of a bounded pass.
    var cursors=state.cursors(s);cursors.put(job.getSource(),job.getCheckedCount()<job.getLimitCount()?0L:job.getCursorId());s.setCursorsJson(cn.hutool.json.JSONUtil.toJsonStr(cursors));s.setSourceIndex(s.getSourceIndex()+1);
   }else{s.setEnabled(false);} // Exhausted failures require a human to inspect; never loop at the same row forever.
   s.setLastResult(job.getStatus());s.setLastJobId(job.getId());s.setActiveJobId(null);s.setNextRunAt(new Date(now.getTime()+s.getIntervalMinutes()*60000L));state.save(s);return;
  }
  if(!Boolean.TRUE.equals(s.getEnabled())||s.getNextRunAt()!=null&&s.getNextRunAt().after(now))return;
  int remaining=state.remaining(s);if(remaining==0){s.setLastResult("BUDGET_EXHAUSTED");s.setNextRunAt(Date.from(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));state.save(s);return;}
  // Database lock serializes schedulers; one active bounded task across nodes.
  if(jobs.selectCountByQuery(QueryWrapper.create().in(ContentRiskJob::getStatus,List.of("QUEUED","RUNNING")))>0)return;
  var sources=state.sources(s);String source=sources.get(Math.floorMod(s.getSourceIndex(),sources.size()));
  int count=Math.min(s.getBatchSize(),remaining);
  var job=runner.submit(new ContentRiskJobs.Start(source,state.cursors(s).getOrDefault(source,0L),count,Math.min(20,count+2)),null,true);
  s.setActiveJobId(job.getId());s.setRetryCount(0);s.setLastRunAt(now);s.setNextRunAt(new Date(now.getTime()+s.getIntervalMinutes()*60000L));s.setLastResult("QUEUED");state.save(s);
 });}
}

