package com.bts.personalbudget.core.domain.enumerator;

public enum PayCycleKind {
    /** Opened by an anchor occurrence and closed by the next one. */
    REGULAR,
    /** A shorter or longer cycle created by an activation or a change of the anchor. */
    TRANSITION
}
