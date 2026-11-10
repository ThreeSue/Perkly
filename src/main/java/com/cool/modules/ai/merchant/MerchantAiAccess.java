package com.cool.modules.ai.merchant;
import com.cool.modules.merchant.report.MerchantReportAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 门店核销报表独立业务组件
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 08:40:09
 */
@Service @RequiredArgsConstructor
public class MerchantAiAccess {
 private final MerchantReportAccess reports;
 public record Scope(Long actorId,Long merchantId,Long storeId,Long operatorId){}
 public Scope require(Long actor,Long operator){var s=reports.require(actor,operator);return new Scope(s.actorId(),s.merchantId(),s.storeId(),s.operatorId());}
}
