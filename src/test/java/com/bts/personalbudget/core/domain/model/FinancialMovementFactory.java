package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.AMOUNT;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.AMOUNT_PAID;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.CODE;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.DESCRIPTION;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.MOVEMENT_DATE;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.MOVEMENT_DUE_DATE;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.OPERATION_TYPE;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.PAY_DATE;
import static com.bts.personalbudget.core.domain.model.FinancialMovementFactory.FinancialMovementProperty.STATUS;

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
                MOVEMENT_DUE_DATE, now.toString(),
                PAY_DATE, now.toString(),
                STATUS, FinancialMovementStatus.PENDING.name());
    }

    public static FinancialMovementModel buildModel() {
        FinancialMovementModel financialMovementModel = FinancialMovementModel.builder()
                .operationType(OperationType.valueOf(data().get(OPERATION_TYPE)))
                .description(data().get(DESCRIPTION))
                .amount(new BigDecimal(data().get(AMOUNT)))
                .amountPaid(new BigDecimal(data().get(AMOUNT_PAID)))
                .movementDate(LocalDateTime.parse(data().get(MOVEMENT_DATE)))
                .dueDate(LocalDateTime.parse(data().get(MOVEMENT_DUE_DATE)))
                .payDate(LocalDateTime.parse(data().get(PAY_DATE)))
                .status(FinancialMovementStatus.valueOf(data().get(STATUS)))
                .build();
        financialMovementModel.prePersist();
        return financialMovementModel;
    }

    public static FinancialMovement buildEntity() {
        return buildEntity(data());
    }

    public static FinancialMovement buildEntity(Map<FinancialMovementProperty, String> data) {
        return FinancialMovement.builder()
                .operationType(OperationType.valueOf(data.get(OPERATION_TYPE)))
                .description(data.get(DESCRIPTION))
                .amount(new BigDecimal(data.get(AMOUNT)))
                .amountPaid(new BigDecimal(data.get(AMOUNT_PAID)))
                .movementDate(LocalDateTime.parse(data.get(MOVEMENT_DATE)))
                .dueDate(LocalDateTime.parse(data.get(MOVEMENT_DUE_DATE)))
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
        MOVEMENT_DUE_DATE("dueDate"),
        PAY_DATE("payDate"),
        STATUS("status"),
        ;
        private final String name;
    }

}
