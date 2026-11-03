package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of the pay cycle history: what changed, between which cycles, why, and the versions the
 * caller said it had read. Written in the same transaction as the change it records.
 *
 * @param previousCycleCode       the cycle before the boundary the change touched; may be null
 * @param nextCycleCode           the cycle after it; may be null
 * @param oldBoundary             the boundary before the change, the day the next cycle opened
 * @param newBoundary             the boundary after it; null when the boundary did not move to a date
 * @param expectedPreviousVersion version of the previous cycle the caller expected; null when not applicable
 * @param expectedNextVersion     version of the next cycle the caller expected; null when not applicable
 * @param expectedConfigVersion   version of the configuration the caller expected; null when not applicable
 * @param createdDate             when it was recorded; null until it is saved
 */
public record PayCycleAdjustment(
        UUID code,
        PayCycleAdjustmentType type,
        UUID previousCycleCode,
        UUID nextCycleCode,
        LocalDate oldBoundary,
        LocalDate newBoundary,
        String justification,
        Long expectedPreviousVersion,
        Long expectedNextVersion,
        Long expectedConfigVersion,
        Instant createdDate
) {

    public PayCycleAdjustment {
        if (code == null) {
            code = UUID.randomUUID();
        }
    }

    /**
     * The boundary between {@code previous} and {@code next} moved to {@code newBoundary}. Both cycles
     * are the ones as they were read, before the change.
     */
    public static PayCycleAdjustment manualBoundary(final PayCycle previous,
                                                    final PayCycle next,
                                                    final LocalDate newBoundary,
                                                    final String justification,
                                                    final Long expectedPreviousVersion,
                                                    final Long expectedNextVersion) {
        return new PayCycleAdjustment(null, PayCycleAdjustmentType.MANUAL_BOUNDARY, previous.code(), next.code(),
                previous.endDate(), newBoundary, justification, expectedPreviousVersion, expectedNextVersion,
                null, null);
    }

    /**
     * The cycles were turned off. {@code lastStartedCycleCode} is the last cycle that had started (it
     * stays as history) and {@code firstCancelledCycle} the first one that was cancelled; either may be
     * null. The old boundary is where the cancelled cycles began.
     */
    public static PayCycleAdjustment deactivation(final UUID lastStartedCycleCode,
                                                  final UUID firstCancelledCycleCode,
                                                  final LocalDate firstCancelledStart,
                                                  final String justification,
                                                  final Long expectedConfigVersion) {
        return new PayCycleAdjustment(null, PayCycleAdjustmentType.DEACTIVATION, lastStartedCycleCode,
                firstCancelledCycleCode, firstCancelledStart, null, justification, null, null,
                expectedConfigVersion, null);
    }
}
