package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import java.util.List;

/**
 * A cycle as it is read: with its status for today (derived, never stored) and the pendencies that
 * are about it.
 */
public record PayCycleView(PayCycle cycle, PayCycleStatus status, List<PayCyclePending> pending) {
}
