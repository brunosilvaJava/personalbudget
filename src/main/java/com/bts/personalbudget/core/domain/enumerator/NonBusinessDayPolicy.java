package com.bts.personalbudget.core.domain.enumerator;

/**
 * What a FIXED_DAY rule does when its day is not a business day (weekend outside the schedule's
 * business week, or holiday).
 */
public enum NonBusinessDayPolicy {
    /** Moves to the closest business day before it, possibly in the previous month or year. */
    ANTICIPATE,
    /** Moves to the closest business day after it, possibly in the next month or year. */
    POSTPONE,
    /** Keeps the day as it is. */
    KEEP
}
