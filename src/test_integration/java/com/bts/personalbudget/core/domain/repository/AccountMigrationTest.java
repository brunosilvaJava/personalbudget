package com.bts.personalbudget.core.domain.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrateWithLegacySettlements() throws SQLException {
        MYSQL.start();
        flyway("17").migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO financial_movement (
                        code, operation_type, description, amount, amount_paid, movement_date,
                        due_date, pay_date, status, flag_active, recurrence_bill_code,
                        competence_date, competence_overridden
                    ) VALUES (
                        UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'LEGACY', -100.00, -40.00,
                        '2025-03-01 00:00:00', '2025-03-10 00:00:00', '2025-03-05 00:00:00',
                        'PAID_OUT', 1, NULL, '2025-03-01', 0
                    )""");
        }
        flyway("19").migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldCreateASingleActiveDefaultAccountAndLinkEveryExistingSettlementToIt() throws SQLException {
        assertEquals(1, scalar("SELECT COUNT(*) FROM account WHERE is_default = 1 AND status = 'ACTIVE'"));
        assertEquals("Conta principal", text("SELECT name FROM account WHERE is_default = 1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM movement_settlement"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM movement_settlement WHERE account_id IS NULL"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM movement_settlement ms "
                + "JOIN account a ON a.id = ms.account_id WHERE a.is_default = 1"));
    }

    @Test
    void shouldStartTheDefaultAccountWithZeroInitialBalanceBeforeAnySettlement() throws SQLException {
        assertEquals(0, scalar("SELECT COUNT(*) FROM account WHERE is_default = 1 AND initial_balance <> 0"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM movement_settlement ms "
                + "JOIN account a ON a.id = ms.account_id WHERE ms.effective_date < a.initial_balance_date"));
    }

    @Test
    void shouldRejectASecondDefaultAccountAndADefaultInactiveAccount() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Outra padrão", "ACTIVE", 1)));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert("Padrão inativa", "INACTIVE", 1)));
        }
    }

    @Test
    void shouldRejectDuplicateAccountNamesAndSettlementWithoutAccount() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(insert("Conta principal", "ACTIVE", 0)));
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO movement_settlement (code, financial_movement_id, amount, effective_date, account_id)
                    SELECT UNHEX(REPLACE(UUID(), '-', '')), id, 1.00, '2025-03-06', NULL
                    FROM financial_movement LIMIT 1"""));
        }
    }

    private static String insert(final String name, final String status, final int isDefault) {
        return "INSERT INTO account (code, name, type, initial_balance, initial_balance_date, status, is_default) "
                + "VALUES (UNHEX(REPLACE(UUID(), '-', '')), '" + name + "', 'CASH', 0, '2026-01-01', '"
                + status + "', " + isDefault + ")";
    }

    private static long scalar(final String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String text(final String sql) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
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
