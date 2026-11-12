package com.cool.modules.recommendation;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.cool.modules.profile.ProfileFacts;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证推荐每日稳定显式排序优先近期抑制及无行为兜底
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:45:00
 */
class RecommendationRankerTest {
    LocalDate day=LocalDate.of(2026,11,10);
    List<RecommendationRanker.Candidate> candidates=List.of(new RecommendationRanker.Candidate(1L,1L,1L,900L,2000.0,0),new RecommendationRanker.Candidate(2L,2L,2L,100L,100.0,0));
    ProfileFacts.Fact visit(long merchant,long category,int days){return new ProfileFacts.Fact("V:"+days,ProfileFacts.Kind.VISIT,1L,merchant,1L,category,day.minusDays(days).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),1000L,null,null,List.of(),false);}
    @Test void stableForSameDayAndAllCandidatesAreConsidered(){var a=RecommendationRanker.rank(1L,day,candidates,List.of(visit(1,1,5)),"PERSONALIZED");assertEquals(a,RecommendationRanker.rank(1L,day,candidates,List.of(visit(1,1,5)),"PERSONALIZED"));assertEquals(2,a.size());assertEquals(1L,a.get(0).id());}
    @Test void explicitPriceAndDistanceWinOverPreferences(){for(String sort:List.of("PRICE_ASC","DISTANCE"))assertEquals(2L,RecommendationRanker.rank(1L,day,candidates,List.of(visit(1,1,5)),sort).get(0).id());}
    @Test void recentSameMerchantIsDamped(){double recent=RecommendationRanker.rank(1L,day,candidates,List.of(visit(1,1,0)),"PERSONALIZED").stream().filter(r->r.id()==1).findFirst().orElseThrow().score();double old=RecommendationRanker.rank(1L,day,candidates,List.of(visit(1,1,3)),"PERSONALIZED").stream().filter(r->r.id()==1).findFirst().orElseThrow().score();assertTrue(recent<old);}
    @Test void coldStartDoesNotInventPreferenceAndRotatesScore(){var a=RecommendationRanker.rank(1L,day,candidates,List.of(),"PERSONALIZED");var b=RecommendationRanker.rank(1L,day.plusDays(1),candidates,List.of(),"PERSONALIZED");assertFalse(a.stream().anyMatch(r->r.reason().contains("业务偏好")));assertNotEquals(a,b);}
    @Test void duplicateCandidatesAreNotRepeated(){assertEquals(1,RecommendationRanker.rank(1L,day,List.of(candidates.get(0),candidates.get(0)),List.of(),"PERSONALIZED").size());}
}
