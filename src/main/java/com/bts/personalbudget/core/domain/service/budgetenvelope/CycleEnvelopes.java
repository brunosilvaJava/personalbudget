package com.bts.personalbudget.core.domain.service.budgetenvelope;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The envelopes of a cycle and their totals. {@code projectionIncomplete} is true when the cycle
 * summary has pendencies (an unreviewed calendar year, for instance): the consumed amounts may
 * then be short.
 */
public record CycleEnvelopes(
        UUID cycleCode,
        List<BudgetEnvelope> envelopes,
        BigDecimal totalPlanned,
        BigDecimal totalConsumed,
        BigDecimal totalReserved,
        BigDecimal totalOverrun,
        boolean projectionIncomplete
) {
}
