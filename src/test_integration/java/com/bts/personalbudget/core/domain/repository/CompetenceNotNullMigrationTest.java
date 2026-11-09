package com.bts.personalbudget.core.domain.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs V27 on the migrated schema: competence_date must be NOT NULL, so a movement inserted
 * without it is rejected by the database.
 */
class CompetenceNotNullMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrate() {
        MYSQL.start();
        flyway(null).migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldMakeCompetenceDateNotNull() throws SQLException {
        try (Connection connection = connection();
             ResultSet rs = connection.createStatement().executeQuery("""
                     SELECT is_nullable FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'financial_movement'
                       AND column_name = 'competence_date'""")) {
            assertTrue(rs.next());
            assertEquals("NO", rs.getString(1));
        }
    }

    @Test
    void shouldRejectMovementWithoutCompetenceDate() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO financial_movement (code, operation_type, description, amount, amount_paid,
                        movement_date, due_date, status, flag_active)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'NO_COMPETENCE', -10.00, 0,
                        '2025-03-05', '2025-03-05', 'PENDING', 1)"""));
        }
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
