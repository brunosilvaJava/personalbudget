package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentBillUpdateRequest(

        UUID code,

        @JsonProperty("operation_type")
        OperationType operationType,

        @NotBlank
        String description,

        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        BigDecimal amount,

        @JsonProperty("purchase_date")
        LocalDate purchaseDate,

        @Min(1)
        @JsonProperty("installment_total")
        Integer installmentTotal
) {
}
