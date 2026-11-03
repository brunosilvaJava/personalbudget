package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.exception.InvalidFieldsException;
import org.junit.jupiter.api.Test;

import static com.bts.personalbudget.core.domain.enumerator.BusinessWeek.MONDAY_TO_FRIDAY;
import static com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.ANTICIPATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomeDateRuleTest {

    @Test
    void shouldRejectFixedDayOutsideOneToThirtyOne() {
        assertTrue(assertThrows(InvalidFieldsException.class,
                () -> IncomeDateRule.fixedDay(0, ANTICIPATE, MONDAY_TO_FRIDAY))
                .getValidations().containsKey("value"));
        assertTrue(assertThrows(InvalidFieldsException.class,
                () -> IncomeDateRule.fixedDay(32, ANTICIPATE, MONDAY_TO_FRIDAY))
                .getValidations().containsKey("value"));
    }

    @Test
    void shouldAcceptFixedDayBoundaries() {
        assertEquals(1, IncomeDateRule.fixedDay(1, ANTICIPATE, MONDAY_TO_FRIDAY).value());
        assertEquals(31, IncomeDateRule.fixedDay(31, ANTICIPATE, MONDAY_TO_FRIDAY).value());
    }

    @Test
    void shouldRejectBusinessDayPositionOutsideAllowedRange() {
        assertTrue(assertThrows(InvalidFieldsException.class,
                () -> IncomeDateRule.nthBusinessDay(0, MONDAY_TO_FRIDAY))
                .getValidations().containsKey("value"));
        assertTrue(assertThrows(InvalidFieldsException.class,
                () -> IncomeDateRule.nthBusinessDay(IncomeDateRule.MAX_BUSINESS_DAY_POSITION + 1, MONDAY_TO_FRIDAY))
                .getValidations().containsKey("value"));
    }

    @Test
    void shouldAcceptBusinessDayPositionBoundaries() {
        assertEquals(1, IncomeDateRule.nthBusinessDay(1, MONDAY_TO_FRIDAY).value());
        assertEquals(IncomeDateRule.MAX_BUSINESS_DAY_POSITION,
                IncomeDateRule.nthBusinessDay(IncomeDateRule.MAX_BUSINESS_DAY_POSITION, MONDAY_TO_FRIDAY).value());
    }

    @Test
    void shouldRequirePolicyWhenRuleIsFixedDay() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> new IncomeDateRule(DateRuleType.FIXED_DAY, 5, MONDAY_TO_FRIDAY, null));

        assertTrue(exception.getValidations().containsKey("nonBusinessDayPolicy"));
    }

    @Test
    void shouldDropPolicyWhenRuleIsNthBusinessDay() {
        final IncomeDateRule rule = new IncomeDateRule(DateRuleType.NTH_BUSINESS_DAY, 5, MONDAY_TO_FRIDAY, ANTICIPATE);

        assertNull(rule.nonBusinessDayPolicy());
    }

    @Test
    void shouldRequireRuleTypeAndBusinessWeek() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> new IncomeDateRule(null, 1, null, null));

        assertTrue(exception.getValidations().containsKey("ruleType"));
        assertTrue(exception.getValidations().containsKey("businessWeek"));
    }
}
