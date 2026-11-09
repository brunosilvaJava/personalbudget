package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InstallmentBillCreateTest {

    @Test
    void shouldCreateNewInstallmentBill() {

        OperationType operationType = OperationType.DEBIT;
        LocalDate purchaseDate = LocalDate.now().minusDays(1);
        String description = "Test";
        int installmentTotal = 1;

        InstallmentBill newInstallmentBill = InstallmentBillFactory.buildModel(
                operationType, description, BigDecimal.TEN, purchaseDate, installmentTotal);

        assertNotNull(newInstallmentBill.getCode());
        assertEquals(InstallmentBillStatus.PENDING, newInstallmentBill.getStatus());
        assertEquals(0, newInstallmentBill.getInstallmentCount());
        assertEquals(purchaseDate.plusMonths(1), newInstallmentBill.getNextInstallmentDate());
        assertEquals(operationType, newInstallmentBill.getOperationType());
        assertEquals(purchaseDate, newInstallmentBill.getPurchaseDate());
        assertEquals(description, newInstallmentBill.getDescription());
        assertEquals(installmentTotal, newInstallmentBill.getInstallmentTotal());
    }

    @Test
    void shouldCreateNewInstallmentBillWithDefaultPurchaseDate() {

        OperationType operationType = OperationType.DEBIT;
        String description = "Test";
        int installmentTotal = 1;

        InstallmentBill newInstallmentBill = InstallmentBillFactory.buildModel(
                operationType, description, BigDecimal.TEN, null, installmentTotal);

        assertNotNull(newInstallmentBill.getCode());
        assertEquals(InstallmentBillStatus.PENDING, newInstallmentBill.getStatus());
        assertEquals(0, newInstallmentBill.getInstallmentCount());
        assertEquals(LocalDate.now().plusMonths(1), newInstallmentBill.getNextInstallmentDate());
        assertEquals(operationType, newInstallmentBill.getOperationType());
        assertEquals(LocalDate.now(), newInstallmentBill.getPurchaseDate());
        assertEquals(description, newInstallmentBill.getDescription());
        assertEquals(installmentTotal, newInstallmentBill.getInstallmentTotal());
    }

    @Test
    void shouldKeepInstallmentAmountWhenGeneratingFinancialMovement() {
        OperationType operationType = OperationType.DEBIT;
        LocalDate purchaseDate = LocalDate.now();
        BigDecimal installmentAmount = new BigDecimal("125.00");
        int installmentTotal = 12;

        InstallmentBill installmentBill = InstallmentBillFactory.buildModel(
                operationType, "Test", installmentAmount, purchaseDate, installmentTotal);

        FinancialMovement financialMovement = FinancialMovementMapper.INSTANCE
                .toFinancialMovement(installmentBill, FinancialMovementStatus.PENDING);

        assertEquals(0, installmentAmount.negate().compareTo(financialMovement.amount()));
        assertEquals(FinancialMovementStatus.PENDING, financialMovement.status());
        assertEquals(installmentBill.getCode(), financialMovement.recurrenceBillCode());
    }

    @Test
    void shouldMapInstallmentCountAndNextInstallmentDateToResponse() {
        final LocalDate purchaseDate = LocalDate.of(2026, 1, 1);
        final InstallmentBill installmentBill = new InstallmentBill(
                null, OperationType.DEBIT, "Test", BigDecimal.TEN, InstallmentBillStatus.PENDING,
                purchaseDate, 5, null, null, null);

        final var response = InstallmentBillMapper.INSTANCE.toResponse(installmentBill);

        assertEquals(0, response.installmentCount());
        assertEquals(purchaseDate.plusMonths(1), response.nextInstallmentDate());
    }

    @Test
    void shouldMapNullNextInstallmentDateWhenInstallmentBillIsDone() {
        final InstallmentBill installmentBill = new InstallmentBill(
                java.util.UUID.randomUUID(), OperationType.DEBIT, "Test", BigDecimal.TEN,
                InstallmentBillStatus.DONE, LocalDate.of(2026, 1, 1), 1, 1,
                LocalDate.of(2026, 1, 31), null);

        final var response = InstallmentBillMapper.INSTANCE.toResponse(installmentBill);

        assertNull(response.nextInstallmentDate());
    }
}
