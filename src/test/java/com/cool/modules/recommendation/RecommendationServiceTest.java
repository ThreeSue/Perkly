package com.cool.modules.recommendation;
import com.cool.core.request.PageResult;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.coupon.mapper.CustomerCouponMapper;
import com.cool.modules.coupon.entity.CustomerCouponEntity;
import com.cool.modules.profile.ProfileService;
import com.cool.modules.support.service.SupportAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证推荐全候选检索实时库存与领购限制及稳定分页
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:55:00
 */
class RecommendationServiceTest {
 AppDiscoveryService discovery=mock(AppDiscoveryService.class);CustomerCouponMapper owned=mock(CustomerCouponMapper.class);
 RecommendationService service=new RecommendationService(discovery,owned,mock(ProfileService.class),mock(SupportAccess.class));
 RecommendationService.Input input(int page,String sort){return new RecommendationService.Input("COUPON",page,2,null,null,null,null,null,null,null,null,null,sort);}
 Map<String,Object> coupon(long id,long price,String state){return new HashMap<>(Map.of("id",id,"merchantId",1L,"categoryId",1L,"salePrice",price,"saleState",state,"perCustomerLimit",1L,"name","测试券"));}
 PageResult<Map<String,Object>> page(List<Map<String,Object>> rows,long total){var page=new PageResult<Map<String,Object>>();page.setList(rows);page.getPagination().setTotal(total);return page;}
 @Test void cheapestCandidateBeyondTwentyPagesIsStillFound(){
  when(discovery.coupons(any())).thenAnswer(call->{int p=((DiscoveryPageReq)call.getArgument(0)).getPage();List<Map<String,Object>> rows=new ArrayList<>();int end=p==21?1:100;for(int i=0;i<end;i++){long id=(p-1)*100L+i+1;rows.add(coupon(id,id==2001?0:10000+id,"AVAILABLE"));}return page(rows,2001);});
  var result=service.recommend(1L,input(1,"PRICE_ASC"));assertEquals(2001,result.total());assertEquals(2001L,result.list().get(0).get("id"));verify(discovery,times(21)).coupons(any());
 }
 @Test void exhaustedEntitlementsAndUnavailableCouponsAreExcluded(){
  when(discovery.coupons(any())).thenReturn(page(List.of(coupon(1,0,"AVAILABLE"),coupon(2,0,"SOLD_OUT"),coupon(3,0,"ENDED"),coupon(4,0,"NOT_STARTED"),coupon(5,100,"AVAILABLE")),5));
  var acquired=new CustomerCouponEntity();acquired.setCustomerId(1L);acquired.setCouponTemplateId(1L);when(owned.selectListByQuery(any())).thenReturn(List.of(acquired));
  var result=service.recommend(1L,input(1,"PERSONALIZED"));assertEquals(1,result.total());assertEquals(5L,result.list().get(0).get("id"));
 }
 @Test void unchangedCandidatesProduceStableNonoverlappingPages(){
  when(discovery.coupons(any())).thenReturn(page(List.of(coupon(1,100,"AVAILABLE"),coupon(2,200,"AVAILABLE"),coupon(3,300,"AVAILABLE"),coupon(4,400,"AVAILABLE")),4));
  var first=service.recommend(1L,input(1,"PERSONALIZED"));var second=service.recommend(1L,input(2,"PERSONALIZED"));
  Set<Object> ids=new HashSet<>();first.list().forEach(r->ids.add(r.get("id")));second.list().forEach(r->assertTrue(ids.add(r.get("id"))));assertEquals(4,ids.size());assertEquals(first,service.recommend(1L,input(1,"PERSONALIZED")));
 }
 @Test void missingLocationCannotPretendToApplyRadius(){assertThrows(com.cool.core.exception.CoolException.class,()->service.recommend(1L,new RecommendationService.Input("STORE",1,20,null,null,null,null,null,null,null,null,1000.0,"DISTANCE")));}
}
