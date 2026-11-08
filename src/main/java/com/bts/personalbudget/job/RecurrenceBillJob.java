package com.bts.personalbudget.job;

import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.recurrencebill.RecurrenceBillService;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
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
    private final FinancialMovementService financialMovementService;
    private final PayCycleService payCycleService;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.time-zone:America/Sao_Paulo}")
    public void postRecurringBills() {
        final LocalDate today = LocalDate.now(clock);
        log.info("m=postRecurringBills today={}", today);
        recurrenceBillService.postRecurringBills(today);
        markOverdueMovementsAsLate(today);
        refreshPayCycles();
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

    /**
     * After the day's receipts are posted, so the cycles see the occurrences already materialized,
     * keeps the pay cycles up to the current cycle plus two. Its own transaction, and a failure here is
     * logged and goes no further: the next run, tomorrow or at startup, tries again, and the receipts
     * already posted stay posted.
     */
    private void refreshPayCycles() {
        try {
            payCycleService.refresh();
        } catch (final RuntimeException ex) {
            log.error("m=refreshPayCycles, msg=FailedToRefreshPayCycles", ex);
        }
    }

    private void markOverdueMovementsAsLate(final LocalDate today) {
        try {
            financialMovementService.markOverdueAsLate(today);
        } catch (final RuntimeException ex) {
            log.error("m=markOverdueMovementsAsLate, msg=FailedToMarkOverdueMovementsAsLate, today={}", today, ex);
        }
    }

}
