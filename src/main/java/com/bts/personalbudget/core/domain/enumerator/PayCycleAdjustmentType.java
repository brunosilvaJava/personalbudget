package com.bts.personalbudget.core.domain.enumerator;

/**
 * What a row of the pay cycle history records. The manual boundary and the deactivation are written by
 * the pay cycle service today; the other two are accepted by the table for the steps that record them.
 */
public enum PayCycleAdjustmentType {
    /** The user moved the boundary between two cycles. */
    MANUAL_BOUNDARY,
    /** The anchor of the cycles changed (its rule, or another schedule became the anchor). */
    ANCHOR_TRANSITION,
    /** The cycles were turned on. */
    ACTIVATION,
    /** The cycles were turned off. */
    DEACTIVATION
}
