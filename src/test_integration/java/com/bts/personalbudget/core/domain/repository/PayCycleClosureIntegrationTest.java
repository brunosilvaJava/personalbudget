package com.bts.personalbudget.core.domain.repository;

import static com.bts.personalbudget.core.domain.enumerator.BusinessWeek.MONDAY_TO_FRIDAY;
import static com.bts.personalbudget.core.domain.enumerator.DateRuleType.FIXED_DAY;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;
import static com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.POSTPONE;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.REGULAR;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.CLOSED;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleMode.PAY_CYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleClosureService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSnapshot;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummaryService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleConfigRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSnapshotService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@org.springframework.context.annotation.Import(PayCycleClosureIntegrationTest.FixedClockConfig.class)
class PayCycleClosureIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        Clock payCycleClosureTestClock() {
            return Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    private static final UUID CYCLE_CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000321");
    private static final UUID SCHEDULE_CODE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000321");
    private static final UUID MOVEMENT_CODE = UUID.fromString("cccccccc-0000-0000-0000-000000000321");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

    @Container
    static final MySQLContainer<MySQLContainerConfig> MYSQL = MySQLContainerConfig.getInstance();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PayCycleConfigRepository configs;
    @Autowired private IncomeScheduleRepository schedules;
    @Autowired private PayCycleRepository cycles;
    @Autowired private FinancialMovementRepository movements;
    @Autowired private PayCycleClosureService closureService;
    @Autowired private PayCycleSnapshotService snapshotService;
    @Autowired private FinancialMovementService movementService;
    @Autowired private PayCycleMapper mapper;
    @MockBean private PayCycleSummaryService summaryService;

    private IncomeScheduleEntity anchor;

    @BeforeEach
    void setUp() {
        cleanData();
        anchor = schedules.saveAndFlush(IncomeScheduleEntity.builder()
                .code(SCHEDULE_CODE)
                .description("Salário")
                .amount(new BigDecimal("5000.00"))
                .ruleType(FIXED_DAY)
                .ruleValue(5)
                .businessWeek(MONDAY_TO_FRIDAY)
                .nonBusinessDayPolicy(POSTPONE)
                .startMonth(LocalDate.of(2026, 1, 1))
                .anchor(true)
                .status(IncomeScheduleStatus.ACTIVE)
                .build());
        final PayCycleConfigEntity config = configs.findSingle().orElseThrow();
        config.setMode(PAY_CYCLE);
        config.setAnchorSchedule(anchor);
        config.setActivationDate(START);
        configs.saveAndFlush(config);
        cycles.saveAndFlush(PayCycleEntity.builder()
                .code(CYCLE_CODE)
                .startDate(START)
                .endDate(END)
                .kind(REGULAR)
                .anchorSchedule(anchor)
                .anchorNominalMonth(START)
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .version(0L)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanData();
    }

    @Test
    void shouldCloseAndPersistTheSnapshotAtTheNewCycleVersion() {
        stubSummary(List.of());

        final var result = closureService.close(CYCLE_CODE, 0L);
        final PayCycleEntity stored = cycles.findByCode(CYCLE_CODE).orElseThrow();
        final List<PayCycleSnapshot> snapshots = snapshotService.findSnapshots(CYCLE_CODE);

        assertThat(stored.getLifecycle()).isEqualTo(CLOSED);
        assertThat(stored.getVersion()).isEqualTo(1L);
        assertThat(result.snapshot().cycleVersion()).isEqualTo(stored.getVersion());
        assertThat(snapshots).extracting(PayCycleSnapshot::snapshotVersion).containsExactly(1);
    }

    @Test
    void shouldRollbackLifecycleAndSnapshotWhenProjectionHasPendencies() {
        stubSummary(List.of(new PayCycleSummary.Pending(
                PayCycleSummary.Pending.Source.PAY_CYCLE,
                PayCycleSummary.Pending.Type.NO_CURRENT_CYCLE, CYCLE_CODE, null, null, null, null)));

        assertThatThrownBy(() -> closureService.close(CYCLE_CODE, 0L))
                .isInstanceOf(com.bts.personalbudget.exception.ConflictException.class);

        assertThat(cycles.findByCode(CYCLE_CODE).orElseThrow().getLifecycle()).isEqualTo(PayCycleLifecycle.ACTIVE);
        assertThat(cycles.findByCode(CYCLE_CODE).orElseThrow().getVersion()).isZero();
        assertThat(snapshotService.findSnapshots(CYCLE_CODE)).isEmpty();
    }

    @Test
    void shouldRejectStaleVersionAndAllowReopenWithTheCurrentVersion() {
        stubSummary(List.of());
        assertThatThrownBy(() -> closureService.close(CYCLE_CODE, 7L))
                .isInstanceOf(com.bts.personalbudget.exception.ConflictException.class);
        assertThat(cycles.findByCode(CYCLE_CODE).orElseThrow().getLifecycle()).isEqualTo(PayCycleLifecycle.ACTIVE);
        assertThat(snapshotService.findSnapshots(CYCLE_CODE)).isEmpty();

        closureService.close(CYCLE_CODE, 0L);
        final PayCycleView reopened = closureService.reopen(CYCLE_CODE, 1L);

        assertThat(reopened.cycle().lifecycle()).isEqualTo(PayCycleLifecycle.ACTIVE);
        assertThat(reopened.cycle().version()).isEqualTo(2L);
        assertThat(snapshotService.findSnapshots(CYCLE_CODE)).hasSize(1);
    }

    @Test
    void shouldBlockBudgetMutationButAllowPaymentAndAllowEditsAfterReopening() {
        movements.saveAndFlush(movement());
        stubSummary(List.of());
        closureService.close(CYCLE_CODE, 0L);

        final FinancialMovementUpdate payment = new FinancialMovementUpdate(
                MOVEMENT_CODE, null, null, null, new BigDecimal("100.00"), null, null,
                TODAY.atStartOfDay(), PAID_OUT, null, null, false);
        movementService.update(payment);
        assertThat(movements.findByCodeAndFlagActiveTrue(MOVEMENT_CODE).orElseThrow().getStatus())
                .isEqualTo(PAID_OUT);

        final FinancialMovementUpdate amountChange = new FinancialMovementUpdate(
                MOVEMENT_CODE, null, null, new BigDecimal("200.00"), null, null, null,
                null, null, null, null, false);
        assertThatThrownBy(() -> movementService.update(amountChange))
                .isInstanceOf(com.bts.personalbudget.exception.ConflictException.class);
        assertThatThrownBy(() -> movementService.delete(MOVEMENT_CODE))
                .isInstanceOf(com.bts.personalbudget.exception.ConflictException.class);

        closureService.reopen(CYCLE_CODE, 1L);
        movementService.update(amountChange);
        assertThat(movements.findByCodeAndFlagActiveTrue(MOVEMENT_CODE).orElseThrow().getAmount())
                .isEqualByComparingTo("200.00");
    }

    private void stubSummary(final List<PayCycleSummary.Pending> pending) {
        when(summaryService.summarize(CYCLE_CODE)).thenAnswer(invocation -> {
            final PayCycleEntity cycleEntity = cycles.findByCode(CYCLE_CODE).orElseThrow();
            final var cycle = mapper.toModel(cycleEntity);
            return new PayCycleSummary(
                    new PayCycleView(cycle, cycle.status(TODAY), List.of()),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), pending);
        });
    }

    private FinancialMovementEntity movement() {
        return FinancialMovementEntity.builder()
                .code(MOVEMENT_CODE)
                .flagActive(true)
                .operationType(CREDIT)
                .description("Movimentação do ciclo")
                .amount(new BigDecimal("100.00"))
                .amountPaid(BigDecimal.ZERO)
                .movementDate(LocalDateTime.of(2026, 10, 15, 0, 0))
                .dueDate(LocalDateTime.of(2026, 10, 15, 0, 0))
                .status(PENDING)
                .competenceDate(LocalDate.of(2026, 10, 15))
                .competenceOverridden(false)
                .build();
    }

    private void cleanData() {
        jdbc.update("DELETE FROM financial_movement WHERE code = UUID_TO_BIN(?)", MOVEMENT_CODE.toString());
        jdbc.update("DELETE FROM pay_cycle_snapshot_category WHERE snapshot_id IN "
                + "(SELECT id FROM pay_cycle_snapshot WHERE pay_cycle_id IN "
                + "(SELECT id FROM pay_cycle WHERE code = UUID_TO_BIN(?)))", CYCLE_CODE.toString());
        jdbc.update("DELETE FROM pay_cycle_snapshot WHERE pay_cycle_id IN "
                + "(SELECT id FROM pay_cycle WHERE code = UUID_TO_BIN(?))", CYCLE_CODE.toString());
        jdbc.update("DELETE FROM pay_cycle WHERE code = UUID_TO_BIN(?)", CYCLE_CODE.toString());
        jdbc.update("UPDATE pay_cycle_config SET mode = 'CALENDAR_MONTH', anchor_schedule_id = NULL, "
                + "activation_date = NULL, version = 0");
        jdbc.update("DELETE FROM income_schedule WHERE code = UUID_TO_BIN(?)", SCHEDULE_CODE.toString());
    }
}
