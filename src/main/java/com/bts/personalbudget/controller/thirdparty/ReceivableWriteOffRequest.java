package com.bts.personalbudget.controller.thirdparty;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceivableWriteOffRequest(@NotNull @JsonProperty("effective_date") LocalDate effectiveDate,
                                        @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 8, fraction = 2)
                                        BigDecimal amount) { }
