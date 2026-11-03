package com.bts.personalbudget.core.domain.enumerator;

public enum PayCycleMode {
    /** The application's behaviour before cycles existed: reports follow the calendar month. */
    CALENDAR_MONTH,
    /** The financial cycle is opened by the anchor income schedule. */
    PAY_CYCLE
}
