package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.mapper.InstallmentBillMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InstallmentBillFactory {

    private static final String DESCRIPTION = "Test";
    private static final String UUID = "cd13e332-70bc-46a2-b91a-c731f285a947";

    public static InstallmentBill buildModel(OperationType operationType, String description, BigDecimal amount,
                                             LocalDate purchaseDate, Integer installmentTotal) {
        return new InstallmentBill(null, operationType, description,
                amount, null, purchaseDate, installmentTotal,
                null, null, null);
    }

    public static InstallmentBill buildModel() {
        return InstallmentBillMapper.INSTANCE.toModel(entityBuilder(DESCRIPTION).build());
    }

    public static InstallmentBill buildModel(String description) {
        return InstallmentBillMapper.INSTANCE.toModel(entityBuilder(description).build());
    }

    public static InstallmentBillEntity buildEntity() {
        return entityBuilder(DESCRIPTION).build();
    }

    public static InstallmentBillEntity.InstallmentBillEntityBuilder entityBuilder() {
        return entityBuilder(DESCRIPTION);
    }

    public static InstallmentBillEntity.InstallmentBillEntityBuilder entityBuilder(String description) {
        return InstallmentBillEntity.builder()
                .id(1L)
                .code(java.util.UUID.fromString(UUID))
                .flagActive(Boolean.TRUE)
                .operationType(OperationType.DEBIT)
                .description(description)
                .amount(BigDecimal.TEN)
                .status(InstallmentBillStatus.PENDING)
                .purchaseDate(LocalDate.now())
                .installmentTotal(1)
                .installmentCount(0)
                .nextInstallmentDate(LocalDate.now().plusMonths(1));
    }

}
