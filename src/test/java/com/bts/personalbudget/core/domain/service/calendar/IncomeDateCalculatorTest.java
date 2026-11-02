package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult.BusinessDayNotFound;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult.CalendarNotReviewed;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult.Calculated;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

import static com.bts.personalbudget.core.domain.enumerator.BusinessWeek.MONDAY_TO_FRIDAY;
import static com.bts.personalbudget.core.domain.enumerator.BusinessWeek.MONDAY_TO_SATURDAY;
import static com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.ANTICIPATE;
import static com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.KEEP;
import static com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.POSTPONE;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildCalendar;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomeDateCalculatorTest {

    // ---- FIXED_DAY ----

    @Test
    void shouldKeepNominalDateWhenFixedDayIsBusinessDay() {
        // Oct 20, 2026 is a Tuesday.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(20, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 10), buildReviewedCalendar(2026));

        assertEquals(new Calculated(LocalDate.of(2026, 10, 20)), result);
    }

    @Test
    void shouldAnticipateToFridayWhenFixedDayFallsOnSunday() {
        // Nov 8, 2026 is a Sunday.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(8, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 11), buildReviewedCalendar(2026));

        assertEquals(new Calculated(LocalDate.of(2026, 11, 6)), result);
    }

    @Test
    void shouldAnticipateToSaturdayWhenSaturdayIsBusinessDay() {
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(8, ANTICIPATE, MONDAY_TO_SATURDAY),
                YearMonth.of(2026, 11), buildReviewedCalendar(2026));

        assertEquals(new Calculated(LocalDate.of(2026, 11, 7)), result);
    }

    @Test
    void shouldPostponeToMondayWhenFixedDayFallsOnSunday() {
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(8, POSTPONE, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 11), buildReviewedCalendar(2026));

        assertEquals(new Calculated(LocalDate.of(2026, 11, 9)), result);
    }

    @Test
    void shouldKeepNonBusinessDayWithoutConsultingTheCalendarWhenPolicyIsKeep() {
        // No year is reviewed and the Sunday is kept anyway.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(8, KEEP, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 11), HolidayCalendar.empty());

        assertEquals(new Calculated(LocalDate.of(2026, 11, 8)), result);
    }

    @Test
    void shouldSkipHolidayAndWeekendWhenAnticipating() {
        // Apr 5, 2026 is a Sunday; Apr 4 is a Saturday; Apr 3 is a Friday holiday.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(5, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 4), buildCalendar(Set.of(2026), buildHoliday(2026, 4, 3)));

        assertEquals(new Calculated(LocalDate.of(2026, 4, 2)), result);
    }

    @Test
    void shouldStopAtSaturdayBeforeHolidayWhenSaturdayIsBusinessDay() {
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(5, ANTICIPATE, MONDAY_TO_SATURDAY),
                YearMonth.of(2026, 4), buildCalendar(Set.of(2026), buildHoliday(2026, 4, 3)));

        assertEquals(new Calculated(LocalDate.of(2026, 4, 4)), result);
    }

    @Test
    void shouldPostponePastHolidayAndWeekend() {
        // Apr 3, 2026 is a Friday holiday; the weekend follows.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(3, POSTPONE, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 4), buildCalendar(Set.of(2026), buildHoliday(2026, 4, 3)));

        assertEquals(new Calculated(LocalDate.of(2026, 4, 6)), result);
    }

    @Test
    void shouldUseLastDayOfLeapFebruaryWhenFixedDayExceedsMonthLength() {
        // Feb 29, 2028 is a Tuesday.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(31, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2028, 2), buildReviewedCalendar(2028));

        assertEquals(new Calculated(LocalDate.of(2028, 2, 29)), result);
    }

    @Test
    void shouldApplyPolicyToLastDayOfShortMonthWhenItIsNotBusinessDay() {
        // Feb 2027 has 28 days; Feb 28 is a Sunday, so day 30 anticipates to Friday Feb 26.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(30, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2027, 2), buildReviewedCalendar(2027));

        assertEquals(new Calculated(LocalDate.of(2027, 2, 26)), result);
    }

    @Test
    void shouldAnticipateIntoPreviousYearWhenFirstOfJanuaryIsHoliday() {
        // Jan 1, 2027 is a Friday holiday; Dec 31, 2026 is a Thursday.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(1, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2027, 1), buildCalendar(Set.of(2026, 2027), buildHoliday(2027, 1, 1)));

        assertEquals(new Calculated(LocalDate.of(2026, 12, 31)), result);
    }

    @Test
    void shouldPostponeIntoNextYearWhenLastOfDecemberIsNotBusinessDay() {
        // Dec 31, 2028 is a Sunday and Jan 1, 2029 is a Monday holiday: lands on Tuesday Jan 2.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(31, POSTPONE, MONDAY_TO_FRIDAY),
                YearMonth.of(2028, 12), buildCalendar(Set.of(2028, 2029), buildHoliday(2029, 1, 1)));

        assertEquals(new Calculated(LocalDate.of(2029, 1, 2)), result);
    }

    // ---- NTH_BUSINESS_DAY ----

    @Test
    void shouldCountSaturdayWhenBusinessWeekIsMondayToSaturday() {
        // Jul 1, 2025 is a Tuesday: Tue, Wed, Thu, Fri and Saturday Jul 5 are the 1st to 5th.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(5, MONDAY_TO_SATURDAY),
                YearMonth.of(2025, 7), buildReviewedCalendar(2025));

        assertEquals(new Calculated(LocalDate.of(2025, 7, 5)), result);
    }

    @Test
    void shouldNotCountSaturdayWhenBusinessWeekIsMondayToFriday() {
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(5, MONDAY_TO_FRIDAY),
                YearMonth.of(2025, 7), buildReviewedCalendar(2025));

        assertEquals(new Calculated(LocalDate.of(2025, 7, 7)), result);
    }

    @Test
    void shouldSkipHolidayWhenCountingBusinessDaysOfMondayToFridayWeek() {
        // May 1, 2026 is a Friday holiday: Mon 4, Tue 5, Wed 6, Thu 7 and Fri 8 are the 1st to 5th.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(5, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 5), buildCalendar(Set.of(2026), buildHoliday(2026, 5, 1)));

        assertEquals(new Calculated(LocalDate.of(2026, 5, 8)), result);
    }

    @Test
    void shouldSkipHolidayWhenCountingBusinessDaysOfMondayToSaturdayWeek() {
        // Saturday May 2 is the 1st; then Mon 4, Tue 5, Wed 6 and Thu 7 as the 2nd to 5th.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(5, MONDAY_TO_SATURDAY),
                YearMonth.of(2026, 5), buildCalendar(Set.of(2026), buildHoliday(2026, 5, 1)));

        assertEquals(new Calculated(LocalDate.of(2026, 5, 7)), result);
    }

    @Test
    void shouldCountFirstBusinessDayAfterHolidayOnFirstOfMonth() {
        // Jan 1, 2026 is a Thursday holiday, so the 1st business day is Friday Jan 2.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(1, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 1), buildCalendar(Set.of(2026), buildHoliday(2026, 1, 1)));

        assertEquals(new Calculated(LocalDate.of(2026, 1, 2)), result);
    }

    @Test
    void shouldReturnBusinessDayNotFoundWhenHolidaysUseUpTheMonth() {
        // Every weekday of Feb 2026 is a holiday except Mon 2 and Tue 3: there is no 3rd business day.
        final Set<Holiday> holidays = new HashSet<>();
        YearMonth.of(2026, 2).atDay(1).datesUntil(YearMonth.of(2026, 3).atDay(1))
                .filter(date -> date.getDayOfWeek().getValue() <= 5)
                .filter(date -> date.getDayOfMonth() > 3)
                .forEach(date -> holidays.add(
                        buildHoliday(date.getYear(), date.getMonthValue(), date.getDayOfMonth())));

        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(3, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 2), new HolidayCalendar(holidays, Set.of(2026)));

        assertEquals(new BusinessDayNotFound(YearMonth.of(2026, 2), 3), result);
    }

    // ---- calendar not reviewed ----

    @Test
    void shouldReportUnreviewedYearWhenBusinessDayDependsOnIt() {
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(5, MONDAY_TO_FRIDAY),
                YearMonth.of(2029, 3), buildReviewedCalendar(2026));

        assertEquals(new CalendarNotReviewed(2029), result);
    }

    @Test
    void shouldCountEveryWeekdayWhenReviewedYearHasNoHolidays() {
        // Oct 1, 2026 is a Thursday: "reviewed with no holidays" is not the same as "not prepared".
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.nthBusinessDay(1, MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 10), buildReviewedCalendar(2026));

        assertEquals(new Calculated(LocalDate.of(2026, 10, 1)), result);
    }

    @Test
    void shouldReportNeighbouringYearWhenAnticipationReachesUnreviewedYear() {
        // Jan 1, 2027 is a reviewed holiday, but the anticipation lands on Dec 31, 2026, not reviewed.
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(1, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2027, 1), buildCalendar(Set.of(2027), buildHoliday(2027, 1, 1)));

        assertEquals(new CalendarNotReviewed(2026), result);
    }

    @Test
    void shouldReportUnreviewedYearForFixedDayWhenPolicyNeedsTheCalendar() {
        final IncomeDateResult result = IncomeDateCalculator.calculate(
                IncomeDateRule.fixedDay(20, ANTICIPATE, MONDAY_TO_FRIDAY),
                YearMonth.of(2030, 10), HolidayCalendar.empty());

        assertEquals(new CalendarNotReviewed(2030), result);
    }

    // ---- determinism ----

    @Test
    void shouldReturnSameResultForSameInputs() {
        final IncomeDateRule rule = IncomeDateRule.nthBusinessDay(5, MONDAY_TO_SATURDAY);
        final HolidayCalendar calendar = buildCalendar(Set.of(2026), buildHoliday(2026, 5, 1));

        assertEquals(
                IncomeDateCalculator.calculate(rule, YearMonth.of(2026, 5), calendar),
                IncomeDateCalculator.calculate(rule, YearMonth.of(2026, 5), calendar));
    }
}
