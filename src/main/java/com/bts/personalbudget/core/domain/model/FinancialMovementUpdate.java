package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.exception.InvalidFieldsException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * The fields a PATCH on a financial movement may change. A null component means "keep what is
 * stored" (categories can be replaced, not removed). Competence has two explicit commands:
 * {@code competenceDate} sets it manually (the movement stops following the automatic rule) and
 * {@code restoreAutomaticCompetence} goes back to the automatic one; they contradict each other
 * and are rejected together.
 */
public record FinancialMovementUpdate(
        UUID code,
        OperationType operationType,
        String description,
        BigDecimal amount,
        BigDecimal amountPaid,
        LocalDateTime movementDate,
        LocalDateTime dueDate,
        LocalDateTime payDate,
        FinancialMovementStatus status,
        UUID categoryCode,
        LocalDate competenceDate,
        boolean restoreAutomaticCompetence
) {

    public FinancialMovementUpdate {
        if (competenceDate != null && restoreAutomaticCompetence) {
            throw new InvalidFieldsException("Campos inválidos", FinancialMovementUpdate.class, Map.of(
                    "competenceDate", "não pode ser informada junto com restore_automatic_competence"));
        }
    }
}
