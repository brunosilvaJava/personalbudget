package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalc;
import com.bts.personalbudget.exception.InvalidFieldsException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static java.math.BigDecimal.ZERO;

@Builder
@Slf4j
public record FinancialMovement(
        UUID code,
        OperationType operationType,
        String description,
        BigDecimal amount,
        BigDecimal amountPaid,
        LocalDateTime movementDate,
        LocalDateTime dueDate,
        LocalDateTime payDate,
        FinancialMovementStatus status,
        Boolean flagActive,
        UUID recurrenceBillCode
) implements BalanceCalc {

    private static final String MSG_FIELD_NULL_OR_EMPTY = "deve ser informado quando o status for ";

    public FinancialMovement(
            OperationType operationType,
            String description,
            BigDecimal amount,
            LocalDateTime movementDate,
            LocalDateTime dueDate,
            FinancialMovementStatus status,
            UUID recurrenceBillCode
    ) {
        this(null, operationType, description, amount, null, movementDate, dueDate, null,
                status, Boolean.TRUE, recurrenceBillCode);
    }

    public FinancialMovement {
        validations(status, dueDate, payDate, amountPaid);
        if (code == null) {
            code = UUID.randomUUID();
        }
    }

    public Optional<BigDecimal> findAmountForCalc() {
        if (amount == null) {
            return Optional.empty();
        }
        return operationType == OperationType.CREDIT ? Optional.of(amount) : Optional.of(amount.negate());
    }

    public Optional<BigDecimal> findAmountPaidForCalc() {
        if (amountPaid == null) {
            return Optional.empty();
        }
        return operationType == OperationType.CREDIT ? Optional.of(amountPaid) : Optional.of(amountPaid.negate());
    }

    @Override
    public BigDecimal getBalanceCalcValue() {
        return switch (status) {
            case PENDING, LATE -> amount;
            case PAID_OUT -> amountPaid;
        };
    }

    @Override
    public LocalDate getBalanceCalcDate() {
        return switch (status) {
            case PENDING -> dueDate.toLocalDate();
            case PAID_OUT -> payDate.toLocalDate();
            case LATE -> LocalDate.now();
        };
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    private void validations(FinancialMovementStatus status,
                             LocalDateTime dueDate,
                             LocalDateTime payDate,
                             BigDecimal amountPaid) {

        final Map<String, String> errorsMsg = new HashMap<>();

        if (status == PAID_OUT) {
            if (payDate == null) {
                errorsMsg.put("payDate", MSG_FIELD_NULL_OR_EMPTY + PAID_OUT);
            }
            if (amountPaid == null || amountPaid.compareTo(ZERO) < 1) {
                errorsMsg.put("amountPaid", MSG_FIELD_NULL_OR_EMPTY + PAID_OUT);
            }
        }

        if (status == LATE) {
            if (dueDate == null) {
                errorsMsg.put("dueDate", MSG_FIELD_NULL_OR_EMPTY + LATE);
            } else if (!dueDate.isBefore(LocalDateTime.now())) {
                errorsMsg.put("dueDate", "a data de vencimento deve ser anterior à data atual");
            }
        }

        if (!errorsMsg.isEmpty()) {
            log.error("c=FinancialMovement, msg=campos invalidos, fields={}", errorsMsg);
            throw new InvalidFieldsException("Campos inválidos", FinancialMovement.class, errorsMsg);
        }
    }
}

