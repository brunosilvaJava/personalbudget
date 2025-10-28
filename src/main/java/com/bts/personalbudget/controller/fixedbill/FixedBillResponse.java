package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Response data for an fixed bill.")
public record FixedBillResponse (

        @Schema(description = "Unique identifier for the fixed bill", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Type of operation for the fixed bill (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Description of the fixed bill", example = "smartphone purchase")
        String description,

        @Schema(description = "Total amount of the fixed bill", example = "100.00")
        BigDecimal amount,

        @Schema(description = "Recurrence type of the fixed bill", example = "WEEKLY")
        @JsonProperty("recurrence_type")
        RecurrenceType recurrenceType,

        @Schema(description = "Days for fixed bill", example = "2,7,15")
        List<Integer> days,

        @Schema(description = "determines if it is a leap year")
        @JsonProperty("flg_leap_year")
        Boolean flgLeapYear,

        @Schema(description = "Reference year for YEARLY recurrence", example = "2024")
        @JsonProperty("reference_year")
        Integer referenceYear,

        @Schema(description = "Current status of the fixed bill (e.g., ACTIVE, COMPLETED, CANCELED)", example = "ACTIVE")
        FixedBillStatus status,

        @Schema(description = "Start date of the fixed bill", example = "2023-07-12")
        @JsonProperty("start_date")
        LocalDate startDate,

        @Schema(description = "End date of the fixed bill", example = "2024-09-12")
        @JsonProperty("end_date")
        LocalDate endDate
){
}