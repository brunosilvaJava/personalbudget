package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.service.calendar.Holiday;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class HolidayCalendarFactory {

    public static Holiday buildHoliday(final int year, final int month, final int dayOfMonth) {
        return new Holiday(LocalDate.of(year, month, dayOfMonth), "Holiday " + year + "-" + month + "-" + dayOfMonth);
    }

    /**
     * Years that were reviewed and have no holidays at all.
     */
    public static HolidayCalendar buildReviewedCalendar(final Integer... reviewedYears) {
        return new HolidayCalendar(Set.of(), new HashSet<>(Arrays.asList(reviewedYears)));
    }

    public static HolidayCalendar buildCalendar(final Set<Integer> reviewedYears, final Holiday... holidays) {
        return new HolidayCalendar(new HashSet<>(Arrays.asList(holidays)), reviewedYears);
    }
}
