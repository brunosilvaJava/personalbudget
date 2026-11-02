package com.bts.personalbudget.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bts.personalbudget.controller.holidaycalendar.HolidayChangeImpactResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeOccurrenceResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleRequest;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleUpdateRequest;
import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeCalendarImpact;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceResolver.IncomeOccurrenceChange;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncomeScheduleMapperTest {

    private static final UUID CODE = IncomeScheduleFactory.SCHEDULE_CODE;
    private static final UUID MOVEMENT_CODE = IncomeScheduleFactory.MOVEMENT_CODE;
    private static final UUID FIXED_BILL_CODE = IncomeScheduleFactory.FIXED_BILL_CODE;
    private static final UUID CATEGORY_CODE = IncomeScheduleFactory.CATEGORY_CODE;
    private static final BigDecimal AMOUNT = IncomeScheduleFactory.AMOUNT;

    private final IncomeScheduleMapper mapper = IncomeScheduleMapper.INSTANCE;

    @Test
    void shouldKeepEverythingWhenAScheduleGoesToTheEntityAndBack() {
        final IncomeSchedule schedule = new IncomeSchedule(CODE, "Salário", AMOUNT, CATEGORY_CODE,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.ANTICIPATE, BusinessWeek.MONDAY_TO_SATURDAY),
                YearMonth.of(2026, 12), YearMonth.of(2027, 11), true, IncomeScheduleStatus.INACTIVE,
                FIXED_BILL_CODE);

        final IncomeScheduleEntity entity = mapper.toEntity(schedule);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCode()).isEqualTo(CODE);
        assertThat(entity.getRuleType()).isEqualTo(DateRuleType.FIXED_DAY);
        assertThat(entity.getRuleValue()).isEqualTo(5);
        assertThat(entity.getBusinessWeek()).isEqualTo(BusinessWeek.MONDAY_TO_SATURDAY);
        assertThat(entity.getNonBusinessDayPolicy()).isEqualTo(NonBusinessDayPolicy.ANTICIPATE);
        assertThat(entity.getStartMonth()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(entity.getEndMonth()).isEqualTo(LocalDate.of(2027, 11, 1));
        assertThat(entity.getAnchor()).isTrue();
        assertThat(entity.getStatus()).isEqualTo(IncomeScheduleStatus.INACTIVE);
        assertThat(mapper.toModel(entity)).isEqualTo(schedule);
    }

    @Test
    void shouldStoreAScheduleWithoutEndAsNullAndReadItBack() {
        final IncomeSchedule schedule = IncomeScheduleFactory.buildSchedule(YearMonth.of(2026, 6));

        final IncomeScheduleEntity entity = mapper.toEntity(schedule);

        assertThat(entity.getEndMonth()).isNull();
        assertThat(mapper.toModel(entity).endMonth()).isNull();
    }

    @Test
    void shouldDropThePolicyOfANthBusinessDayRuleWhenReadingTheEntity() {
        final IncomeSchedule schedule = IncomeScheduleFactory.buildSchedule(
                IncomeDateRule.nthBusinessDay(5, BusinessWeek.MONDAY_TO_FRIDAY), YearMonth.of(2026, 6), null);

        final IncomeScheduleEntity entity = mapper.toEntity(schedule);

        assertThat(entity.getNonBusinessDayPolicy()).isNull();
        assertThat(mapper.toModel(entity).rule())
                .isEqualTo(IncomeDateRule.nthBusinessDay(5, BusinessWeek.MONDAY_TO_FRIDAY));
    }

    @Test
    void shouldCopyOnlyWhatAnEditCanChange() {
        final IncomeScheduleEntity stored = mapper.toEntity(new IncomeSchedule(CODE, "Salário", AMOUNT,
                CATEGORY_CODE, IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 7), null, false, IncomeScheduleStatus.ACTIVE, FIXED_BILL_CODE));
        final IncomeSchedule edited = new IncomeSchedule(UUID.randomUUID(), "Salário CLT", new BigDecimal("5200.00"),
                null, IncomeDateRule.nthBusinessDay(3, BusinessWeek.MONDAY_TO_SATURDAY),
                YearMonth.of(2030, 1), YearMonth.of(2031, 1), true, IncomeScheduleStatus.ACTIVE, null);

        mapper.copyEditableFields(edited, stored);

        assertThat(stored.getDescription()).isEqualTo("Salário CLT");
        assertThat(stored.getAmount()).isEqualByComparingTo("5200.00");
        assertThat(stored.getCategoryCode()).isNull();
        assertThat(stored.getRuleType()).isEqualTo(DateRuleType.NTH_BUSINESS_DAY);
        assertThat(stored.getRuleValue()).isEqualTo(3);
        assertThat(stored.getBusinessWeek()).isEqualTo(BusinessWeek.MONDAY_TO_SATURDAY);
        assertThat(stored.getNonBusinessDayPolicy()).isNull();
        assertThat(stored.getEndMonth()).isEqualTo(LocalDate.of(2031, 1, 1));
        assertThat(stored.getAnchor()).isTrue();
        // What an edit must not move: identity, the months served from, and the bill it replaced.
        assertThat(stored.getCode()).isEqualTo(CODE);
        assertThat(stored.getStartMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(stored.getReplacesFixedBillCode()).isEqualTo(FIXED_BILL_CODE);
    }

    @Test
    void shouldReadANullAnchorOfTheEntityAsFalse() {
        final IncomeScheduleEntity entity = mapper.toEntity(IncomeScheduleFactory.buildSchedule(YearMonth.of(2026, 6)));
        entity.setAnchor(null);

        assertThat(mapper.toModel(entity).anchor()).isFalse();
    }

    @Test
    void shouldMapARequestToANewActiveScheduleThatIsNotTheAnchorByDefault() {
        final IncomeScheduleRequest request = new IncomeScheduleRequest("Salário", AMOUNT, CATEGORY_CODE,
                DateRuleType.FIXED_DAY, 5, BusinessWeek.MONDAY_TO_FRIDAY, NonBusinessDayPolicy.POSTPONE,
                YearMonth.of(2026, 12), null, null, FIXED_BILL_CODE);

        final IncomeSchedule schedule = mapper.toModel(request);

        assertThat(schedule.code()).isNotNull();
        assertThat(schedule.status()).isEqualTo(IncomeScheduleStatus.ACTIVE);
        assertThat(schedule.anchor()).isFalse();
        assertThat(schedule.startMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(schedule.endMonth()).isNull();
        assertThat(schedule.replacesFixedBillCode()).isEqualTo(FIXED_BILL_CODE);
        assertThat(schedule.rule())
                .isEqualTo(IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY));
    }

    @Test
    void shouldRejectARequestWhoseRuleIsNotValidAsAWhole() {
        // FIXED_DAY with no policy for the day that is not a business day.
        final IncomeScheduleRequest request = new IncomeScheduleRequest("Salário", AMOUNT, null,
                DateRuleType.FIXED_DAY, 5, BusinessWeek.MONDAY_TO_FRIDAY, null, YearMonth.of(2026, 12), null, null,
                null);

        assertThatThrownBy(() -> mapper.toModel(request))
                .isInstanceOfSatisfying(InvalidFieldsException.class,
                        exception -> assertThat(exception.getValidations()).containsKey("nonBusinessDayPolicy"));
    }

    @Test
    void shouldMapAnUpdateRequestAndTreatAnOmittedRemoveEndMonthAsFalse() {
        final IncomeScheduleUpdate omitted = mapper.toUpdate(new IncomeScheduleUpdateRequest(
                null, null, null, null, null, null, null, null, null, null));
        final IncomeScheduleUpdate remove = mapper.toUpdate(new IncomeScheduleUpdateRequest(
                null, null, null, null, null, null, null, null, true, null));
        final IncomeScheduleUpdate full = mapper.toUpdate(new IncomeScheduleUpdateRequest(
                "Salário CLT", new BigDecimal("5200.00"), CATEGORY_CODE, DateRuleType.FIXED_DAY, 7,
                BusinessWeek.MONDAY_TO_SATURDAY, NonBusinessDayPolicy.KEEP, YearMonth.of(2027, 11), false, true));

        assertThat(omitted.removeEndMonth()).isFalse();
        assertThat(omitted.isEmpty()).isTrue();
        assertThat(remove.removeEndMonth()).isTrue();
        assertThat(remove.isEmpty()).isFalse();
        assertThat(full.description()).isEqualTo("Salário CLT");
        assertThat(full.amount()).isEqualByComparingTo("5200.00");
        assertThat(full.categoryCode()).isEqualTo(CATEGORY_CODE);
        assertThat(full.ruleType()).isEqualTo(DateRuleType.FIXED_DAY);
        assertThat(full.ruleValue()).isEqualTo(7);
        assertThat(full.businessWeek()).isEqualTo(BusinessWeek.MONDAY_TO_SATURDAY);
        assertThat(full.nonBusinessDayPolicy()).isEqualTo(NonBusinessDayPolicy.KEEP);
        assertThat(full.endMonth()).isEqualTo(YearMonth.of(2027, 11));
        assertThat(full.anchor()).isTrue();
    }

    @Test
    void shouldMapAScheduleToItsResponse() {
        final IncomeSchedule schedule = new IncomeSchedule(CODE, "Salário", AMOUNT, CATEGORY_CODE,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 12), YearMonth.of(2027, 11), true, IncomeScheduleStatus.ACTIVE, FIXED_BILL_CODE);

        final IncomeScheduleResponse response = mapper.toResponse(schedule);

        assertThat(response.code()).isEqualTo(CODE);
        assertThat(response.description()).isEqualTo("Salário");
        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.categoryCode()).isEqualTo(CATEGORY_CODE);
        assertThat(response.ruleType()).isEqualTo(DateRuleType.FIXED_DAY);
        assertThat(response.ruleValue()).isEqualTo(5);
        assertThat(response.businessWeek()).isEqualTo(BusinessWeek.MONDAY_TO_FRIDAY);
        assertThat(response.nonBusinessDayPolicy()).isEqualTo(NonBusinessDayPolicy.POSTPONE);
        assertThat(response.startMonth()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(response.endMonth()).isEqualTo(YearMonth.of(2027, 11));
        assertThat(response.anchor()).isTrue();
        assertThat(response.status()).isEqualTo(IncomeScheduleStatus.ACTIVE);
        assertThat(response.replacesFixedBillCode()).isEqualTo(FIXED_BILL_CODE);
    }

    @Test
    void shouldReadAnOccurrenceEntityAsAModelOfItsSchedule() {
        final IncomeScheduleEntity schedule = mapper.toEntity(IncomeScheduleFactory.buildSchedule(YearMonth.of(2026, 6)));
        final UUID occurrenceCode = UUID.randomUUID();
        final IncomeOccurrenceEntity entity = IncomeOccurrenceEntity.builder()
                .code(occurrenceCode)
                .incomeSchedule(schedule)
                .nominalMonth(LocalDate.of(2026, 7, 1))
                .plannedDate(LocalDate.of(2026, 7, 6))
                .plannedAmount(AMOUNT)
                .movementCode(MOVEMENT_CODE)
                .build();

        final IncomeOccurrence occurrence = mapper.toModel(entity);

        assertThat(occurrence.code()).isEqualTo(occurrenceCode);
        assertThat(occurrence.scheduleCode()).isEqualTo(CODE);
        assertThat(occurrence.nominalMonth()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(occurrence.plannedDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(occurrence.plannedAmount()).isEqualByComparingTo("5000.00");
        assertThat(occurrence.movementCode()).isEqualTo(MOVEMENT_CODE);
    }

    @Test
    void shouldMapEachStateOfAnOccurrenceViewToItsResponse() {
        final IncomeOccurrenceResponse materialized = mapper.toResponse(IncomeOccurrenceView.materialized(
                IncomeScheduleFactory.buildOccurrence(YearMonth.of(2026, 6), LocalDate.of(2026, 6, 5))));
        final IncomeOccurrenceResponse projected = mapper.toResponse(IncomeOccurrenceView.projected(
                YearMonth.of(2026, 7), LocalDate.of(2026, 7, 6), AMOUNT));
        final IncomeOccurrenceResponse notReviewed = mapper.toResponse(
                IncomeOccurrenceView.calendarNotReviewed(YearMonth.of(2027, 1), 2027));
        final IncomeOccurrenceResponse noBusinessDay = mapper.toResponse(
                IncomeOccurrenceView.businessDayNotFound(YearMonth.of(2026, 2)));

        assertThat(materialized.state()).isEqualTo(State.MATERIALIZED);
        assertThat(materialized.movementCode()).isEqualTo(MOVEMENT_CODE);
        assertThat(materialized.plannedDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(projected.state()).isEqualTo(State.PROJECTED);
        assertThat(projected.movementCode()).isNull();
        assertThat(projected.plannedDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(projected.amount()).isEqualByComparingTo("5000.00");
        assertThat(notReviewed.state()).isEqualTo(State.CALENDAR_NOT_REVIEWED);
        assertThat(notReviewed.pendingYear()).isEqualTo(2027);
        assertThat(notReviewed.plannedDate()).isNull();
        assertThat(noBusinessDay.state()).isEqualTo(State.BUSINESS_DAY_NOT_FOUND);
        assertThat(noBusinessDay.plannedDate()).isNull();
    }

    @Test
    void shouldAnswerAMaterializedOccurrenceAsMaterialized() {
        final IncomeOccurrenceResponse response = mapper.toResponse(
                IncomeScheduleFactory.buildOccurrence(YearMonth.of(2026, 6), LocalDate.of(2026, 6, 5)));

        assertThat(response.state()).isEqualTo(State.MATERIALIZED);
        assertThat(response.nominalMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(response.amount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void shouldMapACalendarImpactWithTheMonthItBelongsTo() {
        final IncomeOccurrenceView before = IncomeOccurrenceView.projected(
                YearMonth.of(2026, 8), LocalDate.of(2026, 8, 5), AMOUNT);
        final IncomeOccurrenceView after = IncomeOccurrenceView.projected(
                YearMonth.of(2026, 8), LocalDate.of(2026, 8, 6), AMOUNT);

        final HolidayChangeImpactResponse response = mapper.toResponse(
                new IncomeCalendarImpact(CODE, "Salário", new IncomeOccurrenceChange(before, after)));

        assertThat(response.scheduleCode()).isEqualTo(CODE);
        assertThat(response.scheduleDescription()).isEqualTo("Salário");
        assertThat(response.nominalMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(response.before().plannedDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(response.after().plannedDate()).isEqualTo(LocalDate.of(2026, 8, 6));
    }

    @Test
    void shouldConvertMonthsToTheFirstDayAndBackKeepingNull() {
        assertThat(mapper.toDate(YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(mapper.toDate(null)).isNull();
        assertThat(mapper.toYearMonth(LocalDate.of(2026, 2, 1))).isEqualTo(YearMonth.of(2026, 2));
        assertThat(mapper.toYearMonth(LocalDate.of(2026, 2, 17))).isEqualTo(YearMonth.of(2026, 2));
        assertThat(mapper.toYearMonth(null)).isNull();
    }
}
