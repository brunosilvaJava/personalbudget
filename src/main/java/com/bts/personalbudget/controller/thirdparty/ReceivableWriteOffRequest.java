package com.bts.personalbudget.controller.thirdparty;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReceivableWriteOffRequest(@NotNull @JsonProperty("effective_date") LocalDate effectiveDate) { }
