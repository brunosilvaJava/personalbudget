package com.bts.personalbudget.core.domain.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTransferMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrate() throws SQLException {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("20")
                .load()
                .migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO account (code, name, type, initial_balance, initial_balance_date, "
                    + "status, is_default) VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Carteira', 'CASH', 0, "
                    + "'2026-01-01', 'ACTIVE', 0)");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldAcceptATransferBetweenTwoDifferentAccounts() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeUpdate(insert(
                    "(SELECT id FROM account WHERE is_default = 1)", "(SELECT id FROM account WHERE name = 'Carteira')",
                    "10.00")));
        }
    }

    @Test
    void shouldRejectSameAccountOnBothSidesAndNonPositiveAmounts() throws SQLException {
        final String main = "(SELECT id FROM account WHERE is_default = 1)";
        final String wallet = "(SELECT id FROM account WHERE name = 'Carteira')";
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert(main, main, "10.00")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert(main, wallet, "0.00")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert(main, wallet, "-1.00")));
        }
    }

    private static String insert(final String from, final String to, final String amount) {
        return "INSERT INTO account_transfer (code, from_account_id, to_account_id, amount, effective_date) "
                + "VALUES (UNHEX(REPLACE(UUID(), '-', '')), " + from + ", " + to + ", " + amount + ", '2026-02-01')";
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
