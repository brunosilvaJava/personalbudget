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

class BudgetEnvelopeMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    private static final String CYCLE = "(SELECT id FROM pay_cycle WHERE start_date = '2031-01-01')";

    @BeforeAll
    static void migrate() throws SQLException {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("25")
                .load()
                .migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO pay_cycle (code, start_date, end_date, kind, lifecycle, "
                    + "start_adjusted, end_adjusted, version) VALUES (UNHEX(REPLACE(UUID(), '-', '')), "
                    + "'2031-01-01', '2031-02-01', 'TRANSITION', 'ACTIVE', 0, 0, 0)");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldAllowOneActiveEnvelopePerCategoryAndCycleAndManyDeletedOnes() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeUpdate(envelope("11", "100.00", 1)));
            assertThrows(SQLException.class, () -> statement.executeUpdate(envelope("11", "50.00", 1)));
            assertDoesNotThrow(() -> statement.executeUpdate(envelope("22", "50.00", 1)));
            assertDoesNotThrow(() -> statement.executeUpdate(envelope("11", "50.00", 0)));
            assertDoesNotThrow(() -> statement.executeUpdate(envelope("11", "60.00", 0)));
        }
    }

    @Test
    void shouldRejectANonPositivePlannedAmountAndAnUnknownCycle() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(envelope("33", "0.00", 1)));
            assertThrows(SQLException.class, () -> statement.executeUpdate(envelope("33", "-1.00", 1)));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO budget_envelope (code, pay_cycle_id, category_code, planned_amount) VALUES "
                            + "(UNHEX(REPLACE(UUID(), '-', '')), 999999, UNHEX(REPLACE(UUID(), '-', '')), 10.00)"));
        }
    }

    private static String envelope(final String categoryHex, final String amount, final int active) {
        return "INSERT INTO budget_envelope (code, pay_cycle_id, category_code, planned_amount, flag_active) VALUES "
                + "(UNHEX(REPLACE(UUID(), '-', '')), " + CYCLE + ", UNHEX(RPAD('" + categoryHex + "', 32, '0')), "
                + amount + ", " + active + ")";
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
