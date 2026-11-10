package com.cool.modules.merchant.report;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 门店核销报表独立业务组件
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 08:40:09
 */
public final class MerchantReportModels {
 private MerchantReportModels(){}
 public record Filter(String period,LocalDate startDate,LocalDate endDate,Long operatorId,String orderNo,String couponName,Integer page,Integer size){}
 public record Range(LocalDate startDate,LocalDate endDate){}
 public record Summary(long successfulRedemptions,long linkedOrders,BigDecimal knownConsumptionYuan,long unknownAmountGroups,BigDecimal knownDiscountYuan,long unknownDiscountRecords,Long failedAttempts){}
 public record Bucket(String time,long count){}
 public record TypeCount(String type,long count,BigDecimal percentage){}
 public record Person(Long id,String name,long count){}
 public record Detail(Long id,Long orderId,String orderNo,String couponName,String couponType,Long operatorId,String operatorName,Date redeemedAt,BigDecimal consumptionYuan,BigDecimal discountYuan){}
 public record Report(Range range,Long storeId,Long operatorId,Summary summary,List<Bucket> trend,List<TypeCount> types,List<Person> people,List<Detail> list,long total,int page,int size,String note){}
}
