package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static java.math.BigDecimal.ZERO;

public record InstallmentBillResponse(
        UUID code,
        @JsonProperty("operation_type")
        OperationType operationType,
        String description,
        BigDecimal amount,
        InstallmentBillStatus status,
        @JsonProperty("purchase_date")
        LocalDate purchaseDate,
        @JsonProperty("installment_total")
        Integer installmentTotal
) {
    public InstallmentBillResponse {
        if (operationType == OperationType.DEBIT) {
            if (amount != null && amount.compareTo(ZERO) < 0) {
                amount = amount.negate();
            }
        }
    }
}
