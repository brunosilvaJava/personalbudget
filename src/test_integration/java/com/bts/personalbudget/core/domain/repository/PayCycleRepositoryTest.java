package com.bts.personalbudget.core.domain.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleConfigRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pay cycle configuration and cycles against a real MySQL: the single configuration row, the UNIQUE
 * keys over generated active_* columns that keep non-cancelled cycles from sharing an anchor
 * occurrence or a boundary, the CHECKs, optimistic version and locking reads.
 *
 * <p>Cycles and the configuration reference income schedules, so every test leaves the tables as it
 * found them: the other repository tests delete schedules.
 */
@Testcontainers
@SpringBootTest
@org.springframework.context.annotation.Import(PayCycleRepositoryTest.FixedClockConfig.class)
public class PayCycleRepositoryTest {

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock payCycleTestClock() {
            return java.time.Clock.fixed(java.time.Instant.parse("2026-11-02T13:00:00Z"),
                    java.time.ZoneId.of("America/Sao_Paulo"));
        }
    }

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);
    private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOV = LocalDate.of(2026, 11, 1);
    private static final LocalDate DEC = LocalDate.of(2026, 12, 1);

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private PayCycleRepository cycleRepository;

    @Autowired
    private PayCycleConfigRepository configRepository;

    @Autowired
    private IncomeScheduleRepository scheduleRepository;

    @Autowired
    private IncomeOccurrenceRepository occurrenceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PayCycleService payCycleService;

    @Autowired
    private HolidayCalendarService holidayCalendarService;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        // The history points to cycles: it goes first (its rows are never deleted by the application).
        jdbcTemplate.update("DELETE FROM pay_cycle_adjustment");
        cycleRepository.deleteAll();
        jdbcTemplate.update("UPDATE pay_cycle_config SET mode = 'CALENDAR_MONTH', anchor_schedule_id = NULL, "
                + "activation_date = NULL, version = 0");
        occurrenceRepository.deleteAll();
        scheduleRepository.deleteAll();
    }

    // ---- pay_cycle_config

    @Test
    void shouldSeedASingleConfigurationInCalendarMonthMode() {
        final Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pay_cycle_config", Integer.class);
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();

        assertEquals(1, rows);
        assertNotNull(config.getCode());
        assertEquals(PayCycleMode.CALENDAR_MONTH, config.getMode());
        assertNull(config.getAnchorSchedule());
        assertNull(config.getActivationDate());
        assertEquals(0L, config.getVersion());
        assertNotNull(config.getCreatedDate());
    }

    @Test
    void shouldSwitchTheConfigurationToPayCycleAndReadItBackWithTheAnchor() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();
        config.setMode(PayCycleMode.PAY_CYCLE);
        config.setAnchorSchedule(anchor);
        config.setActivationDate(NOV_5);
        configRepository.saveAndFlush(config);

        final PayCycleConfigEntity found = configRepository.findSingle().orElseThrow();

        assertEquals(PayCycleMode.PAY_CYCLE, found.getMode());
        assertEquals(anchor.getCode(), found.getAnchorSchedule().getCode());
        assertEquals(NOV_5, found.getActivationDate());
        assertEquals(1L, found.getVersion());
    }

    @Test
    void shouldRejectPayCycleModeWithoutAnAnchorOrAnActivationDate() {
        assertCheckViolation("ck_pay_cycle_config_pay_cycle_has_anchor",
                () -> jdbcTemplate.update("UPDATE pay_cycle_config SET mode = 'PAY_CYCLE'"));
    }

    @Test
    void shouldRejectAnUnknownMode() {
        assertCheckViolation("ck_pay_cycle_config_mode",
                () -> jdbcTemplate.update("UPDATE pay_cycle_config SET mode = 'WEEKLY'"));
    }

    @Test
    void shouldRejectAStaleWriteOfTheConfiguration() {
        final PayCycleConfigEntity stale = configRepository.findSingle().orElseThrow();
        final PayCycleConfigEntity fresh = configRepository.findSingle().orElseThrow();
        // The values really change: with no change Hibernate sends no UPDATE and the version stays.
        fresh.setActivationDate(NOV_5);
        configRepository.saveAndFlush(fresh);

        stale.setActivationDate(DEC_7);

        assertThrows(OptimisticLockingFailureException.class, () -> configRepository.saveAndFlush(stale));
    }

    @Test
    void shouldLockTheConfigurationAndItsAnchorInOneRead() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();
        config.setMode(PayCycleMode.PAY_CYCLE);
        config.setAnchorSchedule(anchor);
        config.setActivationDate(NOV_5);
        configRepository.saveAndFlush(config);

        final PayCycleConfigEntity locked = transactionTemplate.execute(
                status -> configRepository.findSingleForUpdate().orElseThrow());

        assertNotNull(locked);
        assertEquals(anchor.getCode(), locked.getAnchorSchedule().getCode());
    }

    @Test
    void shouldLockTheConfigurationThatHasNoAnchor() {
        final PayCycleConfigEntity locked = transactionTemplate.execute(
                status -> configRepository.findSingleForUpdate().orElseThrow());

        assertNotNull(locked);
        assertEquals(PayCycleMode.CALENDAR_MONTH, locked.getMode());
        assertNull(locked.getAnchorSchedule());
    }

    // ---- pay_cycle

    @Test
    void shouldSaveACycleAndReadItBackWithEveryColumn() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity entity = cycle(anchor, NOV_5, DEC_7, NOV);
        entity.setEndAdjusted(true);
        cycleRepository.save(entity);

        final PayCycleEntity found = cycleRepository.findByCode(entity.getCode()).orElseThrow();

        assertEquals(NOV_5, found.getStartDate());
        assertEquals(DEC_7, found.getEndDate());
        assertEquals(PayCycleKind.REGULAR, found.getKind());
        assertEquals(anchor.getCode(), found.getAnchorSchedule().getCode());
        assertEquals(NOV, found.getAnchorNominalMonth());
        assertEquals(PayCycleLifecycle.ACTIVE, found.getLifecycle());
        assertFalse(found.getStartAdjusted());
        assertTrue(found.getEndAdjusted());
        assertEquals(0L, found.getVersion());
        assertNotNull(found.getCreatedDate());
    }

    @Test
    void shouldSaveATransitionWithoutAnchor() {
        final PayCycleEntity entity = transition(OCT_5, NOV_5);
        cycleRepository.save(entity);

        final PayCycleEntity found = cycleRepository.findByCode(entity.getCode()).orElseThrow();

        assertEquals(PayCycleKind.TRANSITION, found.getKind());
        assertNull(found.getAnchorSchedule());
        assertNull(found.getAnchorNominalMonth());
    }

    @Test
    void shouldFindNoCycleForACodeThatDoesNotExist() {
        assertTrue(cycleRepository.findByCode(UUID.randomUUID()).isEmpty());
    }

    @Test
    void shouldListTheCyclesOrderedByStartDateAndByLifecycle() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity third = cycleRepository.save(cycle(anchor, DEC_7, JAN_5, DEC));
        final PayCycleEntity first = cycleRepository.save(cycle(anchor, OCT_5, NOV_5, OCT));
        final PayCycleEntity closed = cycle(anchor, NOV_5, DEC_7, NOV);
        closed.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.save(closed);
        final PayCycleEntity cancelled = cycle(anchor, NOV_5, DEC_7, NOV);
        cancelled.setStartDate(JAN_5);
        cancelled.setEndDate(LocalDate.of(2027, 2, 5));
        cancelled.setAnchorNominalMonth(LocalDate.of(2027, 1, 1));
        cancelled.setLifecycle(PayCycleLifecycle.CANCELLED);
        cycleRepository.save(cancelled);

        final List<PayCycleEntity> active = cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
        final List<PayCycleEntity> all = cycleRepository.findByLifecycleIn(
                List.of(PayCycleLifecycle.ACTIVE, PayCycleLifecycle.CANCELLED));

        assertEquals(List.of(first.getCode(), closed.getCode(), third.getCode()),
                active.stream().map(PayCycleEntity::getCode).toList());
        assertEquals(List.of(first.getCode(), closed.getCode(), third.getCode(), cancelled.getCode()),
                all.stream().map(PayCycleEntity::getCode).toList());
        // the anchor was fetched with the cycle: the model reads its code outside any transaction
        assertEquals(anchor.getCode(), active.get(0).getAnchorSchedule().getCode());
    }

    @Test
    void shouldLockOnlyTheCyclesOfTheLifecycleOrderedByStartDate() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity second = cycle(anchor, NOV_5, DEC_7, NOV);
        second.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.save(second);
        final PayCycleEntity first = cycleRepository.save(cycle(anchor, OCT_5, NOV_5, OCT));
        final PayCycleEntity cancelled = cycle(anchor, DEC_7, JAN_5, DEC);
        cancelled.setLifecycle(PayCycleLifecycle.CANCELLED);
        cycleRepository.save(cancelled);

        final List<PayCycleEntity> locked = transactionTemplate.execute(
                status -> cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE));

        assertNotNull(locked);
        assertEquals(List.of(first.getCode(), second.getCode()), locked.stream().map(PayCycleEntity::getCode).toList());
    }

    @Test
    void shouldPreserveClosedCycleCodeAndLifecycleDuringRefresh() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        holidayCalendarService.reviewYear(2026, List.of());
        holidayCalendarService.reviewYear(2027, List.of());
        activate(anchor, OCT_5);
        materialize(anchor, YearMonth.of(2026, 10), OCT_5);
        materialize(anchor, YearMonth.of(2026, 11), NOV_5);
        materialize(anchor, YearMonth.of(2026, 12), DEC_7);
        materialize(anchor, YearMonth.of(2027, 1), JAN_5);

        final PayCycleEntity closed = cycleRepository.save(cycle(anchor, OCT_5, NOV_5, OCT));
        closed.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.saveAndFlush(closed);
        cycleRepository.saveAll(List.of(
                cycle(anchor, NOV_5, DEC_7, NOV),
                cycle(anchor, DEC_7, JAN_5, DEC)));
        cycleRepository.flush();

        assertDoesNotThrow(payCycleService::refresh);

        final PayCycleEntity refreshed = cycleRepository.findByCode(closed.getCode()).orElseThrow();
        assertEquals(closed.getCode(), refreshed.getCode());
        assertEquals(PayCycleLifecycle.CLOSED, refreshed.getLifecycle());
    }

    @Test
    void shouldRejectTwoActiveCyclesOfTheSameAnchorOccurrence() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        cycleRepository.save(cycle(anchor, NOV_5, DEC_7, NOV));

        assertThrows(DataIntegrityViolationException.class,
                () -> cycleRepository.saveAndFlush(cycle(anchor, DEC_7, JAN_5, NOV)));
    }

    @Test
    void shouldRejectAnActiveCycleWithTheSameAnchorOccurrenceAsAClosedCycle() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity closed = cycle(anchor, NOV_5, DEC_7, NOV);
        closed.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.saveAndFlush(closed);

        assertThrows(DataIntegrityViolationException.class,
                () -> cycleRepository.saveAndFlush(cycle(anchor, DEC_7, JAN_5, NOV)));
    }

    @Test
    void shouldRejectTwoActiveCyclesThatStartOnTheSameDay() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        cycleRepository.save(cycle(anchor, NOV_5, DEC_7, NOV));

        assertThrows(DataIntegrityViolationException.class,
                () -> cycleRepository.saveAndFlush(cycle(anchor, NOV_5, JAN_5, DEC)));
    }

    @Test
    void shouldRejectAnActiveCycleWithTheSameStartDateAsAClosedCycle() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity closed = cycle(anchor, NOV_5, DEC_7, NOV);
        closed.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.saveAndFlush(closed);

        assertThrows(DataIntegrityViolationException.class,
                () -> cycleRepository.saveAndFlush(cycle(anchor, NOV_5, JAN_5, DEC)));
    }

    @Test
    void shouldRejectTwoActiveCyclesThatEndOnTheSameDay() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        cycleRepository.save(cycle(anchor, NOV_5, JAN_5, NOV));

        assertThrows(DataIntegrityViolationException.class,
                () -> cycleRepository.saveAndFlush(cycle(anchor, DEC_7, JAN_5, DEC)));
    }

    @Test
    void shouldRejectAnActiveCycleWithTheSameEndDateAsAClosedCycle() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity closed = cycle(anchor, NOV_5, DEC_7, NOV);
        closed.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.saveAndFlush(closed);

        assertThrows(DataIntegrityViolationException.class,
                () -> cycleRepository.saveAndFlush(cycle(anchor, LocalDate.of(2026, 12, 1), DEC_7, DEC)));
    }

    @Test
    void shouldAllowSeveralTransitionsWithoutAnchorWhenTheirDatesDiffer() {
        assertDoesNotThrow(() -> {
            cycleRepository.saveAndFlush(transition(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 5)));
            cycleRepository.saveAndFlush(transition(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 8)));
        });
    }

    @Test
    void shouldRefreshAdjacentMovedBoundariesWithoutUniqueKeyConflicts() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        activate(anchor, LocalDate.of(2026, 12, 1));
        materialize(anchor, YearMonth.of(2027, 1), LocalDate.of(2027, 2, 1));
        materialize(anchor, YearMonth.of(2027, 2), LocalDate.of(2027, 3, 1));
        materialize(anchor, YearMonth.of(2027, 3), LocalDate.of(2027, 4, 1));
        cycleRepository.saveAll(List.of(
                cycle(anchor, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 1)),
                cycle(anchor, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 1)),
                cycle(anchor, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 2, 1))));
        cycleRepository.flush();

        assertDoesNotThrow(payCycleService::refresh);

        final List<PayCycleEntity> refreshed =
                cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
        assertEquals(List.of(
                        LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1)),
                refreshed.stream().map(PayCycleEntity::getStartDate).toList());
        assertEquals(List.of(
                        LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1)),
                refreshed.stream().map(PayCycleEntity::getEndDate).toList());
    }

    @Test
    void shouldFlushMovedBoundariesBeforeIdentityInsertsDuringRefresh() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        activate(anchor, LocalDate.of(2027, 1, 1));
        materialize(anchor, YearMonth.of(2027, 1), LocalDate.of(2027, 1, 1));
        materialize(anchor, YearMonth.of(2027, 2), LocalDate.of(2027, 2, 1));
        materialize(anchor, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 1));
        materialize(anchor, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 1));
        cycleRepository.saveAndFlush(
                cycle(anchor, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 1, 1)));

        assertDoesNotThrow(payCycleService::refresh);

        final List<PayCycleEntity> refreshed =
                cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
        assertEquals(3, refreshed.size());
        assertEquals(List.of(
                        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1)),
                refreshed.stream().map(PayCycleEntity::getStartDate).toList());
        assertEquals(List.of(
                        LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1)),
                refreshed.stream().map(PayCycleEntity::getEndDate).toList());
    }

    @Test
    void shouldGiveTheKeysBackWhenACycleIsCancelled() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity entity = cycleRepository.save(cycle(anchor, NOV_5, DEC_7, NOV));
        entity.setLifecycle(PayCycleLifecycle.CANCELLED);
        cycleRepository.saveAndFlush(entity);

        assertDoesNotThrow(() -> cycleRepository.saveAndFlush(cycle(anchor, NOV_5, DEC_7, NOV)));
    }

    @Test
    void shouldAllowManyCancelledCyclesWithTheSameKeys() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 3; i++) {
                final PayCycleEntity cancelled = cycle(anchor, NOV_5, DEC_7, NOV);
                cancelled.setLifecycle(PayCycleLifecycle.CANCELLED);
                cycleRepository.saveAndFlush(cancelled);
            }
        });
    }

    @Test
    void shouldFillTheGeneratedColumnsForActiveAndClosedCyclesAndReleaseThemWhenCancelled() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        PayCycleEntity entity = cycleRepository.saveAndFlush(cycle(anchor, NOV_5, DEC_7, NOV));

        assertGeneratedCycleKeys(entity);

        entity.setLifecycle(PayCycleLifecycle.CLOSED);
        entity = cycleRepository.saveAndFlush(entity);
        assertGeneratedCycleKeys(entity);

        entity.setLifecycle(PayCycleLifecycle.CANCELLED);
        entity = cycleRepository.saveAndFlush(entity);

        assertGeneratedCycleKeysAreNull(entity);
    }

    @Test
    void shouldRejectACycleThatEndsBeforeItStarts() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));

        assertCheckViolation("ck_pay_cycle_dates", () -> cycleRepository.saveAndFlush(cycle(anchor, DEC_7, NOV_5, NOV)));
    }

    @Test
    void shouldRejectACycleThatStartsAndEndsOnTheSameDay() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));

        assertCheckViolation("ck_pay_cycle_dates", () -> cycleRepository.saveAndFlush(cycle(anchor, NOV_5, NOV_5, NOV)));
    }

    @Test
    void shouldRejectAnAnchorWithoutItsMonth() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity entity = cycle(anchor, NOV_5, DEC_7, null);

        assertCheckViolation("ck_pay_cycle_anchor_pair", () -> cycleRepository.saveAndFlush(entity));
    }

    @Test
    void shouldRejectAMonthWithoutItsAnchor() {
        final PayCycleEntity entity = cycle(null, NOV_5, DEC_7, NOV);

        assertCheckViolation("ck_pay_cycle_anchor_pair", () -> cycleRepository.saveAndFlush(entity));
    }

    @Test
    void shouldRejectAnUnknownKindOrLifecycle() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final PayCycleEntity entity = cycleRepository.save(cycle(anchor, NOV_5, DEC_7, NOV));

        assertCheckViolation("ck_pay_cycle_kind",
                () -> jdbcTemplate.update("UPDATE pay_cycle SET kind = 'SHORT' WHERE id = ?", entity.getId()));
        assertCheckViolation("ck_pay_cycle_lifecycle",
                () -> jdbcTemplate.update("UPDATE pay_cycle SET lifecycle = 'UNKNOWN' WHERE id = ?", entity.getId()));
    }

    @Test
    void shouldBumpTheVersionOnEveryUpdateAndRejectAStaleWrite() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        final UUID code = cycleRepository.save(cycle(anchor, NOV_5, DEC_7, NOV)).getCode();
        final PayCycleEntity stale = cycleRepository.findByCode(code).orElseThrow();
        final PayCycleEntity fresh = cycleRepository.findByCode(code).orElseThrow();

        fresh.setEndDate(DEC_7.plusDays(1));
        cycleRepository.saveAndFlush(fresh);
        assertEquals(1L, cycleRepository.findByCode(code).orElseThrow().getVersion());

        stale.setEndDate(DEC_7.plusDays(2));
        assertThrows(OptimisticLockingFailureException.class, () -> cycleRepository.saveAndFlush(stale));
        assertEquals(DEC_7.plusDays(1), cycleRepository.findByCode(code).orElseThrow().getEndDate());
    }

    @Test
    void shouldNotDeleteAScheduleThatOpensACycle() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule(true));
        cycleRepository.save(cycle(anchor, NOV_5, DEC_7, NOV));

        assertThrows(DataIntegrityViolationException.class, () -> scheduleRepository.delete(anchor));
    }

    // ---- helpers

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

    private void assertGeneratedCycleKeys(final PayCycleEntity entity) {
        final Long anchorScheduleId = jdbcTemplate.queryForObject(
                "SELECT active_anchor_schedule_id FROM pay_cycle WHERE id = ?", Long.class, entity.getId());
        final Date anchorMonth = jdbcTemplate.queryForObject(
                "SELECT active_anchor_nominal_month FROM pay_cycle WHERE id = ?", Date.class, entity.getId());
        final Date start = jdbcTemplate.queryForObject(
                "SELECT active_start_date FROM pay_cycle WHERE id = ?", Date.class, entity.getId());
        final Date end = jdbcTemplate.queryForObject(
                "SELECT active_end_date FROM pay_cycle WHERE id = ?", Date.class, entity.getId());
        assertNotNull(anchorScheduleId);
        assertEquals(entity.getAnchorNominalMonth(), anchorMonth.toLocalDate());
        assertEquals(entity.getStartDate(), start.toLocalDate());
        assertEquals(entity.getEndDate(), end.toLocalDate());
    }

    private void assertGeneratedCycleKeysAreNull(final PayCycleEntity entity) {
        assertNull(jdbcTemplate.queryForObject(
                "SELECT active_anchor_schedule_id FROM pay_cycle WHERE id = ?", Long.class, entity.getId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT active_anchor_nominal_month FROM pay_cycle WHERE id = ?", Date.class, entity.getId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT active_start_date FROM pay_cycle WHERE id = ?", Date.class, entity.getId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT active_end_date FROM pay_cycle WHERE id = ?", Date.class, entity.getId()));
    }

    private IncomeScheduleEntity schedule(final boolean anchor) {
        return IncomeScheduleEntity.builder()
                .code(UUID.randomUUID())
                .description("Salário")
                .amount(new BigDecimal("5000.00"))
                .ruleType(DateRuleType.FIXED_DAY)
                .ruleValue(5)
                .businessWeek(BusinessWeek.MONDAY_TO_FRIDAY)
                .nonBusinessDayPolicy(NonBusinessDayPolicy.POSTPONE)
                .startMonth(LocalDate.of(2026, 6, 1))
                .anchor(anchor)
                .status(IncomeScheduleStatus.ACTIVE)
                .build();
    }

    private void activate(final IncomeScheduleEntity anchor, final LocalDate activationDate) {
        holidayCalendarService.reviewYear(2027, List.of());
        final PayCycleConfigEntity config = configRepository.findSingle().orElseThrow();
        config.setMode(PayCycleMode.PAY_CYCLE);
        config.setAnchorSchedule(anchor);
        config.setActivationDate(activationDate);
        configRepository.saveAndFlush(config);
    }

    private void materialize(final IncomeScheduleEntity anchor,
                             final YearMonth nominalMonth,
                             final LocalDate plannedDate) {
        occurrenceRepository.save(IncomeOccurrenceEntity.builder()
                .incomeSchedule(anchor)
                .nominalMonth(nominalMonth.atDay(1))
                .plannedDate(plannedDate)
                .plannedAmount(new BigDecimal("5000.00"))
                .movementCode(UUID.randomUUID())
                .build());
    }

    private PayCycleEntity cycle(final IncomeScheduleEntity anchor,
                                 final LocalDate start,
                                 final LocalDate end,
                                 final LocalDate nominalMonth) {
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

    private PayCycleEntity transition(final LocalDate start, final LocalDate end) {
        return PayCycleEntity.builder()
                .code(UUID.randomUUID())
                .startDate(start)
                .endDate(end)
                .kind(PayCycleKind.TRANSITION)
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .build();
    }
}
