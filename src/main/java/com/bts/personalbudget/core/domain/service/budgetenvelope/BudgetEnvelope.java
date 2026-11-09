package com.bts.personalbudget.core.domain.service.budgetenvelope;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An amount planned for a category in a cycle, with what the cycle already did with it. All
 * amounts are positive except {@code remainingAmount}, which is negative once the envelope is
 * overrun.
 *
 * <ul>
 *   <li>{@code recognizedAmount}: consumption already paid (settlements of the cycle's expenses);</li>
 *   <li>{@code committedAmount}: consumption of the cycle still to be paid (pending, late and
 *       card installments);</li>
 *   <li>{@code consumedAmount} = recognized + committed;</li>
 *   <li>{@code remainingAmount} = planned - consumed;</li>
 *   <li>{@code reservedAmount} = what is still free in the envelope (never negative): it is set
 *       aside, but it is not a dated bank outflow;</li>
 *   <li>{@code overrunAmount} = what was consumed beyond the plan (never negative).</li>
 * </ul>
 */
public record BudgetEnvelope(
        UUID code,
        UUID cycleCode,
        UUID categoryCode,
        String categoryName,
        BigDecimal plannedAmount,
        BigDecimal recognizedAmount,
        BigDecimal committedAmount,
        BigDecimal consumedAmount,
        BigDecimal remainingAmount,
        BigDecimal reservedAmount,
        BigDecimal overrunAmount,
        Long version
) {

    /** An envelope with nothing consumed yet, as a client creates it. */
    public static BudgetEnvelope of(final UUID categoryCode, final BigDecimal plannedAmount) {
        return new BudgetEnvelope(null, null, categoryCode, null, plannedAmount, null, null, null, null, null,
                null, null);
    }
}
