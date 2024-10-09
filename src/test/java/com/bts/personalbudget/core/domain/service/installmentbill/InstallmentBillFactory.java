package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class InstallmentBillFactory {

    public static InstallmentBill buildModel(OperationType operationType, String description, BigDecimal amount,
                                             LocalDate purchaseDate, Integer installmentTotal) {
        return new InstallmentBill(null, null, operationType, description,
                amount, null, purchaseDate, installmentTotal,
                null, null, null);
    }

    public static InstallmentBill buildModel() {
        return InstallmentBillMapper.INSTANCE.toModel(entityBuilder().build());
    }

    public static InstallmentBillEntity buildEntity() {
        return entityBuilder().build();
    }

    public static InstallmentBillEntity.InstallmentBillEntityBuilder entityBuilder() {
        return InstallmentBillEntity.builder()
                .id(1L)
                .code(UUID.randomUUID())
                .flagActive(Boolean.TRUE)
                .operationType(OperationType.DEBIT)
                .description("Test")
                .amount(BigDecimal.TEN)
                .status(InstallmentBillStatus.PENDING)
                .purchaseDate(LocalDate.now())
                .installmentTotal(1)
                .installmentCount(0)
                .nextInstallmentDate(LocalDate.now().plusMonths(1));
    }

}
