package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.exception.InvalidFieldsException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildCalendar;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolidayCalendarTest {

    @Test
    void shouldTellReviewedYearWithoutHolidaysFromYearNotPrepared() {
        final HolidayCalendar calendar = buildReviewedCalendar(2026);

        assertTrue(calendar.isReviewed(2026));
        assertFalse(calendar.isReviewed(2027));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void shouldFindHolidayByDate() {
        final HolidayCalendar calendar = buildCalendar(Set.of(2026), buildHoliday(2026, 5, 1));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 5, 1)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 5, 2)));
    }

    @Test
    void shouldRejectTwoHolidaysOnTheSameDate() {
        final Set<Holiday> holidays = Set.of(
                new Holiday(LocalDate.of(2026, 5, 1), "Dia do Trabalho"),
                new Holiday(LocalDate.of(2026, 5, 1), "Feriado municipal"));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> new HolidayCalendar(holidays, Set.of(2026)));

        assertTrue(exception.getValidations().containsKey("holidays"));
    }

    @Test
    void shouldNotChangeWhenTheSourceSetsAreModifiedAfterwards() {
        final Set<Holiday> holidays = new HashSet<>();
        final Set<Integer> reviewedYears = new HashSet<>();
        final HolidayCalendar calendar = new HolidayCalendar(holidays, reviewedYears);

        holidays.add(buildHoliday(2026, 5, 1));
        reviewedYears.add(2026);

        assertFalse(calendar.isHoliday(LocalDate.of(2026, 5, 1)));
        assertFalse(calendar.isReviewed(2026));
    }

    @Test
    void shouldBuildEmptyCalendarWithNoReviewedYear() {
        final HolidayCalendar calendar = HolidayCalendar.empty();

        assertFalse(calendar.isReviewed(2026));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 1, 1)));
    }

    @Test
    void shouldRejectHolidayWithoutDateOrName() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> new Holiday(null, " "));

        assertTrue(exception.getValidations().containsKey("date"));
        assertTrue(exception.getValidations().containsKey("name"));
    }

    @Test
    void shouldReplaceOnlyTheHolidaysOfTheYearAndMarkItReviewed() {
        final HolidayCalendar calendar = buildCalendar(Set.of(2026, 2027),
                buildHoliday(2026, 5, 1), buildHoliday(2027, 5, 1));

        final HolidayCalendar replaced = calendar.replacingYear(2026, List.of(buildHoliday(2026, 9, 7)));

        assertFalse(replaced.isHoliday(LocalDate.of(2026, 5, 1)));
        assertTrue(replaced.isHoliday(LocalDate.of(2026, 9, 7)));
        assertTrue(replaced.isHoliday(LocalDate.of(2027, 5, 1)));
        assertTrue(replaced.isReviewed(2026));
    }

    @Test
    void shouldMarkAnUnpreparedYearAsReviewedEvenWithNoHolidays() {
        final HolidayCalendar replaced = HolidayCalendar.empty().replacingYear(2028, List.of());

        assertTrue(replaced.isReviewed(2028));
        assertFalse(replaced.isReviewed(2029));
    }

    @Test
    void shouldNotChangeTheOriginalCalendarWhenReplacingAYear() {
        final HolidayCalendar calendar = buildCalendar(Set.of(2026), buildHoliday(2026, 5, 1));

        calendar.replacingYear(2026, List.of());

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void shouldRejectReplacingAYearWithAHolidayOfAnotherYear() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> HolidayCalendar.empty().replacingYear(2026, List.of(buildHoliday(2027, 1, 1))));

        assertTrue(exception.getValidations().containsKey("holidays"));
    }

    @Test
    void shouldRejectReplacingAYearWithTwoHolidaysOnTheSameDate() {
        final List<Holiday> sameDate = List.of(
                new Holiday(LocalDate.of(2026, 5, 1), "Dia do Trabalho"),
                new Holiday(LocalDate.of(2026, 5, 1), "Feriado municipal"));

        assertThrows(InvalidFieldsException.class, () -> HolidayCalendar.empty().replacingYear(2026, sameDate));
    }
}
