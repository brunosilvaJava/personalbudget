package com.bts.personalbudget.controller.incomeschedule;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Request data for creating an income schedule: the main income and the rule that "
        + "says on which day of each month it is expected.")
public record IncomeScheduleRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        @Schema(description = "Description copied to the generated movements", example = "Salário")
        String description,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Expected amount of each occurrence, positive", example = "5000.00")
        BigDecimal amount,

        @Schema(description = "Category (CREDIT) the generated movements carry", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode,

        @NotNull
        @Schema(description = "FIXED_DAY (a day of the month) or NTH_BUSINESS_DAY (the Nth business day of the month)",
                example = "NTH_BUSINESS_DAY")
        @JsonProperty("rule_type")
        DateRuleType ruleType,

        @NotNull
        @Schema(description = "The day of the month (1 to 31) for FIXED_DAY, or the position of the business day "
                + "(1 to 15) for NTH_BUSINESS_DAY. A day the month does not have (30 in February) uses the last day",
                example = "5")
        @JsonProperty("rule_value")
        Integer ruleValue,

        @NotNull
        @Schema(description = "Which weekdays are business days; Saturday is a choice of each schedule",
                example = "MONDAY_TO_FRIDAY")
        @JsonProperty("business_week")
        BusinessWeek businessWeek,

        @Schema(description = "What a FIXED_DAY rule does when its day is not a business day. Required for "
                + "FIXED_DAY; ignored for NTH_BUSINESS_DAY", example = "ANTICIPATE")
        @JsonProperty("non_business_day_policy")
        NonBusinessDayPolicy nonBusinessDayPolicy,

        @NotNull
        @Schema(description = "First nominal month served (yyyy-MM). Cannot be before the current month: "
                + "past receipts are registered as movements. When it is the current month and its date already "
                + "passed, the next job run generates it as an overdue PENDING movement", example = "2026-12", type = "string")
        @JsonProperty("start_month")
        YearMonth startMonth,

        @Schema(description = "Last nominal month served (yyyy-MM); omit for no end", example = "2027-12", type = "string")
        @JsonProperty("end_month")
        YearMonth endMonth,

        @Schema(description = "True for the main income that will open the pay cycles. At most one active "
                + "schedule is the anchor. Defaults to false", example = "true")
        Boolean anchor,

        @Schema(description = "Fixed bill (CREDIT) this schedule takes the receipt over from. The fixed bill is "
                + "ended the day before start_month, so the two never generate the same receipt",
                example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("replaces_fixed_bill_code")
        UUID replacesFixedBillCode
) {
}
