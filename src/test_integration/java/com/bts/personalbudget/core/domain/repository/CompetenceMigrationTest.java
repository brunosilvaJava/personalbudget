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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs V8 over data written under the V4 schema (before occurrence_date, categories and
 * competence existed) instead of an empty database, so the backfill rules are exercised on
 * representative legacy rows, including the duplicates V5 leaves without occurrence_date.
 */
class CompetenceMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @BeforeAll
    static void migrateLegacyData() throws SQLException {
        MYSQL.start();
        flyway("4").migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            // 1: standalone, due date different from movement date.
            insert(statement, "STANDALONE", "DEBIT", "10.00", null, "2025-03-05 18:00:00", "2025-03-20 00:00:00",
                    "PENDING", 1, null);
            // 2: standalone, soft deleted.
            insert(statement, "DELETED", "DEBIT", "11.00", null, "2025-03-06 10:00:00", "2025-03-06 10:00:00",
                    "PENDING", 0, null);
            // 3: standalone paid with an amount_paid different from amount.
            insert(statement, "PAID_DIFFERENT", "DEBIT", "-100.00", "-80.00", "2025-03-07 10:00:00",
                    "2025-03-10 00:00:00", "PAID_OUT", 1, null);
            insert(statement, "PARTIAL_CREDIT", "CREDIT", "100.00", "40.00", "2025-03-08 10:00:00",
                    "2025-03-11 00:00:00", "PAID_OUT", 1, null);
            insert(statement, "FULL_DEBIT", "DEBIT", "-25.00", "-25.00", "2025-03-09 10:00:00",
                    "2025-03-12 00:00:00", "PAID_OUT", 1, null);
            insert(statement, "FULL_CREDIT", "CREDIT", "30.00", "30.00", "2025-03-10 10:00:00",
                    "2025-03-13 00:00:00", "PAID_OUT", 1, null);
            // 4 and 5: duplicate occurrence of the same recurrence bill (V5 keeps the lowest id).
            insert(statement, "DUPLICATE_KEPT", "DEBIT", "50.00", null, "2025-05-12 00:00:00", "2025-05-12 00:00:00",
                    "PENDING", 1, "0x11111111111111111111111111111111");
            insert(statement, "DUPLICATE_RELEASED", "DEBIT", "50.00", null, "2025-05-12 00:00:00",
                    "2025-05-12 00:00:00", "PENDING", 1, "0x11111111111111111111111111111111");
            // 6: recurring with a distinct occurrence whose due date was edited afterwards.
            insert(statement, "EDITED_DUE", "DEBIT", "50.00", null, "2025-04-10 00:00:00", "2025-04-10 00:00:00",
                    "PENDING", 1, "0x22222222222222222222222222222222");
        }
        flyway("7").migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE financial_movement
                    SET movement_date = '2025-03-01 10:00:00', due_date = '2025-06-20 00:00:00'
                    WHERE description = 'EDITED_DUE'""");
        }
        // Stops before V27: the released duplicate keeps competence NULL, which V27 rejects by design.
        flyway("25").migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldBackfillStandaloneMovementsWithMovementDateAsAutomaticCompetence() throws SQLException {
        assertEquals(LocalDate.of(2025, 3, 5), competence("STANDALONE"));
        assertEquals(LocalDate.of(2025, 3, 6), competence("DELETED"));
        assertFalse(overridden("STANDALONE"));
        assertFalse(overridden("DELETED"));
    }

    @Test
    void shouldBackfillRecurringMovementsWithOccurrenceDate() throws SQLException {
        assertEquals(LocalDate.of(2025, 5, 12), occurrence("DUPLICATE_KEPT"));
        assertEquals(LocalDate.of(2025, 5, 12), competence("DUPLICATE_KEPT"));
        assertEquals(LocalDate.of(2025, 4, 10), occurrence("EDITED_DUE"));
        assertEquals(LocalDate.of(2025, 4, 10), competence("EDITED_DUE"));
        assertFalse(overridden("EDITED_DUE"));
    }

    @Test
    void shouldLeaveCompetenceNullAndIdempotencyKeyUntouchedWhenRecurringMovementHasNoOccurrenceDate()
            throws SQLException {
        assertNull(occurrence("DUPLICATE_RELEASED"));
        assertNull(competence("DUPLICATE_RELEASED"));
        assertFalse(overridden("DUPLICATE_RELEASED"));
    }

    @Test
    void shouldPreserveLegacyPartialPaymentStatusAndPayDate() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT amount, amount_paid, pay_date, status FROM financial_movement "
                             + "WHERE description = 'PAID_DIFFERENT'")) {
            assertTrue(rs.next());
            assertEquals(0, new BigDecimal("-100.00").compareTo(rs.getBigDecimal("amount")));
            assertEquals(0, new BigDecimal("-80.00").compareTo(rs.getBigDecimal("amount_paid")));
            assertEquals(LocalDate.of(2025, 3, 10), rs.getObject("pay_date", LocalDate.class));
            assertEquals("PAID_OUT", rs.getString("status"));
            assertEquals(LocalDate.of(2025, 3, 7), competence("PAID_DIFFERENT"));
        }
        assertEquals(0, new BigDecimal("80.00").compareTo(settlementAmount("PAID_DIFFERENT")));
        assertEquals(LocalDate.of(2025, 3, 10), settlementDate("PAID_DIFFERENT"));
    }

    @Test
    void shouldBackfillFullAndPartialSettlementsForBothOperationTypes() throws SQLException {
        assertEquals(0, new BigDecimal("40.00").compareTo(settlementAmount("PARTIAL_CREDIT")));
        assertEquals(0, new BigDecimal("25.00").compareTo(settlementAmount("FULL_DEBIT")));
        assertEquals(0, new BigDecimal("30.00").compareTo(settlementAmount("FULL_CREDIT")));
        assertEquals("PAID_OUT", status("PARTIAL_CREDIT"));
        assertEquals("PAID_OUT", status("FULL_DEBIT"));
        assertEquals("PAID_OUT", status("FULL_CREDIT"));
        assertEquals(0, new BigDecimal("40.00").compareTo(amountPaid("PARTIAL_CREDIT")));
        assertEquals(0, new BigDecimal("-25.00").compareTo(amountPaid("FULL_DEBIT")));
    }

    @Test
    void shouldInitializeZeroAggregateAndDeriveLateStatusForUnpaidOverdueMovement() throws SQLException {
        assertEquals(0, BigDecimal.ZERO.compareTo(amountPaid("STANDALONE")));
        assertEquals("LATE", status("STANDALONE"));
    }

    @Test
    void shouldKeepCompetenceNullableUntilLegacyRowsAreReconciled() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT is_nullable FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'financial_movement'
                       AND column_name = 'competence_date'""")) {
            assertTrue(rs.next());
            assertEquals("YES", rs.getString(1));
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

    private static void insert(final Statement statement, final String description, final String operationType,
                               final String amount, final String amountPaid, final String movementDate,
                               final String dueDate, final String status, final int flagActive,
                               final String recurrenceBillCodeHex) throws SQLException {
        final boolean paid = "PAID_OUT".equals(status);
        statement.executeUpdate("""
                INSERT INTO financial_movement (code, operation_type, description, amount, amount_paid,
                    movement_date, due_date, pay_date, status, flag_active, recurrence_bill_code)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), '%s', '%s', %s, %s, '%s', '%s', %s, '%s', %d, %s)"""
                .formatted(operationType, description, amount, amountPaid == null ? "NULL" : amountPaid,
                        movementDate, dueDate, paid ? "'" + dueDate + "'" : "NULL", status, flagActive,
                        recurrenceBillCodeHex == null ? "NULL" : recurrenceBillCodeHex));
    }

    private static LocalDate competence(final String description) throws SQLException {
        return date(description, "competence_date");
    }

    private static LocalDate occurrence(final String description) throws SQLException {
        return date(description, "occurrence_date");
    }

    private static LocalDate date(final String description, final String column) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT " + column + " FROM financial_movement WHERE description = '" + description + "'")) {
            assertTrue(rs.next());
            return rs.getObject(1, LocalDate.class);
        }
    }

    private static boolean overridden(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT competence_overridden FROM financial_movement WHERE description = '" + description + "'")) {
            assertTrue(rs.next());
            return rs.getBoolean(1);
        }
    }

    private static BigDecimal settlementAmount(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT s.amount FROM movement_settlement s
                     JOIN financial_movement fm ON fm.id = s.financial_movement_id
                     WHERE fm.description = '%s' AND s.reversed_at IS NULL""".formatted(description))) {
            assertTrue(rs.next());
            return rs.getBigDecimal(1);
        }
    }

    private static LocalDate settlementDate(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT s.effective_date FROM movement_settlement s
                     JOIN financial_movement fm ON fm.id = s.financial_movement_id
                     WHERE fm.description = '%s' AND s.reversed_at IS NULL""".formatted(description))) {
            assertTrue(rs.next());
            return rs.getObject(1, LocalDate.class);
        }
    }

    private static String status(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT status FROM financial_movement WHERE description = '%s'".formatted(description))) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private static BigDecimal amountPaid(final String description) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT amount_paid FROM financial_movement WHERE description = '%s'".formatted(description))) {
            assertTrue(rs.next());
            return rs.getBigDecimal(1);
        }
    }
}
