package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillFactory;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
        FixedBill fixedBillMock = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(1));
        FinancialMovement installmentBillFinancialMovementMock = financialMovementMapper.toFinancialMovement(installmentBillMock, FinancialMovementStatus.PENDING);
        FinancialMovement fixedBillFinancialMovementMock = financialMovementMapper.toFinancialMovement(fixedBillMock, FinancialMovementStatus.PENDING);

        // Mocks
        when(installmentBillService.findByNextInstallmentDate(dueDate)).thenReturn(List.of(installmentBillMock));
        when(fixedBillService.findByNextDueDate(dueDate)).thenReturn(List.of(fixedBillMock));

        // Teste - lançar contas recorrentes - fixas e parceladas com vencimento na data informada
        service.postRecurringBills(dueDate);

        // Validações
        verify(financialMovementService).create(List.of(fixedBillFinancialMovementMock, installmentBillFinancialMovementMock));
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

}
