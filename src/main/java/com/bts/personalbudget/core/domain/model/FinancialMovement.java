package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import com.bts.personalbudget.exception.InvalidFieldsException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        Boolean flagActive,
        UUID recurrenceBillCode,
        LocalDate occurrenceDate,
        UUID categoryCode,
        LocalDate competenceDate,
        Boolean competenceOverridden,
        UUID cardPurchaseCode,
        UUID cardInvoiceCode,
        Integer installmentNumber,
        UUID thirdPartyCode,
        UUID receivableSourceCode
) implements BalanceCalcData {

    private static final String MSG_FIELD_NULL_OR_EMPTY = "deve ser informado um valor válido quando o status for ";

    /**
     * Used when generating a movement from a recurrence bill (FixedBill/InstallmentBill).
     * {@code occurrenceDate} is the recurrence occurrence's immutable idempotency key, unlike
     * {@code dueDate}, which the user can edit via PATCH. For income schedules it is the nominal
     * month's first day; the planned date remains the due date and competence.
     */
    public FinancialMovement(
            OperationType operationType,
            String description,
            BigDecimal amount,
            LocalDateTime movementDate,
            LocalDateTime dueDate,
            FinancialMovementStatus status,
            UUID recurrenceBillCode,
            LocalDate occurrenceDate
    ) {
        this(null, operationType, description, amount, null, movementDate, dueDate, null,
                status, Boolean.TRUE, recurrenceBillCode, occurrenceDate, null, null, null,
                null, null, null, null, null);
    }

    /**
     * Without the card link (purchase, invoice and installment number), which is read-only on a
     * movement: it is set by the card purchase that generated the installment, never by a client.
     */
    public FinancialMovement(
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
            UUID recurrenceBillCode,
            LocalDate occurrenceDate,
            UUID categoryCode,
            LocalDate competenceDate,
            Boolean competenceOverridden
    ) {
        this(code, operationType, description, amount, amountPaid, movementDate, dueDate, payDate,
                status, flagActive, recurrenceBillCode, occurrenceDate, categoryCode, competenceDate,
                competenceOverridden, null, null, null, null, null);
    }

    /**
     * Without competence: FinancialMovementService.create derives it (automatic) when it is null.
     */
    public FinancialMovement(
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
            UUID recurrenceBillCode,
            LocalDate occurrenceDate,
            UUID categoryCode
    ) {
        this(code, operationType, description, amount, amountPaid, movementDate, dueDate, payDate,
                status, flagActive, recurrenceBillCode, occurrenceDate, categoryCode, null, null,
                null, null, null, null, null);
    }

    /** Compatibility constructor for an already-resolved card-installment link. */
    public FinancialMovement(final UUID code, final OperationType operationType, final String description,
                             final BigDecimal amount, final BigDecimal amountPaid, final LocalDateTime movementDate,
                             final LocalDateTime dueDate, final LocalDateTime payDate,
                             final FinancialMovementStatus status, final Boolean flagActive,
                             final UUID recurrenceBillCode, final LocalDate occurrenceDate, final UUID categoryCode,
                             final LocalDate competenceDate, final Boolean competenceOverridden,
                             final UUID cardPurchaseCode, final UUID cardInvoiceCode,
                             final Integer installmentNumber) {
        this(code, operationType, description, amount, amountPaid, movementDate, dueDate, payDate, status,
                flagActive, recurrenceBillCode, occurrenceDate, categoryCode, competenceDate, competenceOverridden,
                cardPurchaseCode, cardInvoiceCode, installmentNumber, null, null);
    }

    public FinancialMovement {
        validations(status, dueDate, payDate, amountPaid);
        if (code == null) {
            code = UUID.randomUUID();
        }
        competenceOverridden = Boolean.TRUE.equals(competenceOverridden);
    }

    public FinancialMovement withCompetence(final LocalDate newCompetenceDate, final boolean overridden) {
        return new FinancialMovement(code, operationType, description, amount, amountPaid, movementDate,
                dueDate, payDate, status, flagActive, recurrenceBillCode, occurrenceDate, categoryCode,
                newCompetenceDate, overridden, cardPurchaseCode, cardInvoiceCode, installmentNumber,
                thirdPartyCode, receivableSourceCode);
    }

    /** The codes of the purchase and invoice an installment belongs to; set when a movement is read. */
    public FinancialMovement withCardLink(final UUID newCardPurchaseCode, final UUID newCardInvoiceCode) {
        return new FinancialMovement(code, operationType, description, amount, amountPaid, movementDate,
                dueDate, payDate, status, flagActive, recurrenceBillCode, occurrenceDate, categoryCode,
                competenceDate, competenceOverridden, newCardPurchaseCode, newCardInvoiceCode, installmentNumber,
                thirdPartyCode, receivableSourceCode);
    }

    public FinancialMovement withStatus(final FinancialMovementStatus newStatus) {
        return new FinancialMovement(code, operationType, description, amount, amountPaid, movementDate,
                dueDate, payDate, newStatus, flagActive, recurrenceBillCode, occurrenceDate, categoryCode,
                competenceDate, competenceOverridden, cardPurchaseCode, cardInvoiceCode, installmentNumber,
                thirdPartyCode, receivableSourceCode);
    }

    public FinancialMovement withPaymentSummary(final BigDecimal newAmountPaid,
                                               final LocalDateTime newPayDate,
                                               final FinancialMovementStatus newStatus) {
        return new FinancialMovement(code, operationType, description, amount, newAmountPaid, movementDate,
                dueDate, newPayDate, newStatus, flagActive, recurrenceBillCode, occurrenceDate, categoryCode,
                competenceDate, competenceOverridden, cardPurchaseCode, cardInvoiceCode, installmentNumber,
                thirdPartyCode, receivableSourceCode);
    }

    public FinancialMovement withReceivableSource(final UUID sourceCode) {
        return new FinancialMovement(code, operationType, description, amount, amountPaid, movementDate,
                dueDate, payDate, status, flagActive, recurrenceBillCode, occurrenceDate, categoryCode,
                competenceDate, competenceOverridden, cardPurchaseCode, cardInvoiceCode, installmentNumber,
                thirdPartyCode, sourceCode);
    }

    @Override
    public BigDecimal getBalanceCalcValue() {
        final BigDecimal balanceCalcValue = switch (status) {
            case PENDING, LATE -> amount.subtract(amountPaid == null ? ZERO : amountPaid);
            case PAID_OUT -> amountPaid;
        };
        // Negates the value picked above, not amount: for a partially paid debit, amountPaid is
        // what left the balance.
        if (operationType == OperationType.DEBIT) {
            if (balanceCalcValue != null && balanceCalcValue.compareTo(ZERO) > 0) {
                return balanceCalcValue.negate();
            }
        }
        return balanceCalcValue;
    }

    @Override
    public LocalDate findBalanceCalcDate() {
        return switch (status) {
            case PENDING, LATE -> dueDate.toLocalDate();
            case PAID_OUT -> payDate == null ? dueDate.toLocalDate() : payDate.toLocalDate();
        };
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    @Override
    public UUID getCategoryCode() {
        return categoryCode;
    }

    @Override
    public PaymentStatus findStatus() {
        return switch (status) {
            case PAID_OUT -> PaymentStatus.DONE;
            default -> PaymentStatus.PENDING;
        };
    }

    @Override
    public boolean isPassThrough() {
        return thirdPartyCode != null;
    }

    private void validations(FinancialMovementStatus status,
                             LocalDateTime dueDate,
                             LocalDateTime payDate,
                             BigDecimal amountPaid) {

        final Map<String, String> errorsMsg = new HashMap<>();

        if (status == PAID_OUT) {
            if (amountPaid == null || amountPaid.compareTo(ZERO) == 0) {
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
