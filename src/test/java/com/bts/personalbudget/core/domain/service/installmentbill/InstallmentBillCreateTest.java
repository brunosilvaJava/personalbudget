package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(purchaseDate.plusDays(30), newInstallmentBill.getNextInstallmentDate());
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
        assertEquals(LocalDate.now().plusDays(30), newInstallmentBill.getNextInstallmentDate());
        assertEquals(operationType, newInstallmentBill.getOperationType());
        assertEquals(LocalDate.now(), newInstallmentBill.getPurchaseDate());
        assertEquals(description, newInstallmentBill.getDescription());
        assertEquals(installmentTotal, newInstallmentBill.getInstallmentTotal());
    }

}
