package com.bts.personalbudget.core.domain.service.paycycle;

import java.util.List;

/**
 * What an activation on a given date would create, without creating it. The cycles are empty when the
 * first one cannot be calculated, and the pendencies say why.
 *
 * @param version the configuration's version, which the activation has to send back to be accepted
 */
public record PayCycleActivationPreview(long version, List<PayCycleView> cycles, List<PayCyclePending> pending) {
}
