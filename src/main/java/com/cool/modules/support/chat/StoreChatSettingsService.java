package com.cool.modules.support.chat;
import static com.cool.modules.support.chat.StoreChatModels.*;
import com.cool.core.exception.CoolException;import com.cool.modules.support.entity.StoreChatSettingsEntity;
import com.cool.modules.support.mapper.StoreChatSettingsMapper;import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理本店客服AI开关与FAQ并校验配置版本
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 10:19:00
 */
@Service @RequiredArgsConstructor
public class StoreChatSettingsService {
 private final StoreChatSettingsMapper rows;private final MerchantStoreMapper stores;
 private final StoreChatAccess access;private final ObjectMapper json;
 public Settings get(Long actor){return read(access.staff(actor,"chat:manage"),false);}
 public Settings read(Long store,boolean lock){
  var q=QueryWrapper.create().eq(StoreChatSettingsEntity::getStoreId,store);if(lock)q.forUpdate();var row=rows.selectOneByQuery(q);
  if(row==null)return new Settings(0,false,List.of());
  try{return new Settings(row.getVersion(),Boolean.TRUE.equals(row.getAiEnabled()),row.getFaqs()==null?List.of():json.readValue(row.getFaqs(),json.getTypeFactory().constructCollectionType(List.class,Faq.class)));}
  catch(Exception e){throw new CoolException("门店客服知识配置无法读取",500);}
 }
 @Transactional(rollbackFor=Exception.class)
 public Settings save(Long actor,SettingsInput input){
  Long store=access.staff(actor,"chat:manage");
  if(input==null || input.version()==null || input.aiEnabled()==null || input.faqs()==null || input.faqs().size()>30
   || input.faqs().stream().anyMatch(f->f==null || f.question()==null || f.question().isBlank() || f.question().length()>200 || f.answer()==null || f.answer().isBlank() || f.answer().length()>1000))
   throw new CoolException("请填写有效的AI开关、版本及FAQ（最多30条）",400);
  stores.selectOneByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getId,store).forUpdate());
  var current=read(store,true);if(!input.version().equals(current.version()))throw new CoolException("配置已变化，请刷新后重试",409);
  var row=rows.selectOneByQuery(QueryWrapper.create().eq(StoreChatSettingsEntity::getStoreId,store));
  boolean create=row==null;if(create){row=new StoreChatSettingsEntity();row.setStoreId(store);}
  row.setVersion(current.version()+1);row.setAiEnabled(input.aiEnabled());
  try{row.setFaqs(json.writeValueAsString(input.faqs()));}catch(Exception e){throw new CoolException("FAQ格式无效",400);}
  if(create)rows.insert(row);else rows.update(row);return read(store,false);
 }
}
