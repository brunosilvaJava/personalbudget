package com.bts.personalbudget.core.domain.service.income;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildCalendar;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.AMOUNT;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.MOVEMENT_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildInactive;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildOccurrence;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.service.calendar.Holiday;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceResolver.IncomeOccurrenceChange;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IncomeOccurrenceResolverTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);
    private static final YearMonth FEB = YearMonth.of(2026, 2);
    private static final YearMonth MAR = YearMonth.of(2026, 3);
    private static final YearMonth APR = YearMonth.of(2026, 4);

    @Test
    void shouldProjectEveryMonthInsideTheValidityFromTheRuleAndTheCalendar() {
        // Day 5 postponed: Jan 5 2026 is Monday, Feb 5 Thursday, Mar 5 Thursday.
        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                buildSchedule(JAN), List.of(), buildReviewedCalendar(2026), JAN, MAR);

        assertEquals(3, views.size());
        assertEquals(IncomeOccurrenceView.projected(JAN, LocalDate.of(2026, 1, 5), AMOUNT), views.get(0));
        assertEquals(IncomeOccurrenceView.projected(FEB, LocalDate.of(2026, 2, 5), AMOUNT), views.get(1));
        assertEquals(IncomeOccurrenceView.projected(MAR, LocalDate.of(2026, 3, 5), AMOUNT), views.get(2));
    }

    @Test
    void shouldNotReportMonthsBeforeTheStartOrAfterTheEnd() {
        final IncomeSchedule schedule = buildSchedule(
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY), FEB, MAR);

        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                schedule, List.of(), buildReviewedCalendar(2026), JAN, APR);

        assertEquals(2, views.size());
        assertEquals(FEB, views.get(0).nominalMonth());
        assertEquals(MAR, views.get(1).nominalMonth());
    }

    @Test
    void shouldReportMaterializedMonthWithTheFrozenDateEvenWhenTheCalendarWouldMoveIt() {
        // Jan 5 2026 was materialized; a holiday on it would now push the date to Jan 6.
        final HolidayCalendar calendarWithHoliday = buildCalendar(Set.of(2026), buildHoliday(2026, 1, 5));
        final IncomeOccurrence occurrence = buildOccurrence(JAN, LocalDate.of(2026, 1, 5));

        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                buildSchedule(JAN), List.of(occurrence), calendarWithHoliday, JAN, FEB);

        assertEquals(State.MATERIALIZED, views.get(0).state());
        assertEquals(LocalDate.of(2026, 1, 5), views.get(0).plannedDate());
        assertEquals(MOVEMENT_CODE, views.get(0).movementCode());
        assertEquals(State.PROJECTED, views.get(1).state());
    }

    @Test
    void shouldNotProjectAMonthAgainWhenItIsMaterializedUnderAnotherDate() {
        // Same nominal month, other date: still one occurrence for the month, never two.
        final IncomeOccurrence occurrence = buildOccurrence(JAN, LocalDate.of(2026, 1, 3));

        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                buildSchedule(JAN), List.of(occurrence), buildReviewedCalendar(2026), JAN, JAN);

        assertEquals(1, views.size());
        assertEquals(State.MATERIALIZED, views.get(0).state());
    }

    @Test
    void shouldReportCalendarNotReviewedInsteadOfAssumingBusinessDays() {
        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                buildSchedule(JAN), List.of(), buildReviewedCalendar(2026), JAN, YearMonth.of(2027, 1));

        final IncomeOccurrenceView january2027 = views.get(views.size() - 1);
        assertEquals(State.CALENDAR_NOT_REVIEWED, january2027.state());
        assertEquals(Integer.valueOf(2027), january2027.pendingYear());
        assertNull(january2027.plannedDate());
        assertNull(january2027.amount());
    }

    @Test
    void shouldReportBusinessDayNotFoundWhenTheMonthHasFewerBusinessDaysThanTheRulePosition() {
        // The 5th business day of February 2026 does not exist when every weekday is a holiday.
        final HolidayCalendar everyWeekdayOfFebruaryIsAHoliday = buildCalendar(Set.of(2026),
                IntStream.rangeClosed(1, 28)
                        .mapToObj(day -> LocalDate.of(2026, 2, day))
                        .filter(date -> date.getDayOfWeek().getValue() <= 5)
                        .map(date -> buildHoliday(2026, 2, date.getDayOfMonth()))
                        .toArray(Holiday[]::new));
        final IncomeSchedule schedule = buildSchedule(
                IncomeDateRule.nthBusinessDay(5, BusinessWeek.MONDAY_TO_FRIDAY), FEB, FEB);

        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                schedule, List.of(), everyWeekdayOfFebruaryIsAHoliday, FEB, FEB);

        assertEquals(State.BUSINESS_DAY_NOT_FOUND, views.get(0).state());
    }

    @Test
    void shouldKeepOnlyMaterializedMonthsWhenTheScheduleIsInactive() {
        final IncomeOccurrence occurrence = buildOccurrence(JAN, LocalDate.of(2026, 1, 5));

        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                buildInactive(buildSchedule(JAN)), List.of(occurrence), buildReviewedCalendar(2026), JAN, MAR);

        assertEquals(1, views.size());
        assertEquals(State.MATERIALIZED, views.get(0).state());
    }

    @Test
    void shouldReportNothingWhenTheRangeIsEmpty() {
        assertTrue(IncomeOccurrenceResolver.resolve(
                buildSchedule(JAN), List.of(), buildReviewedCalendar(2026), MAR, FEB).isEmpty());
    }

    @Test
    void shouldFindOnlyTheMonthsWhoseDateMovesWithTheProposedCalendar() {
        // Holiday on Mar 5 2026 (Thursday): the postponed date goes from Mar 5 to Mar 6.
        final HolidayCalendar current = buildReviewedCalendar(2026);
        final HolidayCalendar proposed = current.replacingYear(2026, List.of(buildHoliday(2026, 3, 5)));

        final List<IncomeOccurrenceChange> changes = IncomeOccurrenceResolver.findChanges(
                buildSchedule(JAN), List.of(), current, proposed, JAN, APR);

        assertEquals(1, changes.size());
        assertEquals(LocalDate.of(2026, 3, 5), changes.get(0).before().plannedDate());
        assertEquals(LocalDate.of(2026, 3, 6), changes.get(0).after().plannedDate());
    }

    @Test
    void shouldNotListAMaterializedMonthAsChangedByTheCalendar() {
        final HolidayCalendar current = buildReviewedCalendar(2026);
        final HolidayCalendar proposed = current.replacingYear(2026, List.of(buildHoliday(2026, 3, 5)));
        final IncomeOccurrence materializedMarch = buildOccurrence(MAR, LocalDate.of(2026, 3, 5));

        final List<IncomeOccurrenceChange> changes = IncomeOccurrenceResolver.findChanges(
                buildSchedule(JAN), List.of(materializedMarch), current, proposed, JAN, APR);

        assertTrue(changes.isEmpty());
    }
}
