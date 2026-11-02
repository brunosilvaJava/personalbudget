package com.bts.personalbudget.core.domain.enumerator;

/**
 * The state of a cycle as the user sees it, derived from its dates and the clock.
 */
public enum PayCycleStatus {
    /** Starts after today. */
    PLANNED,
    /** Today is inside the cycle. */
    OPEN,
    /** Already ended; closing it arrives with E6. */
    PENDING_CLOSE,
    /** Discarded before it started. */
    CANCELLED
}
