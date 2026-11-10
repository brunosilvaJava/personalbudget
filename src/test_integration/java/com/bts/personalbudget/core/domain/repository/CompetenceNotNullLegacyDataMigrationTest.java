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

/**
 * V27 must not backfill competence_date silently: a legacy movement with NULL competence makes
 * the migration fail, so the reconciliation is done by hand first.
 */
class CompetenceNotNullLegacyDataMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrateLegacyData() throws SQLException {
        MYSQL.start();
        flyway("25").migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO financial_movement (code, operation_type, description, amount, amount_paid,
                        movement_date, due_date, status, flag_active, recurrence_bill_code)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'LEGACY_NO_COMPETENCE', -50.00, 0,
                        '2025-05-12', '2025-05-12', 'PENDING', 1, UNHEX('11111111111111111111111111111111'))""");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldFailMigrationWhenLegacyMovementHasNoCompetence() {
        assertThrows(FlywayException.class, () -> flyway(null).migrate());
    }

    private static Flyway flyway(final String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(target == null ? "latest" : target)
                .load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
