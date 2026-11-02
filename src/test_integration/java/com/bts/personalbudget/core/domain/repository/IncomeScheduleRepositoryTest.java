package com.bts.personalbudget.core.domain.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.entity.HolidayCalendarReviewEntity;
import com.bts.personalbudget.core.domain.entity.HolidayEntity;
import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.repository.HolidayCalendarReviewRepository;
import com.bts.personalbudget.repository.HolidayRepository;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V9 (holiday calendar) and V10 (income schedules and occurrences) against a real MySQL: the unique
 * keys that carry the idempotency and the single-anchor rule live in the schema, so they are
 * exercised here and not only in the services.
 */
@Testcontainers
@SpringBootTest
public class IncomeScheduleRepositoryTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private IncomeScheduleRepository scheduleRepository;

    @Autowired
    private IncomeOccurrenceRepository occurrenceRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private HolidayCalendarReviewRepository reviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanTables() {
        occurrenceRepository.deleteAll();
        scheduleRepository.deleteAll();
        holidayRepository.deleteAll();
        reviewRepository.deleteAll();
    }

    // ---- income_schedule

    @Test
    void shouldSaveAScheduleAndReadItBackWithEveryColumn() {
        final UUID categoryCode = UUID.randomUUID();
        final UUID fixedBillCode = UUID.randomUUID();
        final IncomeScheduleEntity entity = schedule("Salário", false, IncomeScheduleStatus.ACTIVE);
        entity.setCategoryCode(categoryCode);
        entity.setEndMonth(LocalDate.of(2027, 5, 1));
        entity.setReplacesFixedBillCode(fixedBillCode);
        scheduleRepository.save(entity);

        final IncomeScheduleEntity found = scheduleRepository.findByCode(entity.getCode()).orElseThrow();

        assertEquals("Salário", found.getDescription());
        assertEquals(0, new BigDecimal("5000.00").compareTo(found.getAmount()));
        assertEquals(categoryCode, found.getCategoryCode());
        assertEquals(DateRuleType.FIXED_DAY, found.getRuleType());
        assertEquals(5, found.getRuleValue());
        assertEquals(BusinessWeek.MONDAY_TO_FRIDAY, found.getBusinessWeek());
        assertEquals(NonBusinessDayPolicy.POSTPONE, found.getNonBusinessDayPolicy());
        assertEquals(LocalDate.of(2026, 6, 1), found.getStartMonth());
        assertEquals(LocalDate.of(2027, 5, 1), found.getEndMonth());
        assertEquals(Boolean.FALSE, found.getAnchor());
        assertEquals(IncomeScheduleStatus.ACTIVE, found.getStatus());
        assertEquals(fixedBillCode, found.getReplacesFixedBillCode());
        assertNotNull(found.getCreatedDate());
    }

    @Test
    void shouldStoreANthBusinessDayRuleWithoutPolicyAndWithoutEnd() {
        final IncomeScheduleEntity entity = schedule("Salário 5º dia útil", false, IncomeScheduleStatus.ACTIVE);
        entity.setRuleType(DateRuleType.NTH_BUSINESS_DAY);
        entity.setNonBusinessDayPolicy(null);
        scheduleRepository.save(entity);

        final IncomeScheduleEntity found = scheduleRepository.findByCode(entity.getCode()).orElseThrow();

        assertEquals(DateRuleType.NTH_BUSINESS_DAY, found.getRuleType());
        assertNull(found.getNonBusinessDayPolicy());
        assertNull(found.getEndMonth());
        assertNull(found.getCategoryCode());
    }

    @Test
    void shouldRejectASecondActiveAnchor() {
        scheduleRepository.save(schedule("Salário", true, IncomeScheduleStatus.ACTIVE));

        assertThrows(DataIntegrityViolationException.class,
                () -> scheduleRepository.save(schedule("Outro salário", true, IncomeScheduleStatus.ACTIVE)));
    }

    @Test
    void shouldAllowAnInactiveAnchorAndOtherSchedulesNextToTheActiveAnchor() {
        assertDoesNotThrow(() -> {
            scheduleRepository.save(schedule("Salário", true, IncomeScheduleStatus.ACTIVE));
            scheduleRepository.save(schedule("Salário antigo", true, IncomeScheduleStatus.INACTIVE));
            scheduleRepository.save(schedule("Salário antigo 2", true, IncomeScheduleStatus.INACTIVE));
            scheduleRepository.save(schedule("Vale", false, IncomeScheduleStatus.ACTIVE));
            scheduleRepository.save(schedule("Bônus", false, IncomeScheduleStatus.ACTIVE));
        });
    }

    @Test
    void shouldEnforceOneActiveReplacementForAFixedBillOnCreateAndActivation() {
        final UUID fixedBillCode = UUID.randomUUID();
        final IncomeScheduleEntity first = schedule("Salário", false, IncomeScheduleStatus.ACTIVE);
        first.setReplacesFixedBillCode(fixedBillCode);
        scheduleRepository.saveAndFlush(first);

        final IncomeScheduleEntity second = schedule("Outro salário", false, IncomeScheduleStatus.ACTIVE);
        second.setReplacesFixedBillCode(fixedBillCode);
        assertThrows(DataIntegrityViolationException.class, () -> scheduleRepository.saveAndFlush(second));

        final IncomeScheduleEntity inactive = schedule("Salário antigo", false, IncomeScheduleStatus.INACTIVE);
        inactive.setReplacesFixedBillCode(fixedBillCode);
        scheduleRepository.saveAndFlush(inactive);
        inactive.setStatus(IncomeScheduleStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> scheduleRepository.saveAndFlush(inactive));
    }

    @Test
    void shouldRejectReactivatingAnAnchorWhileAnotherAnchorIsActive() {
        scheduleRepository.save(schedule("Salário", true, IncomeScheduleStatus.ACTIVE));
        final IncomeScheduleEntity old = scheduleRepository.save(schedule("Antigo", true, IncomeScheduleStatus.INACTIVE));

        old.setStatus(IncomeScheduleStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> scheduleRepository.saveAndFlush(old));
    }

    @Test
    void shouldFillTheAnchorSlotOnlyForTheActiveAnchor() {
        scheduleRepository.save(schedule("Âncora ativa", true, IncomeScheduleStatus.ACTIVE));
        scheduleRepository.save(schedule("Âncora inativa", true, IncomeScheduleStatus.INACTIVE));
        scheduleRepository.save(schedule("Não âncora", false, IncomeScheduleStatus.ACTIVE));

        assertEquals(1, slotOf("Âncora ativa"));
        assertNull(slotOf("Âncora inativa"));
        assertNull(slotOf("Não âncora"));
    }

    @Test
    void shouldReleaseTheAnchorSlotWhenTheAnchorIsInactivated() {
        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule("Âncora", true, IncomeScheduleStatus.ACTIVE));
        anchor.setStatus(IncomeScheduleStatus.INACTIVE);
        scheduleRepository.saveAndFlush(anchor);

        assertNull(slotOf("Âncora"));
        assertDoesNotThrow(() -> scheduleRepository.save(schedule("Nova âncora", true, IncomeScheduleStatus.ACTIVE)));
    }

    @Test
    void shouldAnswerWhetherAnActiveAnchorExistsAndWhetherItIsAnotherOne() {
        assertFalse(scheduleRepository.existsByAnchorTrueAndStatus(IncomeScheduleStatus.ACTIVE));

        final IncomeScheduleEntity anchor = scheduleRepository.save(schedule("Salário", true, IncomeScheduleStatus.ACTIVE));
        scheduleRepository.save(schedule("Antiga", true, IncomeScheduleStatus.INACTIVE));
        final IncomeScheduleEntity other = scheduleRepository.save(schedule("Vale", false, IncomeScheduleStatus.ACTIVE));

        assertTrue(scheduleRepository.existsByAnchorTrueAndStatus(IncomeScheduleStatus.ACTIVE));
        assertTrue(scheduleRepository.existsByAnchorTrueAndStatus(IncomeScheduleStatus.INACTIVE));
        assertFalse(scheduleRepository.existsByAnchorTrueAndStatusAndCodeNot(
                IncomeScheduleStatus.ACTIVE, anchor.getCode()));
        assertTrue(scheduleRepository.existsByAnchorTrueAndStatusAndCodeNot(
                IncomeScheduleStatus.ACTIVE, other.getCode()));
    }

    @Test
    void shouldAnswerWhetherAnActiveScheduleReplacesTheFixedBill() {
        final UUID fixedBillCode = UUID.randomUUID();
        final IncomeScheduleEntity entity = schedule("Salário", false, IncomeScheduleStatus.ACTIVE);
        entity.setReplacesFixedBillCode(fixedBillCode);
        scheduleRepository.save(entity);

        assertTrue(scheduleRepository.existsByReplacesFixedBillCodeAndStatus(fixedBillCode, IncomeScheduleStatus.ACTIVE));
        assertFalse(scheduleRepository.existsByReplacesFixedBillCodeAndStatus(fixedBillCode, IncomeScheduleStatus.INACTIVE));
        assertFalse(scheduleRepository.existsByReplacesFixedBillCodeAndStatus(
                UUID.randomUUID(), IncomeScheduleStatus.ACTIVE));
    }

    @Test
    void shouldFilterByStatusAndOrderByDescription() {
        scheduleRepository.save(schedule("Zeta", false, IncomeScheduleStatus.ACTIVE));
        scheduleRepository.save(schedule("Alfa", false, IncomeScheduleStatus.INACTIVE));
        scheduleRepository.save(schedule("Beta", false, IncomeScheduleStatus.ACTIVE));

        final List<String> active = scheduleRepository
                .findAllByStatusInOrderByDescriptionAsc(List.of(IncomeScheduleStatus.ACTIVE)).stream()
                .map(IncomeScheduleEntity::getDescription).toList();
        final List<String> all = scheduleRepository
                .findAllByStatusInOrderByDescriptionAsc(List.of(IncomeScheduleStatus.ACTIVE, IncomeScheduleStatus.INACTIVE))
                .stream().map(IncomeScheduleEntity::getDescription).toList();

        assertEquals(List.of("Beta", "Zeta"), active);
        assertEquals(List.of("Alfa", "Beta", "Zeta"), all);
        assertEquals(2, scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE).size());
    }

    @Test
    void shouldFindOnlyTheActiveSchedulesStartedUntilTheMonth() {
        final IncomeScheduleEntity started = schedule("Já começou", false, IncomeScheduleStatus.ACTIVE);
        started.setStartMonth(LocalDate.of(2026, 5, 1));
        final IncomeScheduleEntity startsNextMonth = schedule("Começa em julho", false, IncomeScheduleStatus.ACTIVE);
        startsNextMonth.setStartMonth(LocalDate.of(2026, 7, 1));
        final IncomeScheduleEntity startsLater = schedule("Começa em agosto", false, IncomeScheduleStatus.ACTIVE);
        startsLater.setStartMonth(LocalDate.of(2026, 8, 1));
        final IncomeScheduleEntity inactive = schedule("Inativa", false, IncomeScheduleStatus.INACTIVE);
        inactive.setStartMonth(LocalDate.of(2026, 1, 1));
        scheduleRepository.saveAll(List.of(started, startsNextMonth, startsLater, inactive));

        final List<String> found = scheduleRepository
                .findAllByStatusAndStartMonthLessThanEqual(IncomeScheduleStatus.ACTIVE, LocalDate.of(2026, 7, 1))
                .stream().map(IncomeScheduleEntity::getDescription).sorted().toList();

        assertEquals(List.of("Começa em julho", "Já começou"), found);
    }

    @Test
    void shouldLockTheScheduleRowInsideATransaction() {
        final IncomeScheduleEntity entity = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));

        // PESSIMISTIC_WRITE needs a transaction, which the services provide; here the template does.
        final Optional<IncomeScheduleEntity> locked = transactionTemplate.execute(
                status -> scheduleRepository.findForUpdateByCode(entity.getCode()));
        final Optional<IncomeScheduleEntity> unknown = transactionTemplate.execute(
                status -> scheduleRepository.findForUpdateByCode(UUID.randomUUID()));

        assertTrue(locked.isPresent());
        assertEquals(entity.getCode(), locked.get().getCode());
        assertTrue(unknown.isEmpty());
    }

    // ---- income_occurrence

    @Test
    void shouldSaveAnOccurrenceAndFindItWithItsScheduleFetched() {
        final IncomeScheduleEntity schedule = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));
        final UUID movementCode = UUID.randomUUID();
        occurrenceRepository.save(occurrence(schedule, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), movementCode));

        final IncomeOccurrenceEntity found = occurrenceRepository
                .findByScheduleCodeAndNominalMonth(schedule.getCode(), LocalDate.of(2026, 7, 1)).orElseThrow();

        assertNotNull(found.getCode());
        // Read outside any transaction: only works because the query fetched the schedule.
        assertEquals(schedule.getCode(), found.getIncomeSchedule().getCode());
        assertEquals(LocalDate.of(2026, 7, 6), found.getPlannedDate());
        assertEquals(0, new BigDecimal("5000.00").compareTo(found.getPlannedAmount()));
        assertEquals(movementCode, found.getMovementCode());
    }

    @Test
    void shouldRejectASecondOccurrenceForTheSameScheduleAndMonth() {
        final IncomeScheduleEntity schedule = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));
        occurrenceRepository.save(occurrence(schedule, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), UUID.randomUUID()));

        // Same nominal month under another planned date (a holiday moved it): still the same occurrence.
        assertThrows(DataIntegrityViolationException.class, () -> occurrenceRepository.save(
                occurrence(schedule, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), UUID.randomUUID())));
    }

    @Test
    void shouldAllowTheSameMonthForDifferentSchedulesAndDifferentMonthsForTheSameSchedule() {
        final IncomeScheduleEntity salary = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));
        final IncomeScheduleEntity advance = scheduleRepository.save(schedule("Vale", false, IncomeScheduleStatus.ACTIVE));

        assertDoesNotThrow(() -> {
            occurrenceRepository.save(occurrence(salary, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), UUID.randomUUID()));
            occurrenceRepository.save(occurrence(advance, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20), UUID.randomUUID()));
            occurrenceRepository.save(occurrence(salary, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), UUID.randomUUID()));
        });
    }

    @Test
    void shouldRejectTwoOccurrencesPointingAtTheSameMovement() {
        final IncomeScheduleEntity schedule = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));
        final UUID movementCode = UUID.randomUUID();
        occurrenceRepository.save(occurrence(schedule, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), movementCode));

        assertThrows(DataIntegrityViolationException.class, () -> occurrenceRepository.save(
                occurrence(schedule, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), movementCode)));
    }

    @Test
    void shouldListTheOccurrencesOfOneScheduleInMonthOrder() {
        final IncomeScheduleEntity salary = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));
        final IncomeScheduleEntity advance = scheduleRepository.save(schedule("Vale", false, IncomeScheduleStatus.ACTIVE));
        occurrenceRepository.save(occurrence(salary, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), UUID.randomUUID()));
        occurrenceRepository.save(occurrence(salary, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), UUID.randomUUID()));
        occurrenceRepository.save(occurrence(advance, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 20), UUID.randomUUID()));

        final List<LocalDate> months = occurrenceRepository.findByScheduleCode(salary.getCode()).stream()
                .map(IncomeOccurrenceEntity::getNominalMonth).toList();

        assertEquals(List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 1)), months);
    }

    @Test
    void shouldFindTheOccurrencesOfSeveralSchedulesAtOnce() {
        final IncomeScheduleEntity salary = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));
        final IncomeScheduleEntity advance = scheduleRepository.save(schedule("Vale", false, IncomeScheduleStatus.ACTIVE));
        final IncomeScheduleEntity other = scheduleRepository.save(schedule("Bônus", false, IncomeScheduleStatus.ACTIVE));
        occurrenceRepository.save(occurrence(salary, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5), UUID.randomUUID()));
        occurrenceRepository.save(occurrence(advance, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 20), UUID.randomUUID()));
        occurrenceRepository.save(occurrence(other, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), UUID.randomUUID()));

        final List<UUID> schedules = occurrenceRepository
                .findByScheduleCodeIn(List.of(salary.getCode(), advance.getCode())).stream()
                .map(occurrence -> occurrence.getIncomeSchedule().getCode()).toList();

        assertEquals(2, schedules.size());
        assertTrue(schedules.containsAll(List.of(salary.getCode(), advance.getCode())));
    }

    @Test
    void shouldFindNoOccurrenceForAMonthThatWasNotMaterialized() {
        final IncomeScheduleEntity schedule = scheduleRepository.save(schedule("Salário", false, IncomeScheduleStatus.ACTIVE));

        assertTrue(occurrenceRepository
                .findByScheduleCodeAndNominalMonth(schedule.getCode(), LocalDate.of(2026, 7, 1)).isEmpty());
    }

    // ---- holiday calendar

    @Test
    void shouldRejectTwoHolidaysOnTheSameDate() {
        holidayRepository.save(HolidayEntity.builder().holidayDate(LocalDate.of(2026, 9, 7)).name("Independência").build());

        assertThrows(DataIntegrityViolationException.class, () -> holidayRepository.save(
                HolidayEntity.builder().holidayDate(LocalDate.of(2026, 9, 7)).name("Sete de Setembro").build()));
    }

    @Test
    void shouldFindTheHolidaysOfAYearIncludingItsFirstAndLastDay() {
        holidayRepository.saveAll(List.of(
                holiday(LocalDate.of(2025, 12, 31), "Fim de 2025"),
                holiday(LocalDate.of(2026, 1, 1), "Confraternização"),
                holiday(LocalDate.of(2026, 12, 25), "Natal"),
                holiday(LocalDate.of(2026, 12, 31), "Fim de 2026"),
                holiday(LocalDate.of(2027, 1, 1), "Confraternização 2027")));

        final List<String> names = holidayRepository
                .findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                .stream().map(HolidayEntity::getName).toList();

        assertEquals(List.of("Confraternização", "Natal", "Fim de 2026"), names);
        assertEquals(5, holidayRepository.findAllByOrderByHolidayDateAsc().size());
        assertEquals("Fim de 2025", holidayRepository.findAllByOrderByHolidayDateAsc().get(0).getName());
    }

    @Test
    void shouldRegisterAReviewedYearOnlyOnce() {
        reviewRepository.save(HolidayCalendarReviewEntity.builder().calendarYear(2026).build());

        assertTrue(reviewRepository.existsByCalendarYear(2026));
        assertFalse(reviewRepository.existsByCalendarYear(2027));
        assertThrows(DataIntegrityViolationException.class, () ->
                reviewRepository.save(HolidayCalendarReviewEntity.builder().calendarYear(2026).build()));
    }

    @Test
    void shouldKeepAReviewedYearWithNoHolidayDistinctFromAYearNeverReviewed() {
        reviewRepository.save(HolidayCalendarReviewEntity.builder().calendarYear(2026).build());

        assertTrue(holidayRepository.findAllByOrderByHolidayDateAsc().isEmpty());
        assertEquals(List.of(2026), reviewRepository.findAllByOrderByCalendarYearAsc().stream()
                .map(HolidayCalendarReviewEntity::getCalendarYear).toList());
    }

    // ---- helpers

    private Integer slotOf(final String description) {
        return jdbcTemplate.queryForObject(
                "SELECT active_anchor_slot FROM income_schedule WHERE description = ?", Integer.class, description);
    }

    private IncomeScheduleEntity schedule(final String description, final boolean anchor, final IncomeScheduleStatus status) {
        return IncomeScheduleEntity.builder()
                .code(UUID.randomUUID())
                .description(description)
                .amount(new BigDecimal("5000.00"))
                .ruleType(DateRuleType.FIXED_DAY)
                .ruleValue(5)
                .businessWeek(BusinessWeek.MONDAY_TO_FRIDAY)
                .nonBusinessDayPolicy(NonBusinessDayPolicy.POSTPONE)
                .startMonth(LocalDate.of(2026, 6, 1))
                .anchor(anchor)
                .status(status)
                .build();
    }

    private IncomeOccurrenceEntity occurrence(final IncomeScheduleEntity schedule,
                                              final LocalDate nominalMonth,
                                              final LocalDate plannedDate,
                                              final UUID movementCode) {
        return IncomeOccurrenceEntity.builder()
                .incomeSchedule(schedule)
                .nominalMonth(nominalMonth)
                .plannedDate(plannedDate)
                .plannedAmount(new BigDecimal("5000.00"))
                .movementCode(movementCode)
                .build();
    }

    private HolidayEntity holiday(final LocalDate date, final String name) {
        return HolidayEntity.builder().holidayDate(date).name(name).build();
    }
}
