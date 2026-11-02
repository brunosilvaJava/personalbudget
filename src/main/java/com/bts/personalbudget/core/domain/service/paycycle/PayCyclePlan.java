package com.bts.personalbudget.core.domain.service.paycycle;

import java.util.List;

/**
 * What {@link PayCyclePlanner} decided: the whole chain of active cycles as it should be (the cycles
 * that already existed keep their code, the new ones have a fresh one) and the pendencies found.
 *
 * @param cycles  contiguous and in order, each one starting where the previous ends
 * @param pending without duplicates, in the order they were found
 */
public record PayCyclePlan(List<PayCycle> cycles, List<PayCyclePending> pending) {

    public PayCyclePlan {
        cycles = List.copyOf(cycles);
        pending = List.copyOf(pending);
    }
}
