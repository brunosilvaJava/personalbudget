package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecurrenceBillService is now a pure orchestrator - it finds which accounts have an occurrence
 * due and dispatches each one to FixedBillService.postDueOccurrences /
 * InstallmentBillService.postDueInstallments / IncomeScheduleService.postDueOccurrences
 * through transactionTemplate. The actual posting
 * logic (idempotency, catch-up, concluding a bill) is tested where it lives: see
 * FixedBillServiceTest and InstallmentBillServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class RecurrenceBillServiceTest {

    @InjectMocks
    private RecurrenceBillService service;

    @Mock
    private InstallmentBillService installmentBillService;

    @Mock
    private FixedBillService fixedBillService;

    @Mock
    private IncomeScheduleService incomeScheduleService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        // Runs the account's work right where the real REQUIRES_NEW transaction would, so each
        // test only has to assert on fixedBillService/installmentBillService, not on the
        // transaction plumbing itself.
        lenient().doAnswer(invocation -> {
            final Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void shouldPostFixedBillOccurrencesForEachDueCode() {
        final LocalDate date = LocalDate.of(2026, 6, 15);
        final UUID code1 = UUID.randomUUID();
        final UUID code2 = UUID.randomUUID();

        when(fixedBillService.findCodesByNextDueDateLessThanEqual(date)).thenReturn(List.of(code1, code2));
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(date)).thenReturn(List.of());

        service.postRecurringBills(date);

        verify(fixedBillService).postDueOccurrences(code1, date);
        verify(fixedBillService).postDueOccurrences(code2, date);
    }

    @Test
    void shouldPostInstallmentBillOccurrencesForEachDueCode() {
        final LocalDate date = LocalDate.of(2026, 6, 15);
        final UUID code1 = UUID.randomUUID();
        final UUID code2 = UUID.randomUUID();

        when(fixedBillService.findCodesByNextDueDateLessThanEqual(date)).thenReturn(List.of());
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(date)).thenReturn(List.of(code1, code2));

        service.postRecurringBills(date);

        verify(installmentBillService).postDueInstallments(code1, date);
        verify(installmentBillService).postDueInstallments(code2, date);
    }

    @Test
    void shouldDoNothingWhenNoBillIsDue() {
        final LocalDate date = LocalDate.of(2026, 6, 15);

        when(fixedBillService.findCodesByNextDueDateLessThanEqual(date)).thenReturn(List.of());
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(date)).thenReturn(List.of());

        service.postRecurringBills(date);

        verify(fixedBillService, never()).postDueOccurrences(any(), any());
        verify(installmentBillService, never()).postDueInstallments(any(), any());
    }

    @Test
    void shouldContinueBatchWhenOneBillFails() {
        final LocalDate date = LocalDate.of(2026, 6, 15);
        final UUID failingFixedBillCode = UUID.randomUUID();
        final UUID healthyFixedBillCode = UUID.randomUUID();
        final UUID healthyInstallmentBillCode = UUID.randomUUID();

        when(fixedBillService.findCodesByNextDueDateLessThanEqual(date))
                .thenReturn(List.of(failingFixedBillCode, healthyFixedBillCode));
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(date))
                .thenReturn(List.of(healthyInstallmentBillCode));
        doThrow(new RuntimeException("boom")).when(fixedBillService).postDueOccurrences(failingFixedBillCode, date);

        service.postRecurringBills(date);

        verify(fixedBillService).postDueOccurrences(failingFixedBillCode, date);
        verify(fixedBillService).postDueOccurrences(healthyFixedBillCode, date);
        verify(installmentBillService).postDueInstallments(healthyInstallmentBillCode, date);
    }

    @Test
    void shouldContinueBatchWhenInstallmentBillFails() {
        final LocalDate date = LocalDate.of(2026, 6, 15);
        final UUID failingInstallmentBillCode = UUID.randomUUID();
        final UUID healthyInstallmentBillCode = UUID.randomUUID();

        when(fixedBillService.findCodesByNextDueDateLessThanEqual(date)).thenReturn(List.of());
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(date))
                .thenReturn(List.of(failingInstallmentBillCode, healthyInstallmentBillCode));
        doThrow(new RuntimeException("boom")).when(installmentBillService).postDueInstallments(failingInstallmentBillCode, date);

        service.postRecurringBills(date);

        verify(installmentBillService).postDueInstallments(failingInstallmentBillCode, date);
        verify(installmentBillService).postDueInstallments(healthyInstallmentBillCode, date);
    }

    @Test
    void shouldPostIncomeScheduleOccurrencesForEachCodeToPost() {
        final LocalDate date = LocalDate.of(2026, 6, 15);
        final UUID code1 = UUID.randomUUID();
        final UUID code2 = UUID.randomUUID();

        when(incomeScheduleService.findCodesToPost(date)).thenReturn(List.of(code1, code2));

        service.postRecurringBills(date);

        verify(incomeScheduleService).postDueOccurrences(code1, date);
        verify(incomeScheduleService).postDueOccurrences(code2, date);
    }

    @Test
    void shouldContinueBatchWhenIncomeScheduleFails() {
        final LocalDate date = LocalDate.of(2026, 6, 15);
        final UUID failingCode = UUID.randomUUID();
        final UUID healthyCode = UUID.randomUUID();
        final UUID healthyFixedBillCode = UUID.randomUUID();

        when(fixedBillService.findCodesByNextDueDateLessThanEqual(date)).thenReturn(List.of(healthyFixedBillCode));
        when(incomeScheduleService.findCodesToPost(date)).thenReturn(List.of(failingCode, healthyCode));
        doThrow(new RuntimeException("boom")).when(incomeScheduleService).postDueOccurrences(failingCode, date);

        service.postRecurringBills(date);

        verify(fixedBillService).postDueOccurrences(healthyFixedBillCode, date);
        verify(incomeScheduleService).postDueOccurrences(failingCode, date);
        verify(incomeScheduleService).postDueOccurrences(healthyCode, date);
    }

    @Test
    void shouldNotPostIncomeScheduleWhenNoneIsToBePosted() {
        final LocalDate date = LocalDate.of(2026, 6, 15);

        service.postRecurringBills(date);

        verify(incomeScheduleService, never()).postDueOccurrences(any(), any());
    }
}
