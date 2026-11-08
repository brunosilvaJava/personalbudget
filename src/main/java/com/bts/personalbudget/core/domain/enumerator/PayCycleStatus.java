package com.bts.personalbudget.core.domain.enumerator;

/**
 * The state of a cycle as the user sees it, derived from its dates and the clock.
 */
public enum PayCycleStatus {
    /** Starts after today. */
    PLANNED,
    /** Today is inside the cycle. */
    OPEN,
    /** Already ended and awaiting manual closure. */
    PENDING_CLOSE,
    /** Closed manually and backed by an immutable snapshot. */
    CLOSED,
    /** Discarded before it started. */
    CANCELLED
}
