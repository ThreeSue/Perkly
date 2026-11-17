package com.cool.modules.searchrisk;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;
import com.cool.modules.searchrisk.mapper.RiskWordStateMapper;
import com.cool.modules.searchrisk.mapper.SearchWordMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 可审计的内容风险词库与增量巡检
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 16:22:24
 */

@org.springframework.stereotype.Component
public class ContentRiskDictionary {
 public record Hit(String word,String mode,String source,int start,int end,String matchedText,Long wordId){}
 record Match(boolean whitelisted,List<Hit> hits){}
 private final SearchWordMapper words;
 private final RiskWordStateMapper states;
 private volatile Snapshot snapshot;
 private SensitiveWordBs bundled;
 @org.springframework.beans.factory.annotation.Value("${perkly.content-risk.bundled-enabled:true}") private boolean bundledEnabled=true;
 ContentRiskDictionary(){this(null,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public ContentRiskDictionary(SearchWordMapper words,RiskWordStateMapper states){this.words=words;this.states=states;}

 private record Snapshot(long revision,boolean initialized,Map<String,SearchWord> overrides,Set<String> allow,SensitiveWordBs matcher){}

 private static SensitiveWordBs base(){return SensitiveWordBs.newInstance().enableNumCheck(false).enableEmailCheck(false).enableUrlCheck(false).enableIpv4Check(false);}
 public Map<String,Object> sources(){
  boolean initialized=current().initialized();
  return Map.of("engine","com.github.houbb:sensitive-word:0.29.5","dictionary","com.github.houbb:sensitive-word-data:1.0.0","license","Apache-2.0","bundledEnabled",bundledEnabled&&!initialized,"managed",initialized,"bundledAction","REVIEW","overrideSource","search_risk_word","offsetUnit","UTF-16","endExclusive",true);
 }

 public List<Hit> find(String text){return match(text,current());}
 public boolean isAllowed(String text){return text!=null&&current().allow().contains(SearchRiskService.normalize(text));}
 Match evaluate(String text){
  var snapshot=current();
  boolean allowed=snapshot.allow().contains(SearchRiskService.normalize(text));
  return new Match(allowed,allowed?List.of():match(text,snapshot));
 }
 boolean bundledEnabled(){return bundledEnabled;}

 private Snapshot current(){
  RiskWordState state=states.selectOneById(1L);
  if(state==null)throw new IllegalStateException("风险词库状态缺失，请先执行词库迁移");
  Snapshot cached=snapshot;
  if(cached!=null&&cached.revision()>=state.getRevision())return cached;
  synchronized(this){
   cached=snapshot;
   if(cached==null||cached.revision()<state.getRevision()){
    cached=build(state.getRevision(),Boolean.TRUE.equals(state.getInitialized()),words.selectAll());
    // Do not publish a snapshot built from a transaction that can still roll back.
    if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())snapshot=cached;
    else if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()){
     Snapshot committed=cached;
     org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
      new org.springframework.transaction.support.TransactionSynchronization(){
       @Override public void afterCommit(){cacheCommitted(committed);}
      });
    }
   }
   return cached;
  }
 }
 private synchronized void cacheCommitted(Snapshot committed){
  if(snapshot==null||snapshot.revision()<committed.revision())snapshot=committed;
 }

 List<Hit> find(String text,List<SearchWord> rules){return match(text,build(0,false,rules));}

 private Snapshot build(long revision,boolean initialized,List<SearchWord> rules){
  var overrides=new LinkedHashMap<String,SearchWord>();
  rules.stream().sorted(Comparator.comparing(w->Objects.requireNonNullElse(w.getId(),0L))).forEach(w->{
   String key=SearchRiskService.normalize(w.getWord());
   if(Boolean.TRUE.equals(w.getDeleted()))overrides.putIfAbsent(key,w);
   else overrides.put(key,w);
  });
  var allow=new HashSet<String>();var active=new ArrayList<String>();
  overrides.forEach((key,rule)->{
   if(Boolean.TRUE.equals(rule.getDeleted())||!Boolean.TRUE.equals(rule.getEnabled()))return;
   if("ALLOW".equals(rule.getMode()))allow.add(key);
   if("BLOCK".equals(rule.getMode())||"REVIEW".equals(rule.getMode()))active.add(key);
  });
  var matcher=base().ignoreCase(false).ignoreWidth(false).ignoreNumStyle(false).ignoreChineseStyle(false).ignoreEnglishStyle(false).ignoreRepeat(false).wordDeny(()->active).wordAllow(List::of).init();
  return new Snapshot(revision,initialized,overrides,allow,matcher);
 }

 private List<Hit> match(String text,Snapshot rules){
  var n=normalized(text);
  if(rules.allow().contains(n.text))return List.of();
  var hits=new ArrayList<Hit>();
  for(var hit:rules.matcher().findAll(n.text,WordResultHandlers.raw())){
   var rule=rules.overrides().get(n.text.substring(hit.startIndex(),hit.endIndex()));
   if(rule!=null)hits.add(map(text,n,rule.getWord(),rule.getMode(),"CUSTOM",hit.startIndex(),hit.endIndex(),rule.getId()));
  }
  if(!rules.initialized()&&bundledEnabled)for(var hit:bundled().findAll(n.text,WordResultHandlers.raw())){
   String word=n.text.substring(hit.startIndex(),hit.endIndex());
   // A custom disabled/ALLOW entry also suppresses the same bundled word.
   if(!rules.overrides().containsKey(SearchRiskService.normalize(word)))hits.add(map(text,n,word,"REVIEW","HOUBB_DATA_1.0.0",hit.startIndex(),hit.endIndex(),null));
  }
  return hits.stream().distinct().toList();
 }
 private synchronized SensitiveWordBs bundled(){if(bundled==null)bundled=base().init();return bundled;}
 private Hit map(String original,Normalized n,String word,String mode,String source,int start,int end,Long wordId){
  int a=n.starts.get(start),b=n.ends.get(end-1);return new Hit(word,mode,source,a,b,original.substring(a,b),wordId);
 }
 private record Normalized(String text,List<Integer> starts,List<Integer> ends){}
 private Normalized normalized(String original){
  var out=new StringBuilder();var starts=new ArrayList<Integer>();var ends=new ArrayList<Integer>();
  var iterator=java.text.BreakIterator.getCharacterInstance(Locale.ROOT);iterator.setText(original);
  int start=iterator.first();for(int end=iterator.next();end!=java.text.BreakIterator.DONE;start=end,end=iterator.next()){
   String part=SearchRiskService.normalize(original.substring(start,end));out.append(part);
   for(int i=0;i<part.length();i++){starts.add(start);ends.add(end);}
  }return new Normalized(out.toString(),starts,ends);
 }
}

