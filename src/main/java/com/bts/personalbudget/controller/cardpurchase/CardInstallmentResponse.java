package com.bts.personalbudget.controller.cardpurchase;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "One installment of a card purchase. It is also a financial movement.")
public record CardInstallmentResponse(

        @JsonProperty("movement_code")
        @Schema(description = "Code of the financial movement of this installment")
        UUID movementCode,

        @JsonProperty("purchase_code")
        @Schema(description = "Code of the purchase")
        UUID purchaseCode,

        @JsonProperty("installment_number")
        @Schema(description = "Number k of this installment", example = "3")
        int installmentNumber,

        @JsonProperty("installment_count")
        @Schema(description = "Number n of installments of the purchase", example = "10")
        int installmentCount,

        @JsonProperty("invoice_code")
        @Schema(description = "Code of the invoice the installment falls in")
        UUID invoiceCode,

        @JsonProperty("invoice_reference_month")
        @Schema(description = "Month the invoice closes in", example = "2026-12", type = "string")
        YearMonth invoiceReferenceMonth,

        @Schema(description = "Description with the installment, e.g. Geladeira (3/10)")
        String description,

        @Schema(description = "Positive amount of the installment", example = "250.00")
        BigDecimal amount,

        @JsonProperty("amount_paid")
        @Schema(description = "Positive amount already paid", example = "0.00")
        BigDecimal amountPaid,

        @JsonProperty("due_date")
        @Schema(description = "Due date of the invoice", example = "2027-01-05", type = "string", format = "date")
        LocalDate dueDate,

        @JsonProperty("competence_date")
        @Schema(description = "Competence: the due date of the invoice", example = "2027-01-05",
                type = "string", format = "date")
        LocalDate competenceDate,

        @Schema(description = "PENDING, LATE or PAID_OUT", example = "PENDING")
        FinancialMovementStatus status
) {
}
