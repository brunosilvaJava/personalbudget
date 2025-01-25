package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Request data for updating an existing fixed bill.")
public record FixedBillUpdateRequest (

        @Schema(description = "Unique identifier for the fixed bill to be updated", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Type of operation for the fixed bill (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @NotBlank
        @Schema(description = "Updated description of the fixed bill", example = "Updated laptop purchase")
        String description,

        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Updated total amount of the fixed bill", example = "1550.00")
        BigDecimal amount,

        @Schema(description = "Update recurrence type of the fixed bill", example = "MONTHLY")
        @JsonProperty("recurrence_type")
        RecurrenceType recurrenceType,

        @Schema(description = "Update days of the fixed bill", example = "4,19")
        List<Integer> days,

        @Schema(description = "Update whether it is a leap year or not")
        @JsonProperty("flg_leap_year")
        Boolean flgLeapYear,

        @Schema(description = "Update status of the fixed bill", example = "INACTIVE")
        FixedBillStatus status,

        @Schema(description = "Update start date of the fixed bill", example = "2016-06-29")
        @JsonProperty("start_date")
        LocalDate startDate,

        @Schema(description = "Update start date of the fixed bill", example = "2022-11-15")
        @JsonProperty("end_date")
        LocalDate endDate
){
}

