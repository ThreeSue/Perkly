package com.cool.modules.ai.merchant;

import com.cool.core.exception.CoolException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 门店只读聊天的独立MerchantChatPolicy组件
 * @Version: v1.1
 * @Date: 2026-11-09
 * @Time: 21:29:18
 */

@Component
public class MerchantChatPolicy {
 @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.ai.service.AiFeatureControl featureControl;
 private final boolean enabled;
 private final int dailyLimit;
 private final Map<Long,Integer> usage=new HashMap<>();
 private LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));
 public MerchantChatPolicy(@Value("${perkly.ai.merchant-chat.enabled:false}") boolean enabled,
   @Value("${perkly.ai.merchant-chat.daily-limit:0}") int dailyLimit){this.enabled=enabled;this.dailyLimit=dailyLimit;}
 public synchronized void consume(Long actor){
  if(featureControl!=null){featureControl.consume("merchant",actor);return;}
  if(!enabled || dailyLimit<=0)throw new CoolException("核销端AI尚未开通，请联系管理员配置开关和额度",503);
  var now=LocalDate.now(ZoneId.of("Asia/Shanghai"));if(!now.equals(day)){usage.clear();day=now;}
  int used=usage.getOrDefault(actor,0);if(used>=dailyLimit)throw new CoolException("今日AI请求额度已用完",429);
  if(usage.size()>=10000 && !usage.containsKey(actor))throw new CoolException("AI请求繁忙，请稍后重试",503);
  usage.put(actor,used+1);
 }
}
