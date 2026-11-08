package com.bts.personalbudget.core.domain.repository;

import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.TRANSITION;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummaryService;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@org.springframework.context.annotation.Import(PayCycleSummaryIntegrationTest.FixedClockConfig.class)
class PayCycleSummaryIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock payCycleSummaryTestClock() {
            return Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    private static final UUID CYCLE_CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID INSTALLMENT_CODE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID INSTALLMENT_MOVEMENT_CODE = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID PAID_MOVEMENT_CODE = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID OUTSIDE_MOVEMENT_CODE = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END_EXCLUSIVE = LocalDate.of(2027, 1, 1);

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayCycleRepository payCycleRepository;

    @Autowired
    private FinancialMovementRepository financialMovementRepository;

    @Autowired
    private InstallmentBillService installmentBillService;

    @Autowired
    private PayCycleSummaryService payCycleSummaryService;

    @BeforeEach
    void setUp() {
        cleanData();
        payCycleRepository.saveAndFlush(PayCycleEntity.builder()
                .code(CYCLE_CODE)
                .startDate(START)
                .endDate(END_EXCLUSIVE)
                .kind(TRANSITION)
                .lifecycle(ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .version(0L)
                .build());

        installmentBillService.create(new InstallmentBill(
                INSTALLMENT_CODE,
                DEBIT,
                "Installment projection",
                new BigDecimal("40.00"),
                InstallmentBillStatus.PENDING,
                LocalDate.of(2026, 10, 16),
                2,
                0,
                null,
                LocalDate.of(2026, 11, 15)));

        financialMovementRepository.saveAndFlush(movement(
                INSTALLMENT_MOVEMENT_CODE, DEBIT, "-40.00", null, PENDING,
                INSTALLMENT_CODE, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)));
        financialMovementRepository.saveAndFlush(movement(
                PAID_MOVEMENT_CODE, CREDIT, "500.00", "500.00", PAID_OUT,
                null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 5)));
        financialMovementRepository.saveAndFlush(movement(
                OUTSIDE_MOVEMENT_CODE, CREDIT, "900.00", null, PENDING,
                null, END_EXCLUSIVE, END_EXCLUSIVE));
    }

    @AfterEach
    void tearDown() {
        cleanData();
    }

    @Test
    void shouldSummarizeCompetenceAndAvoidCountingMaterializedInstallmentTwice() {
        final PayCycleSummary summary = payCycleSummaryService.summarize(CYCLE_CODE);

        assertThat(summary.recognizedRevenue()).isEqualByComparingTo("500.00");
        assertThat(summary.recognizedExpense()).isEqualByComparingTo("0");
        assertThat(summary.projectedRevenue()).isEqualByComparingTo("500.00");
        // The Nov 15 installment is materialized; only its Dec 15 virtual installment is added.
        assertThat(summary.projectedExpense()).isEqualByComparingTo("80.00");
        assertThat(summary.categories()).hasSize(2)
                .allSatisfy(category -> assertThat(category.categoryName()).isEqualTo("Sem categoria"));
        assertThat(summary.pending()).isEmpty();
        assertThat(summary.projectionIncomplete()).isFalse();
    }

    private static FinancialMovementEntity movement(final UUID code,
                                                    final com.bts.personalbudget.core.domain.enumerator.OperationType operationType,
                                                    final String amount,
                                                    final String amountPaid,
                                                    final com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus status,
                                                    final UUID recurrenceBillCode,
                                                    final LocalDate competenceDate,
                                                    final LocalDate cashDate) {
        final LocalDateTime dueDate = competenceDate.atStartOfDay();
        return FinancialMovementEntity.builder()
                .code(code)
                .flagActive(true)
                .operationType(operationType)
                .description("Cycle summary integration test")
                .amount(new BigDecimal(amount))
                .amountPaid(amountPaid == null ? null : new BigDecimal(amountPaid))
                .movementDate(dueDate)
                .dueDate(dueDate)
                .payDate(status == PAID_OUT ? cashDate.atStartOfDay() : null)
                .status(status)
                .recurrenceBillCode(recurrenceBillCode)
                .occurrenceDate(recurrenceBillCode == null ? null : competenceDate)
                .competenceDate(competenceDate)
                .competenceOverridden(false)
                .build();
    }

    private void cleanData() {
        jdbcTemplate.update("DELETE FROM financial_movement WHERE code IN (?, ?, ?)",
                INSTALLMENT_MOVEMENT_CODE, PAID_MOVEMENT_CODE, OUTSIDE_MOVEMENT_CODE);
        jdbcTemplate.update("DELETE FROM pay_cycle WHERE code = ?", CYCLE_CODE);
        jdbcTemplate.update("DELETE FROM installment_bill WHERE code = ?", INSTALLMENT_CODE);
        jdbcTemplate.update("UPDATE pay_cycle_config SET mode = 'CALENDAR_MONTH', anchor_schedule_id = NULL, "
                + "activation_date = NULL, version = 0");
    }
}
