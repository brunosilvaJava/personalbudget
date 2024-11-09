package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data for creating a new fixed bill.")
public class FixedBillRequest {

    @NotNull
    @Schema(description = "Operation type of the fixed bill.")
    @JsonProperty("operation_type")
    private OperationType operationType;

    @NotNull
    @Size(min = 3, max = 50)
    @Schema(description = "Description of the fixed bill")
    private String description;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = false, message = "Amount must be greater than zero")
    @Schema(description = "Total value of the fixed bill.")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "Recurrence type of the fixed bill.")
    @JsonProperty("recurrence_type")
    private RecurrenceType recurrenceType;

    @NotNull
    @Schema(description = "Days of the fixed bill recurrence depending on the type of recurrence.",
            example = "WEEKLY - 1 to 7 / MONTHLY - 1 to 31 / YEARLY - 1 to 365")
    private List<Integer> days;

    @Schema(description = "Determines whether the record is from a leap year.")
    @JsonProperty("flg_leap_year")
    private Boolean flgLeapYear;

    @Schema(description = "Fixed bill starting date.", format = "date")
    @JsonProperty("start_date")
    private LocalDate startDate;

    @Schema(description = "Fixed bill end date.", format = "date")
    @JsonProperty("end_date")
    private LocalDate endDate;
}
