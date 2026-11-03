package com.bts.personalbudget.core.domain.service.paycycle;

/**
 * What an applied boundary adjustment leaves behind: the history entry and the two cycles as they are
 * now, already with their new versions.
 */
public record PayCycleEndAdjustmentResult(PayCycleAdjustment adjustment, PayCycleView previous, PayCycleView next) {
}
