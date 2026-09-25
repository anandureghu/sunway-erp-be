import java.sql.*;
public class CheckDb {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url = "jdbc:mysql://localhost:3306/hrdb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    try (Connection c = DriverManager.getConnection(url, "hruser", "hrpass")) {
      System.out.println("connected=" + c.getCatalog());
      try (Statement s = c.createStatement()) {
        ResultSet rs = s.executeQuery("SHOW TABLES LIKE '%company_numbering_configs%';");
        System.out.println(rs.next() ? "tableExists=true" : "tableExists=false");
        ResultSet r2 = s.executeQuery("SELECT version, description, success FROM flyway_schema_history WHERE version='20260918';");
        while (r2.next()) {
          System.out.println(r2.getString(1) + " | " + r2.getString(2) + " | " + r2.getString(3));
        }
      }
    }
  }
}
