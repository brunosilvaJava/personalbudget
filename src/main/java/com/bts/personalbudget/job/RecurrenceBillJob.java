package com.bts.personalbudget.job;

import com.bts.personalbudget.core.domain.service.recurrencebill.RecurrenceBillService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RecurrenceBillJob {

    private final RecurrenceBillService recurrenceBillService;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.time-zone:America/Sao_Paulo}")
    public void postRecurringBills() {
        final LocalDate today = LocalDate.now(clock);
        log.info("m=postRecurringBills today={}", today);
        recurrenceBillService.postRecurringBills(today);
    }

    /**
     * Safety net: also runs once right after the application finishes starting up, in case the
     * server was down when the midnight job should have fired. Harmless to run twice for the
     * same day - postRecurringBills is idempotent (see
     * FinancialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate) - and cheap when
     * there is nothing overdue, since the accounts it finds are already caught up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void postRecurringBillsOnStartup() {
        log.info("m=postRecurringBillsOnStartup, msg=RunningStartupCatchUp");
        postRecurringBills();
    }

}
