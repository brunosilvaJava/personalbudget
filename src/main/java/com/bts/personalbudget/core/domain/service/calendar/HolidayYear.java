package com.bts.personalbudget.core.domain.service.calendar;

import java.util.List;

/**
 * One year of the holiday calendar as the user maintains it.
 *
 * @param reviewed false means the year is not prepared yet - not that it has no holidays
 * @param holidays the year's holidays ordered by date; empty both for a reviewed year without
 *                 holidays and for a year that is not prepared
 */
public record HolidayYear(int year, boolean reviewed, List<Holiday> holidays) {

    public HolidayYear {
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
    }
}
