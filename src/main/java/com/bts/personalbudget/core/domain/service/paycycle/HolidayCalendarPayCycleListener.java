package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarReviewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class HolidayCalendarPayCycleListener {

    private final PayCycleService payCycleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCalendarReviewed(final HolidayCalendarReviewedEvent event) {
        log.info("m=onCalendarReviewed, year={}", event.year());
        try {
            payCycleService.refresh();
        } catch (RuntimeException exception) {
            log.error("m=onCalendarReviewed, message=PayCycleRefreshFailed, year={}", event.year(), exception);
        }
    }
}
