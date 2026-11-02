package com.bts.personalbudget.core.domain.service.income;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * The fields a PATCH on an income schedule may change. A null component means "keep what is
 * stored", so a category can be replaced but not removed. The end of the validity is the exception:
 * {@code removeEndMonth} is the explicit way back to "no end". The start month and the replaced fixed
 * bill are not editable: they decide which months the schedule and the old fixed bill serve.
 *
 * <p>The four rule components are merged with the stored rule, so only the ones that change need to
 * be sent; the merged rule has to be valid as a whole.
 */
public record IncomeScheduleUpdate(
        String description,
        BigDecimal amount,
        UUID categoryCode,
        DateRuleType ruleType,
        Integer ruleValue,
        BusinessWeek businessWeek,
        NonBusinessDayPolicy nonBusinessDayPolicy,
        YearMonth endMonth,
        boolean removeEndMonth,
        Boolean anchor
) {

    public boolean isEmpty() {
        return description == null
                && amount == null
                && categoryCode == null
                && ruleType == null
                && ruleValue == null
                && businessWeek == null
                && nonBusinessDayPolicy == null
                && endMonth == null
                && !removeEndMonth
                && anchor == null;
    }

    public boolean changesRule() {
        return ruleType != null || ruleValue != null || businessWeek != null || nonBusinessDayPolicy != null;
    }
}
