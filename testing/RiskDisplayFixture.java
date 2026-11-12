import java.nio.file.*;
import java.util.*;
import com.zaxxer.hikari.*;
import com.mybatisflex.core.*;
import com.mybatisflex.core.query.QueryWrapper;
import com.cool.modules.searchrisk.SearchRisk;
import com.cool.modules.user.entity.UserInfoEntity;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用现有开发账号创建明确标记的风险展示记录，不修改账号或发送通知
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 20:41:00
 */
public class RiskDisplayFixture {
 public interface Users extends BaseMapper<UserInfoEntity> {}
 public interface Risks extends BaseMapper<SearchRisk> {}
 public interface WorkspaceUsers extends BaseMapper<com.cool.modules.base.entity.sys.BaseSysUserEntity> {}
 public static void main(String[] args) throws Exception {
  Map<?,?> config=new org.yaml.snakeyaml.Yaml().load(Files.readString(Path.of("src/main/resources/application-local.yml")));
  Map<?,?> ds=(Map<?,?>)((Map<?,?>)config.get("spring")).get("datasource");
  String url=ds.get("url").toString();
  if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/cool_test?"))throw new IllegalStateException("Only local cool_test permitted");
  boolean subjects=args.length==1&&args[0].startsWith("--subjects");
  boolean apply=args.length==1&&(args[0].equals("--apply")||args[0].equals("--subjects-apply"));
  HikariConfig hc=new HikariConfig();hc.setJdbcUrl(url);hc.setUsername(ds.get("username").toString());hc.setPassword(ds.get("password").toString());hc.setMaximumPoolSize(1);hc.setReadOnly(!apply);
  try(var data=new HikariDataSource(hc)) {
   var boot=new MybatisFlexBootstrap().setDataSource(data).setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class).addMapper(Users.class).addMapper(Risks.class).addMapper(WorkspaceUsers.class).start();
   boot.getConfiguration().setMapUnderscoreToCamelCase(true);
   if(subjects){seedSubjects(boot,apply);return;}
   var users=boot.getMapper(Users.class).selectListByQuery(QueryWrapper.create().eq(UserInfoEntity::getStatus,1).orderBy(UserInfoEntity::getId,true).limit(3));
   if(users.isEmpty())throw new IllegalStateException("No existing active accounts");
   for(var u:users)System.out.println("EXISTING_ACCOUNT "+u.getId()+" "+u.getNickName());
   if(!apply)return;
   String[] samples={"【风险展示测试，非真实行为】搜索测试：赌博优惠", "【风险展示测试，非真实行为】反馈测试：刷单返利广告", "【风险展示测试，非真实行为】聊天测试：虚假中奖信息"};
   String[] words={"赌博","刷单","中奖"};String[] scenes={"SEARCH","FEEDBACK","CHAT"};
   var mapper=boot.getMapper(Risks.class);var json=new com.fasterxml.jackson.databind.ObjectMapper();
   for(int i=0;i<samples.length;i++) {
    String key="UI_DEMO_20261112_"+i;
    var old=mapper.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getDedupKey,key));
    if(old!=null){System.out.println("EXISTING_RISK "+old.getId());continue;}
    var user=users.get(i%users.size());var r=new SearchRisk();r.setUserId(user.getId());r.setSubjectType("APP");r.setSubjectId(user.getId());r.setScene(scenes[i]);r.setSource("UI_DEMO_20261112");r.setContent(samples[i]);r.setStatus("PENDING");r.setMode("REVIEW");r.setDedupKey(key);
    r.setMatchedWords(json.writeValueAsString(List.of(words[i])));int start=samples[i].indexOf(words[i]);
    r.setMatchedDetails(json.writeValueAsString(List.of(Map.of("word",words[i],"matchedText",words[i],"start",start,"end",start+words[i].length(),"mode","REVIEW","source","UI_DEMO"))));
    mapper.insert(r);System.out.println("INSERTED_RISK "+r.getId()+" account="+user.getId()+" scene="+scenes[i]);
   }
   System.out.println("DEMO_COUNT "+mapper.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getSource,"UI_DEMO_20261112")));
  }
 }
 static void seedSubjects(MybatisFlexBootstrap boot,boolean apply)throws Exception{
  var accounts=boot.getMapper(WorkspaceUsers.class).selectListByQuery(QueryWrapper.create().eq(com.cool.modules.base.entity.sys.BaseSysUserEntity::getStatus,1).orderBy(com.cool.modules.base.entity.sys.BaseSysUserEntity::getId,true));
  var selected=new java.util.LinkedHashMap<String,com.cool.modules.base.entity.sys.BaseSysUserEntity>();
  for(var u:accounts){
   String kind=u.getMerchantId()==null?"ADMIN":"STORE".equals(u.getAccountType())?"STORE":"MERCHANT";
   selected.putIfAbsent(kind,u);
  }
  if(!selected.keySet().containsAll(List.of("ADMIN","MERCHANT","STORE")))throw new IllegalStateException("Missing existing account category");
  var mapper=boot.getMapper(Risks.class);var json=new com.fasterxml.jackson.databind.ObjectMapper();
  for(String kind:List.of("ADMIN","MERCHANT","STORE","GUEST","SYSTEM")){
   var u=selected.get(kind);String type="STORE".equals(kind)?"MERCHANT":kind;
   String key="UI_SUBJECT_DEMO_20261112_"+kind;
   System.out.println("DEMO_SUBJECT "+kind+" name="+(u==null?"无账号主体":u.getName())+" account="+(u==null?"none":u.getId()));
   if(!apply)continue;
   if(mapper.selectOneByQuery(QueryWrapper.create().eq(SearchRisk::getDedupKey,key))!=null){System.out.println("SKIPPED_EXISTING "+kind);continue;}
   String label=switch(kind){case "ADMIN"->"运营账号";case "MERCHANT"->"商家账号";case "STORE"->"门店人员";case "GUEST"->"未登录访客";default->"AI系统输出（不归责用户）";};
   String content="【风险展示测试，非真实行为】"+label+"展示样例：刷单返利广告";
   var r=new SearchRisk();r.setSubjectType(type);r.setSubjectId(u==null?null:u.getId());r.setScene("SYSTEM".equals(kind)?"AI_OUTPUT":"GUEST".equals(kind)?"SEARCH":"CHAT");r.setSource("UI_SUBJECT_DEMO_20261112");r.setContent(content);r.setStatus("PENDING");r.setMode("REVIEW");r.setDedupKey(key);
   r.setMatchedWords(json.writeValueAsString(List.of("刷单")));int start=content.indexOf("刷单");
   r.setMatchedDetails(json.writeValueAsString(List.of(Map.of("word","刷单","matchedText","刷单","start",start,"end",start+2,"mode","REVIEW","source","UI_DEMO"))));
   mapper.insert(r);System.out.println("INSERTED "+kind);
  }
  System.out.println("SUBJECT_DEMO_COUNT "+mapper.selectCountByQuery(QueryWrapper.create().eq(SearchRisk::getSource,"UI_SUBJECT_DEMO_20261112")));
 }
}
