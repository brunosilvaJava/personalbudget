package com.bts.personalbudget.core.domain.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstallmentScheduleMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void start() {
        MYSQL.start();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldMovePendingNextInstallmentToThePurchaseDayAndKeepTheOthers() throws SQLException {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("25")
                .load()
                .migrate();

        insert("Carne pendente", "PENDING", LocalDate.of(2026, 1, 31), 5, 1, LocalDate.of(2026, 3, 3), true);
        insert("Carne concluido", "DONE", LocalDate.of(2026, 1, 10), 2, 2, null, true);
        insert("Carne inativo", "PENDING", LocalDate.of(2026, 1, 10), 5, 1, LocalDate.of(2026, 3, 7), false);

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();

        // Purchase on 31/01 with one installment posted: the next one is the 2nd month after the purchase.
        assertEquals(LocalDate.of(2026, 3, 31), nextInstallmentDate("Carne pendente"));
        assertNull(nextInstallmentDate("Carne concluido"));
        assertEquals(LocalDate.of(2026, 3, 7), nextInstallmentDate("Carne inativo"));
    }

    private static void insert(final String description, final String status, final LocalDate purchaseDate,
                               final int installmentTotal, final int installmentCount,
                               final LocalDate nextInstallmentDate, final boolean active) throws SQLException {
        final String sql = "INSERT INTO installment_bill (code, description, amount, operation_type, status, "
                + "purchase_date, installment_total, installment_count, next_installment_date, flag_active) "
                + "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 10.00, 'DEBIT', ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, description);
            statement.setString(2, status);
            statement.setObject(3, purchaseDate);
            statement.setInt(4, installmentTotal);
            statement.setInt(5, installmentCount);
            statement.setObject(6, nextInstallmentDate);
            statement.setBoolean(7, active);
            statement.executeUpdate();
        }
    }

    private static LocalDate nextInstallmentDate(final String description) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT next_installment_date FROM installment_bill WHERE description = ?")) {
            statement.setString(1, description);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject(1, LocalDate.class);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
