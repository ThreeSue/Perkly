package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.service.SupportAccess;
import com.cool.core.util.CoolSecurityUtil;
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
 * @Time: 16:24:40
 */

@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class ContentRiskScheduleState {
 private final ContentRiskScheduleMapper rows;private final SearchRiskAuditMapper audits;private final SupportAccess access;private final TransactionTemplate tx;
 public record Config(Boolean enabled,Integer intervalMinutes,List<String> sources,Integer batchSize,Integer dailyBudget){}
 public ContentRiskSchedule lock(){var row=rows.selectOneByQuery(QueryWrapper.create().eq(ContentRiskSchedule::getId,1L).forUpdate());if(row==null)throw new IllegalStateException("Content risk migration required");return row;}
 public void save(ContentRiskSchedule row){rows.update(row,false);}
 public ContentRiskSchedule view(){access.platform("search:risk:view");return tx.execute(t->{var row=lock();resetDay(row);return row;});}
 public List<String> sources(ContentRiskSchedule row){return cn.hutool.json.JSONUtil.toList(row.getSourcesJson(),String.class);}
 public Map<String,Long> cursors(ContentRiskSchedule row){var out=new LinkedHashMap<String,Long>();var json=cn.hutool.json.JSONUtil.parseObj(row.getCursorsJson());json.forEach((k,v)->out.put(k,Long.parseLong(v.toString())));return out;}
 private void resetDay(ContentRiskSchedule row){String day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();if(!day.equals(row.getBudgetDate())){row.setBudgetDate(day);row.setUsedCalls(0);save(row);}}
 public boolean consume(){return Boolean.TRUE.equals(tx.execute(t->{var row=lock();resetDay(row);if(row.getUsedCalls()>=row.getDailyBudget())return false;row.setUsedCalls(row.getUsedCalls()+1);save(row);return true;}));}
 public int remaining(ContentRiskSchedule row){resetDay(row);return Math.max(0,row.getDailyBudget()-row.getUsedCalls());}
 public ContentRiskSchedule configure(Config p){
  access.platform("search:risk:manage");
  if(p==null||p.enabled()==null||p.intervalMinutes()==null||p.intervalMinutes()<1||p.intervalMinutes()>10080||p.batchSize()==null||p.batchSize()<1||p.batchSize()>18||p.dailyBudget()==null||p.dailyBudget()<0||p.dailyBudget()>1000||p.sources()==null||p.sources().isEmpty()||p.sources().size()>8||p.sources().stream().anyMatch(s->s==null||!ContentRiskSources.SOURCES.contains(s))||new HashSet<>(p.sources()).size()!=p.sources().size())throw new CoolException("巡检配置无效",400);
  return tx.execute(t->{var row=lock();if(row.getActiveJobId()!=null)throw new CoolException("巡检任务尚未结束，请等待当前批次完成后修改",409);row.setEnabled(p.enabled());row.setIntervalMinutes(p.intervalMinutes());row.setSourcesJson(cn.hutool.json.JSONUtil.toJsonStr(p.sources()));row.setBatchSize(p.batchSize());row.setDailyBudget(p.dailyBudget());row.setNextRunAt(new Date(System.currentTimeMillis()+p.intervalMinutes()*60000L));save(row);
   var a=new SearchRiskAudit();a.setOperatorId(CoolSecurityUtil.getCurrentUserId());a.setTargetId(1L);a.setAction("SCHEDULE_CONFIG");a.setDetail(cn.hutool.json.JSONUtil.toJsonStr(p));audits.insert(a);return row;});
 }
}

