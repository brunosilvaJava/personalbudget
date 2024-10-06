package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentBillRequest(

        @JsonProperty("operation_type")
        OperationType operationType,

        @Size(min = 4, max = 50)
        @NotBlank
        String description,

        @NotNull
        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        BigDecimal amount,

        @JsonProperty("purchase_date")
        LocalDate purchaseDate,

        @NotNull
        @Min(1)
        @JsonProperty("installment_total")
        Integer installmentTotal
) {
}
