package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fixed bill whose first occurrence is today posts it on save, inside the request. The movement
 * must be saved with its movement and due dates (it once failed with "Column 'due_date' cannot be
 * null", and POST /fixed_bill answered 500).
 */
@Testcontainers
@SpringBootTest
class FixedBillDueTodayIntegrationTest {

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired
    private FixedBillService fixedBillService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private UUID billCode;

    @AfterEach
    void cleanUp() {
        if (billCode == null) {
            return;
        }
        final String code = billCode.toString();
        jdbc.update("DELETE FROM financial_movement WHERE recurrence_bill_code = UUID_TO_BIN(?)", code);
        jdbc.update("DELETE FROM calendar_fixed_bill WHERE id_fixed_bill = "
                + "(SELECT id FROM fixed_bill WHERE code = UUID_TO_BIN(?))", code);
        jdbc.update("DELETE FROM fixed_bill WHERE code = UUID_TO_BIN(?)", code);
    }

    @Test
    void shouldPostTodaysOccurrenceWithItsDatesWhenTheBillIsSaved() {
        final LocalDate today = LocalDate.now(clock);
        final FixedBill bill = new FixedBill(null, null, OperationType.DEBIT, "Vence hoje (teste)",
                BigDecimal.valueOf(42), RecurrenceType.MONTHLY, Set.of(today.getDayOfMonth()), null,
                FixedBillStatus.ACTIVE, today, today.plusMonths(2), null);

        final FixedBill saved = fixedBillService.save(bill);
        billCode = saved.getCode();

        final Map<String, Object> movement = jdbc.queryForMap(
                "SELECT movement_date, due_date, occurrence_date, status FROM financial_movement "
                        + "WHERE recurrence_bill_code = UUID_TO_BIN(?)", billCode.toString());
        assertThat(((Timestamp) movement.get("movement_date")).toLocalDateTime()).isEqualTo(today.atStartOfDay());
        assertThat(((Timestamp) movement.get("due_date")).toLocalDateTime()).isEqualTo(today.atStartOfDay());
        assertThat(movement.get("occurrence_date").toString()).isEqualTo(today.toString());
        assertThat(saved.getNextDueDate()).isAfter(today);
    }
}
