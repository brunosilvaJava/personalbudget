package com.bts.personalbudget.core.domain.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementSettlementBackfillMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrateAndInsertLegacyMovements() throws SQLException {
        MYSQL.start();
        flyway("17").migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            insert(statement, "UNPAID", "DEBIT", "-100.00", "NULL", "NULL", "PENDING");
            insert(statement, "PARTIAL_DEBIT", "DEBIT", "-100.00", "-25.00",
                    "'2088-04-02 12:00:00'", "PAID_OUT");
            insert(statement, "PARTIAL_CREDIT", "CREDIT", "100.00", "30.00",
                    "'2088-04-03 12:00:00'", "PAID_OUT");
            insert(statement, "FULL_CREDIT", "CREDIT", "100.00", "100.00",
                    "'2088-04-04 12:00:00'", "PAID_OUT");
        }
        flyway("18").migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldBackfillUnpaidPartialDebitPartialCreditAndFullyPaidMovements() throws SQLException {
        assertEquals(0, settlementCount("UNPAID"));
        assertSettlement("PARTIAL_DEBIT", "25.00", LocalDate.of(2088, 4, 2));
        assertSettlement("PARTIAL_CREDIT", "30.00", LocalDate.of(2088, 4, 3));
        assertSettlement("FULL_CREDIT", "100.00", LocalDate.of(2088, 4, 4));
        assertAmountPaid("UNPAID", "0.00");
        assertAmountPaid("PARTIAL_DEBIT", "-25.00");
        assertAmountPaid("PARTIAL_CREDIT", "30.00");
        assertAmountPaid("FULL_CREDIT", "100.00");
        assertEquals("PAID_OUT", status("PARTIAL_DEBIT"));
        assertEquals("PAID_OUT", status("PARTIAL_CREDIT"));
        assertEquals("PAID_OUT", status("FULL_CREDIT"));
        assertEquals(LocalDate.of(2088, 4, 2), payDate("PARTIAL_DEBIT"));
        assertEquals(LocalDate.of(2088, 4, 3), payDate("PARTIAL_CREDIT"));
        assertEquals(LocalDate.of(2088, 4, 4), payDate("FULL_CREDIT"));
    }

    private static void insert(final Statement statement, final String description, final String operationType,
                               final String amount, final String amountPaid, final String payDate,
                               final String status) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO financial_movement (
                    code, operation_type, description, amount, amount_paid, movement_date,
                    due_date, pay_date, status, flag_active, recurrence_bill_code,
                    competence_date, competence_overridden
                ) VALUES (
                    UNHEX(REPLACE(UUID(), '-', '')), '%s', '%s',
                    %s, %s, '2088-04-01 00:00:00', '2088-04-30 00:00:00',
                    %s, '%s', 1, NULL, '2088-04-01', 0
                )""".formatted(operationType, description, amount, amountPaid, payDate, status));
    }

    private static void assertSettlement(final String description,
                                         final String expectedAmount,
                                         final LocalDate expectedDate) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT s.amount, s.effective_date
                     FROM movement_settlement s
                     JOIN financial_movement fm ON fm.id = s.financial_movement_id
                     WHERE fm.description = '%s'""".formatted(description))) {
            assertTrue(rs.next());
            assertEquals(0, new BigDecimal(expectedAmount).compareTo(rs.getBigDecimal("amount")));
            assertEquals(expectedDate, rs.getObject("effective_date", LocalDate.class));
            assertTrue(!rs.next());
        }
    }

    private static int settlementCount(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(*) FROM movement_settlement s
                     JOIN financial_movement fm ON fm.id = s.financial_movement_id
                     WHERE fm.description = '%s'""".formatted(description))) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static void assertAmountPaid(final String description, final String expectedAmount) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT amount_paid FROM financial_movement WHERE description = '" + description + "'")) {
            assertTrue(rs.next());
            assertEquals(0, new BigDecimal(expectedAmount).compareTo(rs.getBigDecimal(1)));
        }
    }

    private static String status(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT status FROM financial_movement WHERE description = '" + description + "'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private static LocalDate payDate(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT pay_date FROM financial_movement WHERE description = '" + description + "'")) {
            assertTrue(rs.next());
            return rs.getObject(1, LocalDate.class);
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
