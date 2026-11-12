package com.cool.modules.searchrisk;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;
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
 public record Hit(String word,String mode,String source,int start,int end,String matchedText){}
 private final SensitiveWordBs bundled=base().init();
 @org.springframework.beans.factory.annotation.Value("${perkly.content-risk.bundled-enabled:true}") private boolean bundledEnabled=true;
 private String signature=""; private SensitiveWordBs custom;
 private static SensitiveWordBs base(){return SensitiveWordBs.newInstance().enableNumCheck(false).enableEmailCheck(false).enableUrlCheck(false).enableIpv4Check(false);}
 public Map<String,Object> sources(){return Map.of("engine","com.github.houbb:sensitive-word:0.29.5","dictionary","com.github.houbb:sensitive-word-data:1.0.0","license","Apache-2.0","bundledEnabled",bundledEnabled,"bundledAction","REVIEW","overrideSource","search_risk_word","offsetUnit","UTF-16","endExclusive",true);}
 public synchronized List<Hit> find(String text,List<SearchWord> rules){
  var n=normalized(text);var overrides=new LinkedHashMap<String,SearchWord>();
  rules.stream().sorted(Comparator.comparing(w->Objects.requireNonNullElse(w.getId(),0L))).forEach(w->overrides.put(SearchRiskService.normalize(w.getWord()),w));
  if(overrides.values().stream().anyMatch(w->Boolean.TRUE.equals(w.getEnabled())&&"ALLOW".equals(w.getMode())&&SearchRiskService.normalize(w.getWord()).equals(n.text)))return List.of();
  var active=overrides.entrySet().stream().filter(e->Boolean.TRUE.equals(e.getValue().getEnabled())&&Set.of("BLOCK","REVIEW").contains(e.getValue().getMode())).map(Map.Entry::getKey).sorted().toList();
  String next=String.join("\n",active);
  if(custom==null||!signature.equals(next)){
   custom=base().ignoreCase(false).ignoreWidth(false).ignoreNumStyle(false).ignoreChineseStyle(false).ignoreEnglishStyle(false).ignoreRepeat(false).wordDeny(()->active).wordAllow(List::of).init();signature=next;
  }
  var hits=new ArrayList<Hit>();
  for(var hit:custom.findAll(n.text,WordResultHandlers.raw())){
   var rule=overrides.get(n.text.substring(hit.startIndex(),hit.endIndex()));
   if(rule!=null)hits.add(map(text,n,rule.getWord(),rule.getMode(),"CUSTOM",hit.startIndex(),hit.endIndex()));
  }
  if(bundledEnabled)for(var hit:bundled.findAll(n.text,WordResultHandlers.raw())){
   String word=n.text.substring(hit.startIndex(),hit.endIndex());
   // A custom disabled/ALLOW entry also suppresses the same bundled word.
   if(!overrides.containsKey(SearchRiskService.normalize(word)))hits.add(map(text,n,word,"REVIEW","HOUBB_DATA_1.0.0",hit.startIndex(),hit.endIndex()));
  }
  return hits.stream().distinct().toList();
 }
 private Hit map(String original,Normalized n,String word,String mode,String source,int start,int end){
  int a=n.starts.get(start),b=n.ends.get(end-1);return new Hit(word,mode,source,a,b,original.substring(a,b));
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

