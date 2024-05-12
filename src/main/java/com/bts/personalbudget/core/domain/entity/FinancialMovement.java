package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.repository.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static java.math.BigDecimal.ZERO;

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
        Boolean flagActive
) {

    private static final String MSG_FIELD_NULL_OR_EMPTY = "deve ser informado quando o status for ";

    public FinancialMovement {

        if (code == null) {
            code = UUID.randomUUID();
        }

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

