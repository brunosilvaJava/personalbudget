package com.bts.personalbudget.controller.incomeschedule;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Response data for an income schedule.")
public record IncomeScheduleResponse(

        @Schema(description = "Unique identifier of the schedule", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Description copied to the generated movements", example = "Salário")
        String description,

        @Schema(description = "Expected amount of each occurrence", example = "5000.00")
        BigDecimal amount,

        @Schema(description = "Category the generated movements carry", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode,

        @Schema(description = "FIXED_DAY or NTH_BUSINESS_DAY", example = "NTH_BUSINESS_DAY")
        @JsonProperty("rule_type")
        DateRuleType ruleType,

        @Schema(description = "Day of the month or business day position", example = "5")
        @JsonProperty("rule_value")
        Integer ruleValue,

        @Schema(description = "Which weekdays are business days", example = "MONDAY_TO_FRIDAY")
        @JsonProperty("business_week")
        BusinessWeek businessWeek,

        @Schema(description = "What a FIXED_DAY rule does on a non business day; null for NTH_BUSINESS_DAY",
                example = "ANTICIPATE")
        @JsonProperty("non_business_day_policy")
        NonBusinessDayPolicy nonBusinessDayPolicy,

        @Schema(description = "First nominal month served", example = "2026-12", type = "string")
        @JsonProperty("start_month")
        YearMonth startMonth,

        @Schema(description = "Last nominal month served; null means no end", example = "2027-12", type = "string")
        @JsonProperty("end_month")
        YearMonth endMonth,

        @Schema(description = "True for the main income that will open the pay cycles", example = "true")
        boolean anchor,

        @Schema(description = "ACTIVE or INACTIVE", example = "ACTIVE")
        IncomeScheduleStatus status,

        @Schema(description = "Fixed bill this schedule took the receipt over from, if any",
                example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("replaces_fixed_bill_code")
        UUID replacesFixedBillCode
) {
}
