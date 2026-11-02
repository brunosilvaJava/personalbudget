package com.bts.personalbudget.job;

import com.bts.personalbudget.core.domain.service.recurrencebill.RecurrenceBillService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        final LocalDate nextInstallmentDate = LocalDate.now(clock);
        log.info("m=postRecurringBills nextInstallmentDate={}", nextInstallmentDate);
        recurrenceBillService.postRecurringBills(nextInstallmentDate);
    }

}
