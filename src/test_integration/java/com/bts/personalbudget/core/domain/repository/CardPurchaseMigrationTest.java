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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardPurchaseMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    private static final String CARD = "(SELECT id FROM credit_card WHERE name = 'Cartão teste')";

    @BeforeAll
    static void migrate() throws SQLException {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("24")
                .load()
                .migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO credit_card (code, name, closing_day, due_day, status) VALUES "
                    + "(UNHEX(REPLACE(UUID(), '-', '')), 'Cartão teste', 25, 5, 'ACTIVE')");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void shouldAcceptAValidInvoiceAndRejectADuplicateMonthABadReferenceDayAndDueBeforeClosing() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeUpdate(invoice("2026-11-01", "2026-11-25", "2026-12-05")));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(invoice("2026-11-01", "2026-11-25", "2026-12-05")));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(invoice("2026-12-15", "2026-12-25", "2027-01-05")));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(invoice("2027-01-01", "2027-01-25", "2027-01-10")));
        }
    }

    @Test
    void shouldRejectInstallmentsOutsideOneToSixtyAndNonPositiveAmount() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeUpdate(purchase(1, "10.00")));
            assertDoesNotThrow(() -> statement.executeUpdate(purchase(60, "10.00")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(purchase(0, "10.00")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(purchase(61, "10.00")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(purchase(3, "0.00")));
        }
    }

    @Test
    void shouldRequireThePurchaseTheInvoiceAndTheNumberTogetherOnAMovement() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(invoice("2030-01-01", "2030-01-25", "2030-02-05"));
            statement.executeUpdate(purchase(2, "10.00"));
            final long purchaseId = scalar(statement, "SELECT MAX(id) FROM card_purchase");
            final long invoiceId = scalar(statement, "SELECT id FROM card_invoice WHERE reference_month = '2030-01-01'");

            assertDoesNotThrow(() -> statement.executeUpdate(movement("NULL", "NULL", "NULL")));
            assertDoesNotThrow(() -> statement.executeUpdate(movement(purchaseId + "", invoiceId + "", "1")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(movement(purchaseId + "", "NULL", "1")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(movement(purchaseId + "", invoiceId + "", "NULL")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(movement("NULL", invoiceId + "", "1")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(movement(purchaseId + "", invoiceId + "", "0")));
        }
    }

    @Test
    void shouldAllowOneActivePaymentPerInvoiceAndManyReversedOnesAndRejectNonPositiveAmounts() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(invoice("2031-01-01", "2031-01-25", "2031-02-05"));
            assertDoesNotThrow(() -> statement.executeUpdate(payment("2031-01-01", "100.00", "NULL")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(payment("2031-01-01", "50.00", "NULL")));
            assertDoesNotThrow(() -> statement.executeUpdate(payment("2031-01-01", "100.00", "NOW()")));
            assertDoesNotThrow(() -> statement.executeUpdate(payment("2031-01-01", "100.00", "NOW()")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(payment("2031-01-01", "0.00", "NOW()")));
        }
    }

    private static String payment(final String referenceMonth, final String amount, final String reversedAt) {
        return "INSERT INTO card_invoice_payment (code, card_invoice_id, account_id, payment_date, amount, "
                + "reversed_at) VALUES (UNHEX(REPLACE(UUID(), '-', '')), "
                + "(SELECT id FROM card_invoice WHERE reference_month = '" + referenceMonth + "'), "
                + "(SELECT id FROM account WHERE is_default = 1), '2031-02-05', " + amount + ", " + reversedAt + ")";
    }

    private static String invoice(final String referenceMonth, final String closing, final String due) {
        return "INSERT INTO card_invoice (code, credit_card_id, reference_month, closing_date, due_date) VALUES "
                + "(UNHEX(REPLACE(UUID(), '-', '')), " + CARD + ", '" + referenceMonth + "', '" + closing + "', '"
                + due + "')";
    }

    private static String purchase(final int installments, final String amount) {
        return "INSERT INTO card_purchase (code, credit_card_id, description, purchase_date, installments, "
                + "installment_amount) VALUES (UNHEX(REPLACE(UUID(), '-', '')), " + CARD + ", 'Compra', '2026-11-09', "
                + installments + ", " + amount + ")";
    }

    private static String movement(final String purchaseId, final String invoiceId, final String number) {
        return "INSERT INTO financial_movement (code, flag_active, operation_type, description, amount, "
                + "movement_date, due_date, status, competence_overridden, card_purchase_id, card_invoice_id, "
                + "installment_number) VALUES (UNHEX(REPLACE(UUID(), '-', '')), 1, 'DEBIT', 'Parcela', -10.00, "
                + "'2026-11-09 00:00:00', '2026-12-05 00:00:00', 'PENDING', 0, " + purchaseId + ", " + invoiceId
                + ", " + number + ")";
    }

    private static long scalar(final Statement statement, final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
