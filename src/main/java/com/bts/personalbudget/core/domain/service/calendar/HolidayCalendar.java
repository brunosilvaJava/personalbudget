package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.exception.InvalidFieldsException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The local holiday calendar: holiday dates plus the years that were reviewed. The two are kept
 * apart on purpose: a reviewed year with no holidays means "checked, there are none", while a year
 * that is not reviewed means "not prepared yet" - dates in it must not be assumed to be business
 * days. Immutable; persistence and editing live elsewhere.
 *
 * @param holidays      at most one holiday per date
 * @param reviewedYears years whose holidays were checked, whether or not any exist
 */
public record HolidayCalendar(Set<Holiday> holidays, Set<Integer> reviewedYears) {

    public HolidayCalendar {
        holidays = holidays == null ? Set.of() : Set.copyOf(holidays);
        reviewedYears = reviewedYears == null ? Set.of() : Set.copyOf(reviewedYears);

        final Set<LocalDate> dates = new HashSet<>();
        for (final Holiday holiday : holidays) {
            if (!dates.add(holiday.date())) {
                throw new InvalidFieldsException("Campos inválidos", HolidayCalendar.class,
                        Map.of("holidays", "há mais de um feriado em " + holiday.date()));
            }
        }
    }

    public static HolidayCalendar empty() {
        return new HolidayCalendar(Set.of(), Set.of());
    }

    /**
     * The calendar as it would be after reviewing {@code year} with exactly these holidays: the
     * year's current holidays are replaced and the year is marked as reviewed. Does not change this
     * calendar; used to preview what a review would do before it is saved.
     *
     * @throws InvalidFieldsException when a holiday is outside the year, or two share a date
     */
    public HolidayCalendar replacingYear(final int year, final Collection<Holiday> yearHolidays) {
        final Set<Holiday> merged = new HashSet<>();
        holidays.stream()
                .filter(holiday -> holiday.date().getYear() != year)
                .forEach(merged::add);
        for (final Holiday holiday : yearHolidays) {
            if (holiday.date().getYear() != year) {
                throw new InvalidFieldsException("Campos inválidos", HolidayCalendar.class,
                        Map.of("holidays", "o feriado " + holiday.date() + " não pertence ao ano " + year));
            }
            merged.add(holiday);
        }
        final Set<Integer> years = new HashSet<>(reviewedYears);
        years.add(year);
        return new HolidayCalendar(merged, years);
    }

    /**
     * @return true when the year's holidays were checked; false when the year is not prepared yet
     */
    public boolean isReviewed(final int year) {
        return reviewedYears.contains(year);
    }

    /**
     * Says nothing about whether the date's year was reviewed: check {@link #isReviewed(int)} first
     * when "no holiday" must mean "none exists" and not "none registered".
     */
    public boolean isHoliday(final LocalDate date) {
        return holidays.stream().anyMatch(holiday -> holiday.date().equals(date));
    }
}
