package com.bts.personalbudget.controller.thirdparty;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceiptRequest(@NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 8, fraction = 2)
                             BigDecimal amount,
                             @NotNull @JsonProperty("effective_date") LocalDate effectiveDate,
                             @JsonProperty("account_code") UUID accountCode) { }
