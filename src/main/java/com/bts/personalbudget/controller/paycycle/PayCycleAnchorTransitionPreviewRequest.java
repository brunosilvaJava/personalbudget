package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "A proposed change to the current anchor rule or anchor schedule.")
public record PayCycleAnchorTransitionPreviewRequest(

        @Schema(description = "Existing active schedule to become the anchor. Omit to keep the current anchor.",
                example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("anchor_schedule_code")
        UUID anchorScheduleCode,

        @Schema(description = "New date rule, used only when keeping the current anchor", example = "FIXED_DAY")
        @JsonProperty("rule_type")
        DateRuleType ruleType,

        @Schema(description = "New day of the month or business-day position", example = "10")
        @JsonProperty("rule_value")
        Integer ruleValue,

        @Schema(description = "New business week", example = "MONDAY_TO_FRIDAY")
        @JsonProperty("business_week")
        BusinessWeek businessWeek,

        @Schema(description = "New fixed-day policy", example = "POSTPONE")
        @JsonProperty("non_business_day_policy")
        NonBusinessDayPolicy nonBusinessDayPolicy,

        @Schema(description = "New final month of validity; cannot be combined with remove_end_month",
                example = "2027-12", type = "string")
        @JsonProperty("end_month")
        YearMonth endMonth,

        @Schema(description = "Remove the final month of validity", example = "false")
        @JsonProperty("remove_end_month")
        Boolean removeEndMonth,

        @Schema(description = "Required when switching schedules and the first new date is after the frozen boundary",
                example = "SHORT")
        @JsonProperty("transition_shape")
        PayCycleTransitionShape transitionShape
) {
}
