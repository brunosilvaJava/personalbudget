package com.bts.personalbudget.core.domain.enumerator;

import java.time.DayOfWeek;

/**
 * Which weekdays count as business days for a schedule. Sunday never does; whether Saturday does is
 * a per-schedule choice (the CLT counts it for the "5th business day" of salary payment).
 */
public enum BusinessWeek {
    MONDAY_TO_FRIDAY(DayOfWeek.FRIDAY),
    MONDAY_TO_SATURDAY(DayOfWeek.SATURDAY);

    private final DayOfWeek lastBusinessDay;

    BusinessWeek(final DayOfWeek lastBusinessDay) {
        this.lastBusinessDay = lastBusinessDay;
    }

    /**
     * @return true when the weekday is a business day of this week, before any holiday is applied
     */
    public boolean includes(final DayOfWeek dayOfWeek) {
        return dayOfWeek.compareTo(lastBusinessDay) <= 0;
    }
}
