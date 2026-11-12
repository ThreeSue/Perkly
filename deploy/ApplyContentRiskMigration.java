import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

public class ApplyContentRiskMigration {
 @SuppressWarnings("unchecked")
 public static void main(String[] args) throws Exception {
  if(args.length!=1||!"--apply-cool-test".equals(args[0]))throw new IllegalArgumentException("Explicit local development migration flag required");
  Map<String,Object> yaml=new Yaml().load(Files.readString(Path.of("src/main/resources/application-local.yml")));
  var data=(Map<String,Object>)((Map<String,Object>)yaml.get("spring")).get("datasource");
  String url=Objects.toString(data.get("url"),"");
  if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/cool_test?"))throw new IllegalArgumentException("Only cool_test on localhost is allowed");
  try(var c=DriverManager.getConnection(url,Objects.toString(data.get("username"),""),Objects.toString(data.get("password"),""))){
   Path backup=Path.of("logs/server/content-risk-backup-"+System.currentTimeMillis()+".sql");
   var out=new StringBuilder("-- Before content-risk additive migration; cool_test only\n");
   for(String table:List.of("search_risk_word","search_risk_record","search_risk_audit","content_risk_policy","platform_warning","content_risk_job","content_risk_schedule")){
    try(var rs=c.getMetaData().getTables("cool_test",null,table,new String[]{"TABLE"})){if(!rs.next())continue;}
    try(var s=c.createStatement();var rs=s.executeQuery("SHOW CREATE TABLE "+table)){if(rs.next())out.append(rs.getString(2)).append(";\n");}
    try(var s=c.createStatement();var rs=s.executeQuery("SELECT * FROM "+table)){
     var meta=rs.getMetaData();
     while(rs.next()){out.append("INSERT INTO ").append(table).append(" VALUES(");for(int i=1;i<=meta.getColumnCount();i++){if(i>1)out.append(",");String v=rs.getString(i);out.append(v==null?"NULL":"CONVERT(0x"+java.util.HexFormat.of().formatHex(v.getBytes(java.nio.charset.StandardCharsets.UTF_8))+" USING utf8mb4)");}out.append(");\n");}
    }
   }
   Files.createDirectories(backup.getParent());Files.writeString(backup,out);
   for(String file:List.of("deploy/search-risk-20261112.sql","deploy/content-risk-20261112.sql")){
    String sql=Files.readString(Path.of(file)).replaceAll("(?m)^--.*$","");
    for(String statement:sql.split(";"))if(!statement.isBlank())try(var s=c.createStatement()){s.execute(statement.trim());}
   }
   System.out.println("Applied additive content-risk schema to cool_test; backup: "+backup);
  }
 }
}

