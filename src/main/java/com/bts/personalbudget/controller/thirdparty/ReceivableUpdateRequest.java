package com.bts.personalbudget.controller.thirdparty;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReceivableUpdateRequest(@NotNull @JsonProperty("due_date") LocalDate dueDate) { }
