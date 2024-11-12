package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import java.time.LocalDate;
import java.util.HashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT_PAID;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.CODE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.DESCRIPTION;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.MOVEMENT_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.DUE_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.OPERATION_TYPE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.PAY_DATE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.STATUS;

public class FinancialMovementFactory {

    public static Map<FinancialMovementProperty, String> data() {
        final LocalDateTime now = LocalDateTime.now();
        return Map.of(
                CODE, UUID.randomUUID().toString(),
                OPERATION_TYPE, OperationType.DEBIT.toString(),
                DESCRIPTION, "Teste",
                AMOUNT, BigDecimal.ONE.toString(),
                AMOUNT_PAID, BigDecimal.ZERO.toString(),
                MOVEMENT_DATE, now.toString(),
                DUE_DATE, now.toString(),
                PAY_DATE, now.toString(),
                STATUS, FinancialMovementStatus.PENDING.name());
    }

    public static Map<FinancialMovementProperty, String> data(Map<FinancialMovementProperty, String> dataMap) {
        final Map<FinancialMovementProperty, String> data = new java.util.HashMap<>(data());
        data.putAll(dataMap);
        return data;
    }

    public static FinancialMovementEntity buildEntity() {
        return buildEntity(data());
    }

    public static FinancialMovementEntity buildEntity(Map<FinancialMovementProperty, String> data) {
        BigDecimal amountPaid = new BigDecimal(data.get(AMOUNT_PAID));
        FinancialMovementEntity financialMovementEntity = FinancialMovementEntity.builder()
                .code(UUID.fromString(data.get(CODE)))
                .operationType(OperationType.valueOf(data.get(OPERATION_TYPE)))
                .description(data.get(DESCRIPTION))
                .amount(amountPaid.equals(BigDecimal.ZERO) ? new BigDecimal(data.get(AMOUNT)): amountPaid)
                .amountPaid(amountPaid)
                .movementDate(LocalDateTime.parse(data.get(MOVEMENT_DATE)))
                .dueDate(LocalDateTime.parse(data.get(DUE_DATE)))
                .payDate(LocalDateTime.parse(data.get(PAY_DATE)))
                .status(FinancialMovementStatus.valueOf(data.get(STATUS)))
                .build();
        financialMovementEntity.prePersist();
        return financialMovementEntity;
    }

    public static FinancialMovement buildModel(LocalDate date, OperationType operationType,
                                               String amountPaid, FinancialMovementStatus status) {
        Map<FinancialMovementProperty, String> dataMap = new HashMap<>();
        dataMap.put(OPERATION_TYPE, operationType.name());
        dataMap.put(AMOUNT_PAID, amountPaid);
        dataMap.put(STATUS, status.name());
        switch (status) {
            case PENDING -> dataMap.put(DUE_DATE, date.atStartOfDay().toString());
            case PAID_OUT -> dataMap.put(PAY_DATE, date.atStartOfDay().toString());
        }
        return buildModel(data(dataMap));
    }

    public static FinancialMovement buildModel() {
        return buildModel(data());
    }

    public static FinancialMovement buildModel(Map<FinancialMovementProperty, String> data) {
        BigDecimal amountPaid = new BigDecimal(data.get(AMOUNT_PAID));
        return FinancialMovement.builder()
                .code(UUID.fromString(data.get(CODE)))
                .operationType(OperationType.valueOf(data.get(OPERATION_TYPE)))
                .description(data.get(DESCRIPTION))
                .amount(amountPaid.equals(BigDecimal.ZERO) ? new BigDecimal(data().get(AMOUNT)): amountPaid)
                .amountPaid(amountPaid)
                .movementDate(LocalDateTime.parse(data.get(MOVEMENT_DATE)))
                .dueDate(LocalDateTime.parse(data.get(DUE_DATE)))
                .payDate(LocalDateTime.parse(data.get(PAY_DATE)))
                .status(FinancialMovementStatus.valueOf(data.get(STATUS)))
                .build();
    }

    @Getter
    @RequiredArgsConstructor
    public enum FinancialMovementProperty {
        CODE("code"),
        OPERATION_TYPE("operationType"),
        DESCRIPTION("description"),
        AMOUNT("amount"),
        AMOUNT_PAID("amountPaid"),
        MOVEMENT_DATE("movementDate"),
        DUE_DATE("dueDate"),
        PAY_DATE("payDate"),
        STATUS("status"),
        ;
        private final String name;
    }

}
