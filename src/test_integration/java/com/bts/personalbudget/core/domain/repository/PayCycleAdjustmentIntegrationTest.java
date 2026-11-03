package com.bts.personalbudget.core.domain.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleAdjustmentEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentResult;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleAdjustmentRepository;
import com.bts.personalbudget.repository.PayCycleConfigRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V12 (the history of the pay cycle adjustments) and the E4b operations against a real MySQL: the
 * CHECKs and foreign keys, the history queries, and the service end to end, with the clock fixed on
 * 2026-11-02 and the chain Oct 5 to Nov 5, Nov 5 to Dec 7, Dec 7 to Jan 5.
 */
@Testcontainers
@SpringBootTest
@Import(PayCycleRepositoryTest.FixedClockConfig.class)
class PayCycleAdjustmentIntegrationTest {

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);
    private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEC = LocalDate.of(2026, 12, 1);

    private static final String JUSTIFICATION = "O salário caiu mais tarde neste mês";

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private PayCycleService payCycleService;

    @Autowired
    private FinancialMovementService financialMovementService;

    @Autowired
    private HolidayCalendarService holidayCalendarService;

    @Autowired
    private PayCycleRepository cycleRepository;

    @Autowired
    private PayCycleAdjustmentRepository adjustmentRepository;

    @Autowired
    private PayCycleConfigRepository configRepository;

    @Autowired
    private IncomeScheduleRepository scheduleRepository;

    @Autowired
    private IncomeOccurrenceRepository occurrenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdMovements = new ArrayList<>();

    private IncomeScheduleEntity anchor;
    private PayCycleEntity first;
    private PayCycleEntity second;
    private PayCycleEntity third;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        createdMovements.forEach(financialMovementService::delete);
        createdMovements.clear();
        // The history points to cycles: it goes first (its rows are never deleted by the application).
        jdbcTemplate.update("DELETE FROM pay_cycle_adjustment");
        cycleRepository.deleteAll();
        jdbcTemplate.update("UPDATE pay_cycle_config SET mode = 'CALENDAR_MONTH', anchor_schedule_id = NULL, "
                + "activation_date = NULL, version = 0");
        occurrenceRepository.deleteAll();
        scheduleRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM holiday");
        jdbcTemplate.update("DELETE FROM holiday_calendar_review");
    }

    // ---- V12: the table

    @Test
    void shouldSaveAManualAdjustmentAndReadItBackWithEveryColumn() {
        givenTheChain();
        final PayCycleAdjustmentEntity saved = adjustmentRepository.saveAndFlush(
                manualBoundary(first, second, NOV_5, LocalDate.of(2026, 11, 10), JUSTIFICATION));

        final PayCycleAdjustmentEntity found = adjustmentRepository.findByCycleId(first.getId()).get(0);

        assertEquals(saved.getCode(), found.getCode());
        assertEquals(PayCycleAdjustmentType.MANUAL_BOUNDARY, found.getType());
        assertEquals(first.getCode(), found.getPreviousCycle().getCode());
        assertEquals(second.getCode(), found.getNextCycle().getCode());
        assertEquals(NOV_5, found.getOldBoundary());
        assertEquals(LocalDate.of(2026, 11, 10), found.getNewBoundary());
        assertEquals(JUSTIFICATION, found.getJustification());
        assertEquals(0L, found.getExpectedPreviousVersion());
        assertEquals(0L, found.getExpectedNextVersion());
        assertNull(found.getExpectedConfigVersion());
        assertNotNull(found.getCreatedDate());
    }

    @Test
    void shouldFindTheHistoryOfBothCyclesOfTheBoundaryNewestFirstAndNothingOfTheOthers() {
        givenTheChain();
        final PayCycleAdjustmentEntity older = adjustmentRepository.saveAndFlush(
                manualBoundary(first, second, NOV_5, LocalDate.of(2026, 11, 10), JUSTIFICATION));
        final PayCycleAdjustmentEntity newer = adjustmentRepository.saveAndFlush(
                manualBoundary(first, second, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 8), JUSTIFICATION));
        adjustmentRepository.saveAndFlush(
                manualBoundary(second, third, DEC_7, LocalDate.of(2026, 12, 10), JUSTIFICATION));

        final List<UUID> ofFirst = codes(adjustmentRepository.findByCycleId(first.getId()));
        final List<UUID> ofSecond = codes(adjustmentRepository.findByCycleId(second.getId()));

        assertEquals(List.of(newer.getCode(), older.getCode()), ofFirst);
        assertEquals(3, ofSecond.size());
        assertEquals(newer.getCode(), ofSecond.get(1));
        assertEquals(older.getCode(), ofSecond.get(2));
        assertTrue(adjustmentRepository.findByCycleId(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void shouldAcceptADeactivationWithNoCyclesAtAll() {
        assertDoesNotThrow(() -> adjustmentRepository.saveAndFlush(PayCycleAdjustmentEntity.builder()
                .code(UUID.randomUUID())
                .type(PayCycleAdjustmentType.DEACTIVATION)
                .justification(JUSTIFICATION)
                .expectedConfigVersion(1L)
                .build()));
    }

    @Test
    void shouldRejectAManualAdjustmentWithoutAJustification() {
        givenTheChain();

        assertCheckViolation("ck_pay_cycle_adjustment_justification", () -> adjustmentRepository.saveAndFlush(
                manualBoundary(first, second, NOV_5, LocalDate.of(2026, 11, 10), "   ")));
        assertCheckViolation("ck_pay_cycle_adjustment_justification", () -> adjustmentRepository.saveAndFlush(
                manualBoundary(first, second, NOV_5, LocalDate.of(2026, 11, 10), null)));
    }

    @Test
    void shouldRejectADeactivationWithoutAJustification() {
        assertCheckViolation("ck_pay_cycle_adjustment_justification", () -> adjustmentRepository.saveAndFlush(
                PayCycleAdjustmentEntity.builder()
                        .code(UUID.randomUUID())
                        .type(PayCycleAdjustmentType.DEACTIVATION)
                        .justification("")
                        .build()));
    }

    @Test
    void shouldRejectAManualAdjustmentThatDoesNotMoveTheBoundary() {
        givenTheChain();

        assertCheckViolation("ck_pay_cycle_adjustment_manual_boundary", () -> adjustmentRepository.saveAndFlush(
                manualBoundary(first, second, NOV_5, NOV_5, JUSTIFICATION)));
    }

    @Test
    void shouldRejectAManualAdjustmentWithoutBothCycles() {
        givenTheChain();
        final PayCycleAdjustmentEntity withoutNext = manualBoundary(first, null, NOV_5, LocalDate.of(2026, 11, 10),
                JUSTIFICATION);
        final PayCycleAdjustmentEntity withoutBoundary = manualBoundary(first, second, NOV_5, null, JUSTIFICATION);

        assertCheckViolation("ck_pay_cycle_adjustment_manual_boundary",
                () -> adjustmentRepository.saveAndFlush(withoutNext));
        assertCheckViolation("ck_pay_cycle_adjustment_manual_boundary",
                () -> adjustmentRepository.saveAndFlush(withoutBoundary));
    }

    @Test
    void shouldRejectAnUnknownType() {
        assertCheckViolation("ck_pay_cycle_adjustment_type", () -> jdbcTemplate.update(
                "INSERT INTO pay_cycle_adjustment (code, type, justification) "
                        + "VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'SOMETHING_ELSE', 'x')"));
    }

    @Test
    void shouldNotDeleteACycleThatHasHistory() {
        givenTheChain();
        adjustmentRepository.saveAndFlush(manualBoundary(first, second, NOV_5, LocalDate.of(2026, 11, 10), JUSTIFICATION));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM pay_cycle WHERE id = ?", first.getId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("DELETE FROM pay_cycle WHERE id = ?", second.getId()));
    }

    // ---- the operations: preview

    @Test
    void shouldPreviewTheMovementsAndIncomesThatChangeCycleWithoutWritingAnything() {
        givenTheChain();
        final FinancialMovement beforeTheWindow = createMovement(LocalDate.of(2026, 11, 4));
        final FinancialMovement onTheOldBoundary = createMovement(NOV_5);
        final FinancialMovement inside = createMovement(LocalDate.of(2026, 11, 9));
        final FinancialMovement onTheNewBoundary = createMovement(LocalDate.of(2026, 11, 10));

        final PayCycleEndAdjustmentPreview preview =
                payCycleService.previewEndAdjustment(first.getCode(), LocalDate.of(2026, 11, 10));

        final List<UUID> movements = preview.movements().stream().map(FinancialMovement::code).toList();
        assertTrue(movements.contains(onTheOldBoundary.code()));
        assertTrue(movements.contains(inside.code()));
        assertFalse(movements.contains(beforeTheWindow.code()));
        assertFalse(movements.contains(onTheNewBoundary.code()));
        assertTrue(preview.incomeOccurrences().stream().anyMatch(impact ->
                impact.scheduleCode().equals(anchor.getCode())
                        && impact.occurrence().plannedDate().equals(NOV_5)));
        assertEquals(NOV_5, preview.oldEndDate());
        assertEquals(LocalDate.of(2026, 11, 10), preview.newEndDate());
        assertEquals(second.getCode(), preview.leavingCycleCode());
        assertEquals(first.getCode(), preview.receivingCycleCode());

        assertEquals(NOV_5, find(first).getEndDate());
        assertFalse(find(first).getEndAdjusted());
        assertEquals(0L, find(first).getVersion());
        assertEquals(0, historySize());
    }

    // ---- the operations: apply

    @Test
    void shouldMoveTheBoundaryAndRecordItInTheHistoryOfBothCycles() {
        givenTheChain();

        final PayCycleEndAdjustmentResult result = payCycleService.adjustEnd(
                first.getCode(), LocalDate.of(2026, 11, 10), JUSTIFICATION, 0L, 0L);

        final PayCycleEntity storedFirst = find(first);
        final PayCycleEntity storedSecond = find(second);
        assertEquals(LocalDate.of(2026, 11, 10), storedFirst.getEndDate());
        assertTrue(storedFirst.getEndAdjusted());
        assertEquals(1L, storedFirst.getVersion());
        assertEquals(LocalDate.of(2026, 11, 10), storedSecond.getStartDate());
        assertTrue(storedSecond.getStartAdjusted());
        assertEquals(1L, storedSecond.getVersion());
        assertEquals(1L, result.previous().cycle().version());
        assertEquals(1L, result.next().cycle().version());

        final List<PayCycleAdjustmentEntity> historyOfFirst = adjustmentRepository.findByCycleId(first.getId());
        final List<PayCycleAdjustmentEntity> historyOfSecond = adjustmentRepository.findByCycleId(second.getId());
        assertEquals(1, historyOfFirst.size());
        assertEquals(codes(historyOfFirst), codes(historyOfSecond));
        assertEquals(result.adjustment().code(), historyOfFirst.get(0).getCode());
        assertEquals(PayCycleAdjustmentType.MANUAL_BOUNDARY, historyOfFirst.get(0).getType());
        assertEquals(NOV_5, historyOfFirst.get(0).getOldBoundary());
        assertEquals(LocalDate.of(2026, 11, 10), historyOfFirst.get(0).getNewBoundary());
        assertEquals(JUSTIFICATION, historyOfFirst.get(0).getJustification());
        assertEquals(0L, historyOfFirst.get(0).getExpectedPreviousVersion());
        assertEquals(0L, historyOfFirst.get(0).getExpectedNextVersion());
        assertTrue(adjustmentRepository.findByCycleId(third.getId()).isEmpty());
    }

    @Test
    void shouldLeaveNoGapAndNoOverlapAfterEveryAdjustmentOfThePair() {
        givenTheChain();
        final List<LocalDate> boundaries = List.of(
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 6));

        for (final LocalDate boundary : boundaries) {
            payCycleService.adjustEnd(first.getCode(), boundary, JUSTIFICATION,
                    find(first).getVersion(), find(second).getVersion());

            assertEveryActiveCycleContiguous();
            assertEquals(boundary, find(first).getEndDate());
            assertEquals(boundary, find(second).getStartDate());
        }

        assertEquals(OCT_5, find(first).getStartDate());
        assertEquals(DEC_7, find(second).getEndDate());
        assertEquals(DEC_7, find(third).getStartDate());
        assertEquals(JAN_5, find(third).getEndDate());
        assertEquals(boundaries.size(), adjustmentRepository.findByCycleId(first.getId()).size());
    }

    @Test
    void shouldRejectAStaleVersionOfEitherCycleAndWriteNothing() {
        givenTheChain();
        payCycleService.adjustEnd(first.getCode(), LocalDate.of(2026, 11, 10), JUSTIFICATION, 0L, 0L);

        assertThrows(ConflictException.class, () -> payCycleService.adjustEnd(
                first.getCode(), LocalDate.of(2026, 11, 6), JUSTIFICATION, 0L, 1L));
        assertThrows(ConflictException.class, () -> payCycleService.adjustEnd(
                first.getCode(), LocalDate.of(2026, 11, 6), JUSTIFICATION, 1L, 0L));

        assertEquals(LocalDate.of(2026, 11, 10), find(first).getEndDate());
        assertEquals(1L, find(first).getVersion());
        assertEquals(LocalDate.of(2026, 11, 10), find(second).getStartDate());
        assertEquals(1, historySize());
    }

    @Test
    void shouldRollBackTheBoundaryWhenTheHistoryCannotBeWritten() {
        givenTheChain();
        jdbcTemplate.execute("ALTER TABLE pay_cycle_adjustment "
                + "ADD CONSTRAINT ck_test_forced_failure CHECK (justification <> 'forced failure')");
        try {
            assertThrows(RuntimeException.class, () -> payCycleService.adjustEnd(
                    first.getCode(), LocalDate.of(2026, 11, 10), "forced failure", 0L, 0L));
        } finally {
            jdbcTemplate.execute("ALTER TABLE pay_cycle_adjustment DROP CHECK ck_test_forced_failure");
        }

        assertEquals(NOV_5, find(first).getEndDate());
        assertFalse(find(first).getEndAdjusted());
        assertEquals(0L, find(first).getVersion());
        assertEquals(NOV_5, find(second).getStartDate());
        assertFalse(find(second).getStartAdjusted());
        assertEquals(0, historySize());
    }

    @Test
    void shouldKeepTheAdjustedBoundaryWhenTheChainIsRefreshed() {
        givenTheChain();
        final LocalDate dec10 = LocalDate.of(2026, 12, 10);
        payCycleService.adjustEnd(second.getCode(), dec10, JUSTIFICATION, 0L, 0L);

        payCycleService.refresh();

        assertEquals(dec10, find(second).getEndDate());
        assertTrue(find(second).getEndAdjusted());
        assertEquals(dec10, find(third).getStartDate());
        assertTrue(find(third).getStartAdjusted());
        assertEquals(1L, find(second).getVersion());
        assertEveryActiveCycleContiguous();
    }

    @Test
    void shouldMoveTheSameBoundaryBackOnRefreshWhenItWasNotAdjusted() {
        givenTheChain();
        final LocalDate dec10 = LocalDate.of(2026, 12, 10);
        final PayCycleEntity movedSecond = find(second);
        movedSecond.setEndDate(dec10);
        cycleRepository.saveAndFlush(movedSecond);
        final PayCycleEntity movedThird = find(third);
        movedThird.setStartDate(dec10);
        cycleRepository.saveAndFlush(movedThird);

        payCycleService.refresh();

        assertEquals(DEC_7, find(second).getEndDate());
        assertEquals(DEC_7, find(third).getStartDate());
        assertEveryActiveCycleContiguous();
    }

    // ---- the operations: deactivation

    @Test
    void shouldRecordTheDeactivationInTheHistoryOfTheLastStartedAndTheFirstCancelledCycle() {
        givenTheChain();
        final Long configVersion = configRepository.findSingle().orElseThrow().getVersion();

        payCycleService.deactivate(configVersion, JUSTIFICATION);

        assertEquals(PayCycleLifecycle.ACTIVE, find(first).getLifecycle());
        assertEquals(PayCycleLifecycle.CANCELLED, find(second).getLifecycle());
        assertEquals(PayCycleLifecycle.CANCELLED, find(third).getLifecycle());
        assertEquals(PayCycleMode.CALENDAR_MONTH, configRepository.findSingle().orElseThrow().getMode());

        final List<PayCycleAdjustmentEntity> historyOfFirst = adjustmentRepository.findByCycleId(first.getId());
        final List<PayCycleAdjustmentEntity> historyOfSecond = adjustmentRepository.findByCycleId(second.getId());
        assertEquals(1, historyOfFirst.size());
        assertEquals(codes(historyOfFirst), codes(historyOfSecond));
        final PayCycleAdjustmentEntity recorded = historyOfFirst.get(0);
        assertEquals(PayCycleAdjustmentType.DEACTIVATION, recorded.getType());
        assertEquals(first.getCode(), recorded.getPreviousCycle().getCode());
        assertEquals(second.getCode(), recorded.getNextCycle().getCode());
        assertEquals(NOV_5, recorded.getOldBoundary());
        assertNull(recorded.getNewBoundary());
        assertEquals(JUSTIFICATION, recorded.getJustification());
        assertEquals(configVersion, recorded.getExpectedConfigVersion());
        assertTrue(adjustmentRepository.findByCycleId(third.getId()).isEmpty());
    }

    @Test
    void shouldRollBackTheDeactivationWhenTheHistoryCannotBeWritten() {
        givenTheChain();
        final Long configVersion = configRepository.findSingle().orElseThrow().getVersion();
        jdbcTemplate.execute("ALTER TABLE pay_cycle_adjustment "
                + "ADD CONSTRAINT ck_test_forced_failure CHECK (justification <> 'forced failure')");
        try {
            assertThrows(RuntimeException.class, () -> payCycleService.deactivate(configVersion, "forced failure"));
        } finally {
            jdbcTemplate.execute("ALTER TABLE pay_cycle_adjustment DROP CHECK ck_test_forced_failure");
        }

        assertEquals(PayCycleLifecycle.ACTIVE, find(second).getLifecycle());
        assertEquals(PayCycleLifecycle.ACTIVE, find(third).getLifecycle());
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();
        assertEquals(PayCycleMode.PAY_CYCLE, config.getMode());
        assertEquals(configVersion, config.getVersion());
        assertEquals(0, historySize());
    }

    // ---- helpers

    /**
     * The pay cycles are on, with the anchor on day 5 (postponed to the next business day) and the three
     * cycles the horizon keeps on 2026-11-02: October, November and December.
     */
    private void givenTheChain() {
        anchor = scheduleRepository.save(schedule());
        holidayCalendarService.reviewYear(2026, List.of());
        holidayCalendarService.reviewYear(2027, List.of());
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();
        config.setMode(PayCycleMode.PAY_CYCLE);
        config.setAnchorSchedule(anchor);
        config.setActivationDate(OCT_5);
        configRepository.saveAndFlush(config);
        first = cycleRepository.save(cycle(OCT_5, NOV_5, OCT));
        second = cycleRepository.save(cycle(NOV_5, DEC_7, NOV));
        third = cycleRepository.save(cycle(DEC_7, JAN_5, DEC));
        cycleRepository.flush();
    }

    private PayCycleEntity find(final PayCycleEntity cycle) {
        return cycleRepository.findByCode(cycle.getCode()).orElseThrow();
    }

    private int historySize() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pay_cycle_adjustment", Integer.class);
    }

    private void assertEveryActiveCycleContiguous() {
        final List<PayCycleEntity> active = cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
        for (int i = 0; i < active.size(); i++) {
            assertTrue(active.get(i).getStartDate().isBefore(active.get(i).getEndDate()),
                    "cycle " + i + " is empty or inverted");
            if (i > 0) {
                assertEquals(active.get(i - 1).getEndDate(), active.get(i).getStartDate(),
                        "gap or overlap before cycle " + i);
            }
        }
    }

    private static List<UUID> codes(final List<PayCycleAdjustmentEntity> adjustments) {
        return adjustments.stream().map(PayCycleAdjustmentEntity::getCode).toList();
    }

    private FinancialMovement createMovement(final LocalDate competenceDate) {
        final FinancialMovement created = financialMovementService.create(new FinancialMovement(
                UUID.randomUUID(), OperationType.DEBIT, "Padaria", new BigDecimal("12.50"), null,
                competenceDate.atTime(10, 0), competenceDate.atTime(10, 0), null, FinancialMovementStatus.PENDING,
                Boolean.TRUE, null, null, null, competenceDate, false));
        createdMovements.add(created.code());
        return created;
    }

    /**
     * MySQL answers a CHECK violation with error 3819 and SQLSTATE HY000, which Spring may not classify
     * as a data integrity violation: the test asserts the family of the exception and that the
     * constraint named in MySQL's message is the expected one.
     */
    private static void assertCheckViolation(final String constraint, final Executable action) {
        final DataAccessException exception = assertThrows(DataAccessException.class, action);
        final String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(exception).getMessage());
        assertTrue(cause.contains(constraint), "expected a violation of " + constraint + " but got: " + cause);
    }

    private PayCycleAdjustmentEntity manualBoundary(final PayCycleEntity previous,
                                                    final PayCycleEntity next,
                                                    final LocalDate oldBoundary,
                                                    final LocalDate newBoundary,
                                                    final String justification) {
        return PayCycleAdjustmentEntity.builder()
                .code(UUID.randomUUID())
                .type(PayCycleAdjustmentType.MANUAL_BOUNDARY)
                .previousCycle(previous)
                .nextCycle(next)
                .oldBoundary(oldBoundary)
                .newBoundary(newBoundary)
                .justification(justification)
                .expectedPreviousVersion(0L)
                .expectedNextVersion(0L)
                .build();
    }

    private IncomeScheduleEntity schedule() {
        return IncomeScheduleEntity.builder()
                .code(UUID.randomUUID())
                .description("Salário")
                .amount(new BigDecimal("5000.00"))
                .ruleType(DateRuleType.FIXED_DAY)
                .ruleValue(5)
                .businessWeek(BusinessWeek.MONDAY_TO_FRIDAY)
                .nonBusinessDayPolicy(NonBusinessDayPolicy.POSTPONE)
                .startMonth(LocalDate.of(2026, 6, 1))
                .anchor(true)
                .status(IncomeScheduleStatus.ACTIVE)
                .build();
    }

    private PayCycleEntity cycle(final LocalDate start, final LocalDate end, final LocalDate nominalMonth) {
        return PayCycleEntity.builder()
                .code(UUID.randomUUID())
                .startDate(start)
                .endDate(end)
                .kind(PayCycleKind.REGULAR)
                .anchorSchedule(anchor)
                .anchorNominalMonth(nominalMonth)
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .build();
    }
}
