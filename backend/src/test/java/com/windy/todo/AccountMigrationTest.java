package com.windy.todo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class AccountMigrationTest {
    @Test void upgradingV1PreservesAccountsAndGivesThemNoAdminPrivileges() throws Exception {
        String url = "jdbc:h2:mem:upgrade-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into app_users(email,password_hash,display_name) values('existing@example.com','existing-hash','Existing')");
        }
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select email,password_hash,role,token_version from app_users")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString("email")).isEqualTo("existing@example.com");
            assertThat(rows.getString("password_hash")).isEqualTo("existing-hash");
            assertThat(rows.getString("role")).isEqualTo("USER"); assertThat(rows.getInt("token_version")).isZero();
        }
    }
}
