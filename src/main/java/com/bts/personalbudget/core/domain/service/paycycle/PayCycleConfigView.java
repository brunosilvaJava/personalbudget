package com.bts.personalbudget.core.domain.service.paycycle;

import java.util.List;

/**
 * The configuration with what is pending in it. The pendencies are calculated when it is read.
 */
public record PayCycleConfigView(PayCycleConfig config, List<PayCyclePending> pending) {
}
