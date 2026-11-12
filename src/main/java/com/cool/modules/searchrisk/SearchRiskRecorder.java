package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 搜索风险检测与运营复核
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:00:23
 */
@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class SearchRiskRecorder {
 private final SearchRiskMapper records;
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void bind(java.util.List<Long> ids,Long businessId){for(Long id:ids){var row=records.selectOneById(id);if(row!=null&&row.getBusinessId()==null){row.setBusinessId(businessId);records.update(row);}}}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void record(SearchRisk entry){records.insert(entry);}
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public Long persist(SearchRisk entry){
  if(entry.getDedupKey()!=null){var old=records.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create().eq(SearchRisk::getDedupKey,entry.getDedupKey()));if(old!=null){if(entry.getAiAdvice()!=null){old.setAiAdvice(entry.getAiAdvice());records.update(old);}return old.getId();}}
  try{records.insert(entry);}catch(org.springframework.dao.DuplicateKeyException duplicate){if(entry.getDedupKey()==null)throw duplicate;var old=records.selectOneByQuery(com.mybatisflex.core.query.QueryWrapper.create().eq(SearchRisk::getDedupKey,entry.getDedupKey()));if(old==null)throw duplicate;return old.getId();}
  return entry.getId();
 }
}
