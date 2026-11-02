package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.exception.InvalidFieldsException;

import java.util.HashMap;
import java.util.Map;

/**
 * How a schedule picks its date inside a month.
 *
 * @param ruleType           FIXED_DAY or NTH_BUSINESS_DAY
 * @param value              the day of the month (1 to 31) for FIXED_DAY; the position (1 to
 *                           {@value #MAX_BUSINESS_DAY_POSITION}) for NTH_BUSINESS_DAY
 * @param businessWeek       which weekdays are business days (Saturday is a per-schedule choice)
 * @param nonBusinessDayPolicy what FIXED_DAY does when its day is not a business day; required for
 *                           FIXED_DAY and dropped (null) for NTH_BUSINESS_DAY, which always lands
 *                           on a business day
 */
public record IncomeDateRule(
        DateRuleType ruleType,
        int value,
        BusinessWeek businessWeek,
        NonBusinessDayPolicy nonBusinessDayPolicy
) {

    /**
     * Low enough that every month has that many business days in practice, even with the holidays
     * of a month like February.
     */
    public static final int MAX_BUSINESS_DAY_POSITION = 15;

    public IncomeDateRule {
        final Map<String, String> errors = new HashMap<>();
        if (ruleType == null) {
            errors.put("ruleType", "deve ser informado");
        }
        if (businessWeek == null) {
            errors.put("businessWeek", "deve ser informada");
        }
        if (ruleType == DateRuleType.FIXED_DAY) {
            if (value < 1 || value > 31) {
                errors.put("value", "o dia do mês deve estar entre 1 e 31");
            }
            if (nonBusinessDayPolicy == null) {
                errors.put("nonBusinessDayPolicy", "deve ser informada para uma regra de dia fixo");
            }
        } else if (ruleType == DateRuleType.NTH_BUSINESS_DAY) {
            if (value < 1 || value > MAX_BUSINESS_DAY_POSITION) {
                errors.put("value", "a posição do dia útil deve estar entre 1 e " + MAX_BUSINESS_DAY_POSITION);
            }
            nonBusinessDayPolicy = null;
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException("Campos inválidos", IncomeDateRule.class, errors);
        }
    }

    public static IncomeDateRule fixedDay(final int dayOfMonth,
                                          final NonBusinessDayPolicy nonBusinessDayPolicy,
                                          final BusinessWeek businessWeek) {
        return new IncomeDateRule(DateRuleType.FIXED_DAY, dayOfMonth, businessWeek, nonBusinessDayPolicy);
    }

    public static IncomeDateRule nthBusinessDay(final int position, final BusinessWeek businessWeek) {
        return new IncomeDateRule(DateRuleType.NTH_BUSINESS_DAY, position, businessWeek, null);
    }
}
