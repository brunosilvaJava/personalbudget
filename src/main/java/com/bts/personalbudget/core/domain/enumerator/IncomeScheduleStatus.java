package com.bts.personalbudget.core.domain.enumerator;

public enum IncomeScheduleStatus {
    /** Generates occurrences and takes part in the balance projection. */
    ACTIVE,
    /** Keeps its history (materialized occurrences) but generates and projects nothing. */
    INACTIVE
}
