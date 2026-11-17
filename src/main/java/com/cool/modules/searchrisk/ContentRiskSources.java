package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.SearchRiskMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
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
public class ContentRiskSources {
 public static final Set<String> SOURCES=Set.of("PROFILE","FEEDBACK","CHAT","MERCHANT","STORE","COUPON","NOTICE","RISKS");
 private final RiskOriginalContent originals;
 private final SearchRiskMapper risks;
 public record Item(Long id,List<RiskOriginalContent.Field> fields){}
 public List<Item> next(String source,long cursor,int count){
  if(!SOURCES.contains(source)||count<1||count>10)throw new IllegalArgumentException("Invalid scan scope");
  if(!"RISKS".equals(source))return originals.page(source,cursor,count).stream().map(row->new Item(row.id(),row.fields())).toList();
  return risks.selectListByQuery(pendingPreprocessing().gt(SearchRisk::getId,cursor)
   .select(SearchRisk::getId).orderBy(SearchRisk::getId,true).limit(count))
   .stream().map(row->new Item(row.getId(),List.of())).toList();
 }

 static QueryWrapper pendingPreprocessing() {
  return QueryWrapper.create().eq(SearchRisk::getStatus,"PENDING")
      .isNull(SearchRisk::getDuplicateOfId).isNull(SearchRisk::getAiAdvice)
      .isNotNull(SearchRisk::getMatchedWords).ne(SearchRisk::getMatchedWords,"[]")
      .ne(SearchRisk::getMatchedWords,"").isNotNull(SearchRisk::getContent).ne(SearchRisk::getContent,"");
 }
}

