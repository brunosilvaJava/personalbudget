package com.bts.personalbudget.job;

import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.recurrencebill.RecurrenceBillService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecurrenceBillJobTest {

    @InjectMocks
    private RecurrenceBillJob recurrenceBillJob;

    @Mock
    private RecurrenceBillService recurrenceBillService;

    @Mock
    private PayCycleService payCycleService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-02T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldUseSaoPauloDateWhenServerClockIsUtcAfter21h() {
        recurrenceBillJob.postRecurringBills();

        verify(recurrenceBillService).postRecurringBills(LocalDate.of(2026, 11, 1));
    }

    @Test
    void shouldRunCatchUpOnStartup() {
        recurrenceBillJob.postRecurringBillsOnStartup();

        verify(recurrenceBillService).postRecurringBills(LocalDate.of(2026, 11, 1));
    }

    @Test
    void shouldRefreshThePayCyclesAfterPostingTheRecurringBills() {
        recurrenceBillJob.postRecurringBills();

        final InOrder inOrder = inOrder(recurrenceBillService, payCycleService);
        inOrder.verify(recurrenceBillService).postRecurringBills(LocalDate.of(2026, 11, 1));
        inOrder.verify(payCycleService).refresh();
    }

    @Test
    void shouldRefreshThePayCyclesOnStartupToo() {
        recurrenceBillJob.postRecurringBillsOnStartup();

        verify(payCycleService).refresh();
    }

    @Test
    void shouldNotLetAFailedRefreshOfThePayCyclesEscape() {
        doThrow(new IllegalStateException("cycles are not contiguous")).when(payCycleService).refresh();

        assertThatCode(() -> recurrenceBillJob.postRecurringBills()).doesNotThrowAnyException();

        verify(recurrenceBillService).postRecurringBills(LocalDate.of(2026, 11, 1));
    }
}
