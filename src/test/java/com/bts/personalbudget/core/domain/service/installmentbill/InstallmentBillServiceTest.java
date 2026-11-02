package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InstallmentBillServiceTest {

    @InjectMocks
    private InstallmentBillService service;

    @Mock
    private InstallmentBillRepository repository;

    @Mock
    private FinancialMovementService financialMovementService;

    @Spy
    private FinancialMovementMapper financialMovementMapper = FinancialMovementMapper.INSTANCE;

    @Test
    void shouldCreateInstallmentBill() {
        InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                OperationType.CREDIT, "Test", BigDecimal.TEN, LocalDate.now(), 1);
        service.create(installmentBill);
        verify(repository).save(installmentBill);
    }

//    @Test
//    void shouldFindAllInstallmentBill() {
//        InstallmentBill installmentBill = InstallmentBillFactory.buildModel();
//        when(repository.findAll()).thenReturn(List.of(installmentBill));
//        List<InstallmentBill> installmentBillList = service.findAll();
//        assertFalse(installmentBillList.isEmpty());
//        assertEquals(installmentBill, installmentBillList.stream().findFirst().get());
//    }

    @Test
    void shouldFindInstallmentBillByCode() {
        InstallmentBill installmentBillMock = InstallmentBillFactory.buildModel();
        UUID code = installmentBillMock.getCode();
        when(repository.findByCode(code)).thenReturn(installmentBillMock);
        InstallmentBill installmentBill = service.findByCode(code);
        assertNotNull(installmentBill);
        assertEquals(installmentBillMock, installmentBill);
    }

    @Test
    void shouldUpdateInstallmentBill() {

        InstallmentBill installmentBill = InstallmentBillFactory.buildModel();

        UUID code = installmentBill.getCode();
        OperationType operationType = OperationType.CREDIT;
        String description = "Test Update";
        BigDecimal amount = BigDecimal.valueOf(100L);
        LocalDate purchaseDate = LocalDate.now().minusDays(1);
        Integer installmentTotal = 10;

        when(repository.findByCode(code)).thenReturn(installmentBill);

        service.update(code, operationType, description, amount, purchaseDate, installmentTotal);

        ArgumentCaptor<InstallmentBill> argumentCaptor = ArgumentCaptor.forClass(InstallmentBill.class);

        verify(repository).update(argumentCaptor.capture());

        InstallmentBill savedInstallmentBill = argumentCaptor.getValue();

        assertEquals(operationType, savedInstallmentBill.getOperationType());
        assertEquals(description, savedInstallmentBill.getDescription());
        assertEquals(amount, savedInstallmentBill.getAmount());
        assertEquals(purchaseDate, savedInstallmentBill.getPurchaseDate());
        assertEquals(installmentTotal, savedInstallmentBill.getInstallmentTotal());
    }

    @Test
    void shouldDeleteInstallmentBill() {
        UUID code = UUID.randomUUID();
        service.delete(code);
        verify(repository).delete(code);
    }

    @Test
    void shouldNotDoAnythingIfAlreadyDeletedInstallmentBill() {
        UUID code = UUID.randomUUID();
        doThrow(new InstallmentBillAlreadyDeletedException(code)).when(repository).delete(code);
        service.delete(code);
    }

    @Test
    public void shouldThrowsExceptionWhenNotFoundInstallmentBillByCode() {
        UUID code = UUID.randomUUID();
        doThrow(new RuntimeException()).when(repository).delete(code);
        assertThrows(RuntimeException.class, () -> service.delete(code));
    }

    @Test
    void shouldPostOverdueInstallmentsWhenJobSkippedDays() {
        final UUID code = UUID.randomUUID();
        final LocalDate today = LocalDate.now();
        final LocalDate firstOverdueInstallment = today.minusDays(65);
        final InstallmentBill installmentBill = new InstallmentBill(code, OperationType.DEBIT, "Catch-up test",
                BigDecimal.TEN, InstallmentBillStatus.PENDING, today.minusDays(95), 5,
                0, null, firstOverdueInstallment);

        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(eq(code), any())).thenReturn(false);

        service.postDueInstallments(code, today);

        final ArgumentCaptor<FinancialMovement> captor = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService, times(3)).create(captor.capture());

        final List<LocalDate> postedOccurrences = captor.getAllValues().stream()
                .map(FinancialMovement::occurrenceDate)
                .toList();
        assertEquals(List.of(firstOverdueInstallment, firstOverdueInstallment.plusDays(30), firstOverdueInstallment.plusDays(60)),
                postedOccurrences);
        assertEquals(3, installmentBill.getInstallmentCount());
        assertEquals(InstallmentBillStatus.PENDING, installmentBill.getStatus());
        assertEquals(firstOverdueInstallment.plusDays(90), installmentBill.getNextInstallmentDate());
    }

    @Test
    void shouldConcludeInstallmentBillWhenLastInstallmentIsCaughtUp() {
        final UUID code = UUID.randomUUID();
        final LocalDate today = LocalDate.now();
        final LocalDate lastOverdueInstallment = today.minusDays(10);
        final InstallmentBill installmentBill = new InstallmentBill(code, OperationType.DEBIT, "Catch-up test",
                BigDecimal.TEN, InstallmentBillStatus.PENDING, today.minusDays(40), 1,
                0, null, lastOverdueInstallment);

        when(repository.findForUpdateByCode(code)).thenReturn(installmentBill);
        when(repository.update(any(InstallmentBill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(code, lastOverdueInstallment)).thenReturn(false);

        service.postDueInstallments(code, today);

        verify(financialMovementService, times(1)).create(any(FinancialMovement.class));

        final ArgumentCaptor<InstallmentBill> captor = ArgumentCaptor.forClass(InstallmentBill.class);
        verify(repository).update(captor.capture());
        assertEquals(InstallmentBillStatus.DONE, captor.getValue().getStatus());

        assertEquals(InstallmentBillStatus.DONE, installmentBill.getStatus());
        assertNull(installmentBill.getNextInstallmentDate());
        assertEquals(1, installmentBill.getInstallmentCount());
    }

}
