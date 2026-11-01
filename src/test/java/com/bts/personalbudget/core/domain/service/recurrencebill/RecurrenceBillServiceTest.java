package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillFactory;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RecurrenceBillServiceTest {

    @InjectMocks
    private RecurrenceBillService service;

    @Mock
    private FinancialMovementService financialMovementService;

    @Mock
    private InstallmentBillService installmentBillService;

    @Mock
    private FixedBillService fixedBillService;

    @Spy
    private FinancialMovementMapper financialMovementMapper = FinancialMovementMapper.INSTANCE;

    @Test
    void mustPostRecurrenceBill() {
        // Cenário - lançar contas da data estipulada
        InstallmentBill installmentBillMock = InstallmentBillFactory.buildModel();
        LocalDate dueDate = installmentBillMock.getNextInstallmentDate();
        FixedBill fixedBillMock = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, Set.of(1));

        // Mocks
        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of(installmentBillMock));
        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of(fixedBillMock));

        // Teste - lançar contas recorrentes - fixas e parceladas com vencimento na data informada
        service.postRecurringBills(dueDate);

        // Validações
        // Not comparing FinancialMovement instances by equals(): the mapper generates a new
        // random code for each movement it builds, so two independently-built instances for the
        // same bill never compare equal. Assert on the content that matters instead.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinancialMovement>> captor = ArgumentCaptor.forClass(List.class);
        verify(financialMovementService).create(captor.capture());
        List<FinancialMovement> createdMovements = captor.getValue();

        assertEquals(2, createdMovements.size());
        assertTrue(createdMovements.stream().anyMatch(fm -> fixedBillMock.getCode().equals(fm.recurrenceBillCode())));
        assertTrue(createdMovements.stream().anyMatch(fm -> installmentBillMock.getCode().equals(fm.recurrenceBillCode())));
        verify(installmentBillService).updateNextInstallmentDate(installmentBillMock);
        verify(fixedBillService).updateNextDueDate(fixedBillMock, dueDate.plusDays(1));
    }

    @Test
    void mustNotPostRecurrenceBill() {
        LocalDate dueDate = LocalDate.now();

        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of());
        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of());

        service.postRecurringBills(dueDate);

        verify(financialMovementService).create(List.of());
        verify(installmentBillService, never()).updateNextInstallmentDate(any());
        verify(fixedBillService, never()).updateNextDueDate(any(), any());
    }

    @Test
    void shouldNotPostFixedBillWhenDueDateIsAfterEndDate() {
        LocalDate dueDate = LocalDate.of(2026, 6, 10);
        FixedBill fixedBillMock = FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(10),
                FixedBillFactory.START_DATE, LocalDate.of(2026, 1, 1),
                FixedBillFactory.END_DATE, LocalDate.of(2026, 5, 15),
                FixedBillFactory.NEXT_DUE_DATE, dueDate
        ));

        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of());
        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of(fixedBillMock));

        service.postRecurringBills(dueDate);

        verify(financialMovementService).create(List.of());
        verify(fixedBillService).updateNextDueDate(fixedBillMock, dueDate.plusDays(1));
    }

    @Test
    void shouldNotDuplicateMovementWhenJobRunsTwiceForSameDate() {
        LocalDate dueDate = LocalDate.of(2026, 11, 10);
        FixedBill fixedBillMock = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, Set.of(10));
        fixedBillMock.setNextDueDate(dueDate);

        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of(fixedBillMock));
        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of());
        // First run: nothing posted yet. Second run (crash-retry or the job firing twice before
        // next_due_date advances): the movement from the first run already exists.
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(fixedBillMock.getCode(), dueDate))
                .thenReturn(false, true);

        service.postRecurringBills(dueDate);
        service.postRecurringBills(dueDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinancialMovement>> captor = ArgumentCaptor.forClass(List.class);
        verify(financialMovementService, times(2)).create(captor.capture());

        assertEquals(1, captor.getAllValues().get(0).size());
        assertEquals(0, captor.getAllValues().get(1).size());
    }

    @Test
    void shouldNotDuplicateMovementWhenManualEndpointCalledAfterJob() {
        InstallmentBill installmentBillMock = InstallmentBillFactory.buildModel();
        LocalDate dueDate = installmentBillMock.getNextInstallmentDate();

        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of());
        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of(installmentBillMock));
        // The midnight job already posted this occurrence; calling POST /util/recurrence_bill
        // manually afterwards for the same date must not create a second movement.
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(installmentBillMock.getCode(), dueDate))
                .thenReturn(true);

        service.postRecurringBills(dueDate);

        verify(financialMovementService).create(List.of());
        verify(installmentBillService).updateNextInstallmentDate(installmentBillMock);
    }

    @Test
    void shouldNotRecreateMovementWhenOccurrenceWasSoftDeleted() {
        LocalDate dueDate = LocalDate.of(2026, 11, 10);
        FixedBill fixedBillMock = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, Set.of(10));
        fixedBillMock.setNextDueDate(dueDate);

        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of(fixedBillMock));
        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of());
        // The movement for this occurrence was soft-deleted by the user (flag_active = false).
        // existsByRecurrenceBillCodeAndOccurrenceDate is deliberately not scoped to flag_active,
        // so it still reports true and the job must not recreate the movement.
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(fixedBillMock.getCode(), dueDate))
                .thenReturn(true);

        service.postRecurringBills(dueDate);

        verify(financialMovementService).create(List.of());
    }
}
