package com.bts.personalbudget.core.domain.service.paycycle;

import java.util.List;

/**
 * The cycle that contains today, if there is one; otherwise the pendencies that explain why not.
 *
 * @param cycle null when no active cycle contains today (or the mode is CALENDAR_MONTH)
 */
public record CurrentPayCycle(PayCycleView cycle, List<PayCyclePending> pending) {
}
