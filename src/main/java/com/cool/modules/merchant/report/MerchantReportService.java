package com.cool.modules.merchant.report;
import static com.cool.modules.merchant.report.MerchantReportModels.*;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.*;
import java.math.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class MerchantReportService {
 private static final Set<String> COUPON_TYPES = Set.of("FULL_REDUCTION", "DISCOUNT", "CASH", "PACKAGE", "EXPERIENCE", "EXCHANGE");
 private final MerchantReportAccess access;
 private final CouponRedemptionMapper records;
 private final ConsumptionOrderMapper orders;
 private final CustomerCouponMapper customerCoupons;
 private final BaseSysUserMapper users;
 private final com.cool.modules.merchant.mapper.MerchantUserStoreMapper memberships;
 public List<Person> operators(Long actor){
  var scope=access.require(actor,null);var ids=new TreeSet<Long>();
  if(scope.operatorId()!=null)ids.add(scope.operatorId());
  else {
   for(var m:memberships.selectListByQuery(QueryWrapper.create().eq(com.cool.modules.merchant.entity.MerchantUserStoreEntity::getMerchantId,scope.merchantId()).eq(com.cool.modules.merchant.entity.MerchantUserStoreEntity::getStoreId,scope.storeId())))ids.add(m.getUserId());
   var q=QueryWrapper.create().select(CouponRedemptionEntity::getOperatorId).eq(CouponRedemptionEntity::getMerchantId,scope.merchantId()).eq(CouponRedemptionEntity::getStoreId,scope.storeId()).eq(CouponRedemptionEntity::getOperatorType,"MERCHANT").groupBy(CouponRedemptionEntity::getOperatorId);
   for(var r:records.selectListByQuery(q))if(r.getOperatorId()!=null)ids.add(r.getOperatorId());
  }
  return ids.stream().map(id->{var u=users.selectOneById(id);return new Person(id,u==null?"历史员工（账号已删除）":Objects.toString(u.getName(),"未命名员工"),0);}).toList();
 }
 public static Range range(Filter f,LocalDate today){
  String period=f.period()==null?"TODAY":f.period();
  return switch(period){
   case "TODAY"->new Range(today,today);
   case "LAST_7_DAYS"->new Range(today.minusDays(6),today);
   case "LAST_30_DAYS"->new Range(today.minusDays(29),today);
   case "CUSTOM"->{if(f.startDate()==null||f.endDate()==null||f.endDate().isBefore(f.startDate())||f.endDate().isAfter(today)||f.startDate().plusDays(365).isBefore(f.endDate()))throw new CoolException("日期范围无效，最多366天",400);yield new Range(f.startDate(),f.endDate());}
   default->throw new CoolException("日期选项无效",400);
  };
 }
 static class Amount {BigDecimal value;boolean unknown;void add(BigDecimal v){if(v==null)unknown=true;else if(value==null)value=v;else if(value.compareTo(v)!=0)unknown=true;}}

 private record CouponIdentity(String name, String type) {
  private static final CouponIdentity EMPTY = new CouponIdentity(null, null);
 }

 private static String knownType(String value) {
  return value != null && COUPON_TYPES.contains(value) ? value : null;
 }

 private static CouponIdentity fromSnapshot(String encoded) {
  if (encoded == null || encoded.isBlank()) return CouponIdentity.EMPTY;
  try {
   var snapshot = JSONUtil.parseObj(encoded);
   return new CouponIdentity(snapshot.getStr("name"), knownType(snapshot.getStr("type")));
  } catch (JSONException invalidHistoricalSnapshot) {
   return CouponIdentity.EMPTY;
  }
 }

 private static CouponIdentity fromOrder(ConsumptionOrderEntity order) {
  String type = knownType(order.getCouponType());
  String name = order.getCouponName();
  if (type != null && name != null && !name.isBlank()) return new CouponIdentity(name, type);
  var snapshot = fromSnapshot(order.getCouponSnapshot());
  return new CouponIdentity(name == null || name.isBlank() ? snapshot.name() : name,
   type == null ? snapshot.type() : type);
 }

 private Map<Long, CouponIdentity> historicalCoupons(Long merchantId, List<CouponRedemptionEntity> batch,
                                                     Map<Long, CouponIdentity> orderCoupons) {
  var ids = new HashSet<Long>();
  for (var record : batch) {
   var receipt = orderCoupons.get(record.getConsumptionOrderId());
   if (record.getCustomerCouponId() != null && (receipt == null || receipt.type() == null || receipt.name() == null)) {
    ids.add(record.getCustomerCouponId());
   }
  }
  if (ids.isEmpty()) return Map.of();
  var snapshots = new HashMap<Long, CouponIdentity>();
  var query = QueryWrapper.create().select(CustomerCouponEntity::getId, CustomerCouponEntity::getRuleSnapshot)
   .from(CustomerCouponEntity.class)
   .eq(CustomerCouponEntity::getMerchantId, merchantId).in(CustomerCouponEntity::getId, ids);
  for (var coupon : customerCoupons.selectListByQuery(query)) {
   snapshots.put(coupon.getId(), fromSnapshot(coupon.getRuleSnapshot()));
  }
  return snapshots;
 }

 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Report query(Long actor,Filter f){
  if(f==null)throw new CoolException("缺少筛选条件",400);
  var scope=access.require(actor,f.operatorId());var zone=ZoneId.of("Asia/Shanghai");var range=range(f,LocalDate.now(zone));
  int page=f.page()==null?1:f.page(),size=f.size()==null?20:f.size();if(page<1||size<1||size>100)throw new CoolException("分页参数无效",400);
  var trend=new TreeMap<String,Long>();boolean hourly=range.startDate().equals(range.endDate());
  for(var d=range.startDate();!d.isAfter(range.endDate());d=d.plusDays(1)){if(hourly)for(int hour=0;hour<24;hour++)trend.put(d+String.format("T%02d:00",hour),0L);else trend.put(d.toString(),0L);}
  var amounts=new HashMap<String,Amount>();var linked=new HashSet<Long>();var types=new TreeMap<String,Long>();var people=new TreeMap<Long,Long>();var names=new HashMap<Long,String>();var details=new ArrayList<Detail>();
  long count=0,unknownDiscount=0;BigDecimal discount=BigDecimal.ZERO;Long before=null;
  while(true){
   var q=QueryWrapper.create().eq(CouponRedemptionEntity::getMerchantId,scope.merchantId()).eq(CouponRedemptionEntity::getStoreId,scope.storeId())
    .ge(CouponRedemptionEntity::getRedeemedAt,Date.from(range.startDate().atStartOfDay(zone).toInstant())).lt(CouponRedemptionEntity::getRedeemedAt,Date.from(range.endDate().plusDays(1).atStartOfDay(zone).toInstant()));
   if(scope.operatorId()!=null)q.eq(CouponRedemptionEntity::getOperatorId,scope.operatorId()).eq(CouponRedemptionEntity::getOperatorType,"MERCHANT");
   if(before!=null)q.lt(CouponRedemptionEntity::getId,before);q.orderBy(CouponRedemptionEntity::getId,false).limit(500);
   var batch=records.selectListByQuery(q);if(batch.isEmpty())break;
   var ids=batch.stream().map(CouponRedemptionEntity::getConsumptionOrderId).filter(Objects::nonNull).distinct().toList();
   var bills=new HashMap<Long,ConsumptionOrderEntity>();if(!ids.isEmpty())for(var o:orders.selectListByQuery(QueryWrapper.create().in(ConsumptionOrderEntity::getId,ids).eq(ConsumptionOrderEntity::getMerchantId,scope.merchantId()).eq(ConsumptionOrderEntity::getStoreId,scope.storeId())))bills.put(o.getId(),o);
   var orderCoupons = new HashMap<Long, CouponIdentity>();
   bills.forEach((id, bill) -> orderCoupons.put(id, fromOrder(bill)));
   var historicalCoupons = historicalCoupons(scope.merchantId(), batch, orderCoupons);
   for(var r:batch){var o=bills.get(r.getConsumptionOrderId());String no=o==null?null:o.getOrderNo();
    var orderCoupon = orderCoupons.getOrDefault(r.getConsumptionOrderId(), CouponIdentity.EMPTY);
    var historical = r.getCustomerCouponId() == null ? CouponIdentity.EMPTY
     : historicalCoupons.getOrDefault(r.getCustomerCouponId(), CouponIdentity.EMPTY);
    String name = orderCoupon.name() == null ? historical.name() : orderCoupon.name();
    if(!matches(no,f.orderNo())||!matches(name,f.couponName()))continue;
    String type = orderCoupon.type() == null ? Objects.toString(historical.type(), "UNKNOWN") : orderCoupon.type();
    count++;types.merge(type,1L,Long::sum);
    var time=r.getRedeemedAt().toInstant().atZone(zone);String key=hourly?time.toLocalDate()+String.format("T%02d:00",time.getHour()):time.toLocalDate().toString();trend.merge(key,1L,Long::sum);
    Long person="MERCHANT".equals(r.getOperatorType())?r.getOperatorId():null;
    String personName="历史操作人未知";if(person!=null){people.merge(person,1L,Long::sum);personName=names.computeIfAbsent(person,id->{var u=users.selectOneById(id);return u==null?"历史员工（账号已删除）":Objects.toString(u.getName(),"未命名员工");});}
    if(o!=null)linked.add(o.getId());String group=o==null?"R"+r.getId():"O"+o.getId();amounts.computeIfAbsent(group,k->new Amount()).add(r.getConsumptionAmount());
    if(r.getDiscountAmount()==null)unknownDiscount++;else discount=discount.add(r.getDiscountAmount());
    if(count>((long)page-1)*size&&details.size()<size)details.add(new Detail(r.getId(),o==null?null:o.getId(),no,name,type,person,personName,r.getRedeemedAt(),r.getConsumptionAmount(),r.getDiscountAmount()));
   }
   before=batch.get(batch.size()-1).getId();
  }
  long unknown=amounts.values().stream().filter(a->a.unknown||a.value==null).count();BigDecimal sum=amounts.values().stream().filter(a->!a.unknown&&a.value!=null).map(a->a.value).reduce(BigDecimal.ZERO,BigDecimal::add);
  long total=count;var distribution=types.entrySet().stream().map(e->new TypeCount(e.getKey(),e.getValue(),BigDecimal.valueOf(e.getValue()*100).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP))).toList();
  return new Report(range,scope.storeId(),scope.operatorId(),new Summary(count,linked.size(),unknown>0&&unknown==amounts.size()?null:sum,unknown,count>0&&unknownDiscount==count?null:discount,unknownDiscount,null),trend.entrySet().stream().map(e->new Bucket(e.getKey(),e.getValue())).toList(),distribution,people.entrySet().stream().map(e->new Person(e.getKey(),names.get(e.getKey()),e.getValue())).toList(),details,count,page,size,"金额单位元；消费金额不是营业收入。券种优先取账单历史信息，其次取发券快照。优惠为核销时实际优惠。按流水ID倒序分页。");
 }
 static boolean matches(String actual,String search){return search==null||search.isBlank()||actual!=null&&actual.contains(search.trim());}
}
