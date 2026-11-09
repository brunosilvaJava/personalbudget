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

class CreditCardMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrate() {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("21")
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldAcceptACardWithoutLimitAndWithoutDefaultAccount() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeUpdate(insert("Sem limite", 31, 1, "NULL", "'ACTIVE'")));
        }
    }

    @Test
    void shouldRejectDaysOutsideOneToThirtyOne() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Dia zero", 0, 5, "NULL", "'ACTIVE'")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Dia 32", 25, 32, "NULL", "'ACTIVE'")));
        }
    }

    @Test
    void shouldRejectNonPositiveLimitUnknownStatusAndDuplicateName() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Limite zero", 25, 5, "0", "'ACTIVE'")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Status ruim", 25, 5, "NULL", "'OTHER'")));
            statement.executeUpdate(insert("Repetido", 25, 5, "100.00", "'ACTIVE'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Repetido", 10, 17, "NULL", "'ACTIVE'")));
        }
    }

    private static String insert(final String name, final int closingDay, final int dueDay,
                                 final String limit, final String status) {
        return "INSERT INTO credit_card (code, name, closing_day, due_day, credit_limit, status) VALUES "
                + "(UNHEX(REPLACE(UUID(), '-', '')), '" + name + "', " + closingDay + ", " + dueDay + ", "
                + limit + ", " + status + ")";
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
