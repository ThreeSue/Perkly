import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 只允许向固定隔离回归数据库应用画像增量表结构
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:50:00
 */
public class ProfileTestMigration {
 public static void main(String[] args)throws Exception {
  Map<?,?> root=new Yaml().load(Files.readString(Path.of("src/main/resources/application-local.yml")));
  Map<?,?> source=(Map<?,?>)((Map<?,?>)root.get("spring")).get("datasource");
  String url="jdbc:mysql://127.0.0.1:3306/perkly_ai_regression_20261106?useUnicode=true&characterEncoding=UTF-8&serverTimezone=GMT%2b8";
  try(Connection c=DriverManager.getConnection(url,Objects.toString(source.get("username")),Objects.toString(source.get("password")))) {
   if(!"perkly_ai_regression_20261106".equals(c.getCatalog()))throw new IllegalStateException("Wrong database");
   String sql=Files.readString(Path.of("deploy/profile-recommendation-20261110.sql"));
   for(String statement:sql.split(";"))if(!statement.isBlank())try(Statement s=c.createStatement()){s.execute(statement);}
   System.out.println("Profile migration applied to isolated regression database only.");
  }
 }
}
