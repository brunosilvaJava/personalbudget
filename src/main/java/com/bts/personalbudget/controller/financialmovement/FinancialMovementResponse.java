package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static java.math.BigDecimal.ZERO;

@Schema(description = "Response representation of a financial movement")
public record FinancialMovementResponse (

        @Schema(description = "Unique identifier for the financial movement", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Type of operation (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Description of the financial movement", example = "Monthly subscription payment")
        String description,

        @Schema(description = "Total amount of the financial movement", example = "150.75")
        BigDecimal amount,

        @Schema(description = "Aggregate amount of active settlements", example = "100.50")
        @JsonProperty("amount_paid")
        BigDecimal amountPaid,

        @Schema(description = "Date and time when the financial movement was created", example = "2024-11-07T15:30:00", type = "string", format = "date-time")
        @JsonProperty("movement_date")
        LocalDateTime movementDate,

        @Schema(description = "Due date for the financial movement", example = "2024-11-30T23:59:59", type = "string", format = "date-time")
        @JsonProperty("due_date")
        LocalDateTime dueDate,

        @Schema(description = "Status of the financial movement (e.g., PENDING, COMPLETED, CANCELLED)", example = "PENDING")
        FinancialMovementStatus status,

        @Schema(description = "Code of the recurrence bill associated with this movement, if any", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("recurrence_bill_code")
        UUID recurrenceBillCode,

        @Schema(description = "Scheduled occurrence date from the recurrence bill this movement was generated from, if any. Immutable.", example = "2026-11-10")
        @JsonProperty("occurrence_date")
        LocalDate occurrenceDate,

        @Schema(description = "Code of the category, if any", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode,

        @Schema(description = "Budget competence date: when the obligation/revenue belongs to the budget, independent of due and pay dates. Null only for legacy recurring movements pending reconciliation", example = "2026-11-01", type = "string", format = "date")
        @JsonProperty("competence_date")
        LocalDate competenceDate,

        @Schema(description = "true when the competence was informed by the client and is not recalculated automatically", example = "false")
        @JsonProperty("competence_overridden")
        Boolean competenceOverridden,

        @Schema(description = "Code of the card purchase this movement is an installment of; null for any other movement. Such a movement is changed through the purchase, never alone", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("card_purchase_code")
        UUID cardPurchaseCode,

        @Schema(description = "Code of the card invoice the installment falls in; null for any other movement", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("card_invoice_code")
        UUID cardInvoiceCode,

        @Schema(description = "Installment number k of the card purchase (1-based); null for any other movement", example = "2")
        @JsonProperty("installment_number")
        Integer installmentNumber
        ,

        @JsonProperty("third_party_code")
        UUID thirdPartyCode
) {
    /** Without the card link, for a movement that is not a card installment. */
    public FinancialMovementResponse(final UUID code, final OperationType operationType, final String description,
                                     final BigDecimal amount, final BigDecimal amountPaid,
                                     final LocalDateTime movementDate, final LocalDateTime dueDate,
                                     final FinancialMovementStatus status, final UUID recurrenceBillCode,
                                     final LocalDate occurrenceDate, final UUID categoryCode,
                                     final LocalDate competenceDate, final Boolean competenceOverridden) {
        this(code, operationType, description, amount, amountPaid, movementDate, dueDate, status,
                recurrenceBillCode, occurrenceDate, categoryCode, competenceDate, competenceOverridden,
                null, null, null, null);
    }

    /** Compatibility constructor for clients that already receive the card link. */
    public FinancialMovementResponse(final UUID code, final OperationType operationType, final String description,
                                     final BigDecimal amount, final BigDecimal amountPaid,
                                     final LocalDateTime movementDate, final LocalDateTime dueDate,
                                     final FinancialMovementStatus status, final UUID recurrenceBillCode,
                                     final LocalDate occurrenceDate, final UUID categoryCode,
                                     final LocalDate competenceDate, final Boolean competenceOverridden,
                                     final UUID cardPurchaseCode, final UUID cardInvoiceCode,
                                     final Integer installmentNumber) {
        this(code, operationType, description, amount, amountPaid, movementDate, dueDate, status,
                recurrenceBillCode, occurrenceDate, categoryCode, competenceDate, competenceOverridden,
                cardPurchaseCode, cardInvoiceCode, installmentNumber, null);
    }

    public FinancialMovementResponse {
        if (operationType == OperationType.DEBIT) {
            if (amount != null && amount.compareTo(ZERO) < 0) {
                amount = amount.negate();
            }
            if (amountPaid != null && amountPaid.compareTo(ZERO) < 0) {
                amountPaid = amountPaid.negate();
            }
        }
    }
}
