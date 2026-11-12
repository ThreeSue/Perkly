package com.cool.modules.recommendation;
import com.cool.modules.profile.ProfileFacts;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 用预先定义的虚构后续选择对照距离与热度基线而不宣称真实转化
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 17:08:00
 */
class RecommendationHoldoutTest {
 LocalDate day=LocalDate.of(2026,11,10);
 ProfileFacts.Fact visit(int category,int age){return new ProfileFacts.Fact("synthetic-"+category+"-"+age,ProfileFacts.Kind.VISIT,1L,(long)category,1L,(long)category,day.minusDays(age).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),2500L,null,null,List.of(),false);}
 @Test void compareSyntheticFutureChoicesWithBaselines() throws Exception {
  var candidates=List.of(new RecommendationRanker.Candidate(1L,1L,1L,100L,2000.0,2),new RecommendationRanker.Candidate(2L,2L,2L,100L,100.0,20),new RecommendationRanker.Candidate(3L,3L,3L,100L,1000.0,100));
  List<List<ProfileFacts.Fact>> train=List.of(List.of(visit(1,7),visit(1,14),visit(1,21)),List.of(visit(2,7),visit(2,14)),List.of(visit(1,100),visit(1,120),visit(3,5),visit(3,10)));
  long[] future={1,2,3};int personal=0,distance=0,popular=0;
  StringBuilder report=new StringBuilder("# 虚构留出选择对照\n\n本测试先冻结历史输入和后续选择，再执行排序。3 个极小的人工场景仅用于检查规则方向，不代表真实准确率或转化率；没有用未来选择参与训练或评分。\n\n|场景|预设后续类别|推荐首位|距离首位|热度首位|\n|---|---:|---:|---:|---:|\n");
  for(int i=0;i<train.size();i++) {
   long predicted=RecommendationRanker.rank(1L,day,candidates,train.get(i),"PERSONALIZED").get(0).id();
   long nearest=RecommendationRanker.rank(1L,day,candidates,List.of(),"DISTANCE").get(0).id();
   long hot=candidates.stream().max(Comparator.comparingLong(RecommendationRanker.Candidate::popularity)).orElseThrow().id();
   if(predicted==future[i])personal++;if(nearest==future[i])distance++;if(hot==future[i])popular++;
   report.append("|").append(i+1).append("|").append(future[i]).append("|").append(predicted).append("|").append(nearest).append("|").append(hot).append("|\n");
  }
  assertEquals(3,personal);report.append("\n人工留出匹配次数：个性化 "+personal+"/3；距离 "+distance+"/3；热度 "+popular+"/3。不能外推为线上效果。\n");
  Files.createDirectories(Path.of("logs/test"));Files.writeString(Path.of("logs/test/profile-holdout.md"),report);
 }
}
