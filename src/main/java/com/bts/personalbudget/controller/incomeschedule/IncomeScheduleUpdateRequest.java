package com.bts.personalbudget.controller.incomeschedule;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Request data for updating an income schedule. Fields left out are kept as they are. "
        + "Occurrences already generated keep their date and amount; the change applies to the ones not "
        + "generated yet.")
public record IncomeScheduleUpdateRequest(

        @Size(min = 3, max = 50)
        @Schema(description = "New description", example = "Salário CLT")
        String description,

        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "New expected amount, positive", example = "5200.00")
        BigDecimal amount,

        @Schema(description = "Category (CREDIT) to use from now on", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode,

        @Schema(description = "New rule type", example = "FIXED_DAY")
        @JsonProperty("rule_type")
        DateRuleType ruleType,

        @Schema(description = "New day of the month or business day position", example = "5")
        @JsonProperty("rule_value")
        Integer ruleValue,

        @Schema(description = "New business week", example = "MONDAY_TO_SATURDAY")
        @JsonProperty("business_week")
        BusinessWeek businessWeek,

        @Schema(description = "New policy for a FIXED_DAY rule on a non business day", example = "POSTPONE")
        @JsonProperty("non_business_day_policy")
        NonBusinessDayPolicy nonBusinessDayPolicy,

        @Schema(description = "New last nominal month served (yyyy-MM)", example = "2027-12", type = "string")
        @JsonProperty("end_month")
        YearMonth endMonth,

        @Schema(description = "True removes the end of the validity (end_month cannot be sent together)", example = "false")
        @JsonProperty("remove_end_month")
        Boolean removeEndMonth,

        @Schema(description = "Whether this is the main income that will open the pay cycles", example = "true")
        Boolean anchor
) {
}
