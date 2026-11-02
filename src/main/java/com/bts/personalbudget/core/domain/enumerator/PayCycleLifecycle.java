package com.bts.personalbudget.core.domain.enumerator;

/**
 * What is stored about a cycle. Whether it is planned, open or waiting to be closed is derived from
 * its dates (see {@link PayCycleStatus}), so it can never be stale when the nightly job is late.
 */
public enum PayCycleLifecycle {
    ACTIVE,
    /** Discarded before it started; kept as a row, never deleted. */
    CANCELLED
}
