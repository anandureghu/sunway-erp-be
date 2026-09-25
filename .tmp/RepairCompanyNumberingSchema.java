import java.sql.*;
public class RepairCompanyNumberingSchema {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String url = "jdbc:mysql://localhost:3306/hrdb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    String sql = "CREATE TABLE IF NOT EXISTS company_numbering_configs (" +
      "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
      "company_id BIGINT NOT NULL, " +
      "doc_type VARCHAR(30) NOT NULL, " +
      "prefix VARCHAR(20) DEFAULT NULL, " +
      "start_number BIGINT NOT NULL DEFAULT 1000, " +
      "UNIQUE KEY uk_company_doc_type (company_id, doc_type), " +
      "CONSTRAINT fk_numbering_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE) ENGINE=InnoDB;";
    try (Connection c = DriverManager.getConnection(url, "hruser", "hrpass"); Statement s = c.createStatement()) {
      s.executeUpdate(sql);
      ResultSet rs = s.executeQuery("SHOW TABLES LIKE '%company_numbering_configs%';");
      System.out.println(rs.next() ? "tableExists=true" : "tableExists=false");
    }
  }
}
