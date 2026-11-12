import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 在全新隔离库验证旧Docker数据与增量迁移保留集章记录
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 22:06:00
 */
public class MobileUpgradeSchemaCheck {
 @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
  var yaml=(Map<String,Object>)new Yaml().load(Files.readString(Path.of("src/main/resources/application-local.yml")));
  var d=(Map<String,Object>)((Map<String,Object>)yaml.get("spring")).get("datasource");
  String url=Objects.toString(d.get("url"));
  if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/cool_test?"))throw new IllegalStateException("Local DB only");
  String db="perkly_mobile_upgrade_"+System.currentTimeMillis();
  try(var c=DriverManager.getConnection(url,Objects.toString(d.get("username")),Objects.toString(d.get("password")))) {
   c.createStatement().execute("CREATE DATABASE "+db+" CHARACTER SET utf8mb4");
   var p=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe","-h127.0.0.1","-P3306","-u"+d.get("username"),"--default-character-set=utf8mb4",db);
   p.environment().put("MYSQL_PWD",Objects.toString(d.get("password")));
   p.redirectInput(Path.of("../output/perkly-docker-20261111/cool_test.sql").toFile());
   p.redirectOutput(Path.of("logs/test/mobile-upgrade-import-20261112.log").toFile());p.redirectErrorStream(true);
   if(p.start().waitFor()!=0)throw new IllegalStateException("Import failed; see log");
   c.setCatalog(db);
   String before=snapshot(c);
   for(int pass=1;pass<=2;pass++) {
    for(String f:List.of("notice-types-20261109.sql","store-chat-ai-20261110.sql","management-assistant-tasks-20261110.sql","profile-recommendation-20261110.sql","stamp-confirmations-20261111.sql","search-risk-20261112.sql","content-risk-20261112.sql")) {
     String sql=Files.readString(Path.of("deploy",f)).replaceAll("(?m)^--.*$","");
     for(String statement:sql.split(";"))if(!statement.isBlank())c.createStatement().execute(statement);
    }
    if(!before.equals(snapshot(c)))throw new AssertionError("Existing stamp rows changed");
    try(var rs=c.createStatement().executeQuery("SELECT confirmations FROM stamp_campaign")){while(rs.next())if(rs.getString(1)!=null)throw new AssertionError("History fabricated");}
    System.out.println("PASS migration "+pass+": existing stamp rows preserved, confirmations readable");
   }
   Files.writeString(Path.of("logs/test/mobile-upgrade-db-name.txt"),db);
   System.out.println("Isolated database retained: "+db);
  }
 }
 static String snapshot(Connection c)throws Exception {
  var b=new StringBuilder();try(var rs=c.createStatement().executeQuery("SELECT id,activity_id,goals,reward_template_id,reward_stock,granted_count FROM stamp_campaign ORDER BY id")){
   while(rs.next())for(int i=1;i<=6;i++)b.append(rs.getString(i)).append('|');
  }return b.toString();
 }
}
