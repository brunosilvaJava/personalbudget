package com.bts.personalbudget.core.domain.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MovementSettlementMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrateToPreviousVersion() throws SQLException {
        MYSQL.start();
        flyway("17").migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO financial_movement (
                        code, operation_type, description, amount, amount_paid, movement_date,
                        due_date, pay_date, status, flag_active, recurrence_bill_code,
                        competence_date, competence_overridden
                    ) VALUES (
                        UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'MISSING_PAYMENT_DATE',
                        -100.00, -20.00, '2025-03-01 00:00:00', '2025-03-10 00:00:00',
                        NULL, 'PENDING', 1, NULL, '2025-03-01', 0
                    )""");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldStopMigrationWhenLegacyPaymentHasNoEffectiveDate() {
        assertThrows(FlywayException.class, () -> flyway("18").migrate());
    }

    private static Flyway flyway(final String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(target)
                .load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
