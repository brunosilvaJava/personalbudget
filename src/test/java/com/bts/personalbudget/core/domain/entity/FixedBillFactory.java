package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FixedBillFactory {

    private static final String DESCRIPTION = "test";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(50);
    private static final LocalDate START_DATE = LocalDate.now();
    private static final LocalDate END_DATE = START_DATE.plusYears(1);
    private static final boolean COMMONS_YEAR = false;
    private static final String BLANK_DESCRIPTION = "     ";
    private static final List<Integer> EMPTY_DAYS = List.of();
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

    public static FixedBill buildModel(RecurrenceType type, List<Integer> days) {
        return buildFixedBill(type, DESCRIPTION, AMOUNT, days, COMMONS_YEAR);
    }

    public static FixedBill buildModel(RecurrenceType type, List<Integer> days, Boolean flgLeapYear) {
        return buildFixedBill(type, DESCRIPTION, AMOUNT, days, flgLeapYear);
    }

    public static FixedBill buildModelWithEmptyMandatoryFields() {
        return buildFixedBill(RecurrenceType.WEEKLY, BLANK_DESCRIPTION, ZERO_AMOUNT, EMPTY_DAYS, COMMONS_YEAR);
    }

    private static FixedBill buildFixedBill(RecurrenceType type, String test, BigDecimal amount, List<Integer> days, Boolean flgLeapYear) {
        FixedBill fixedBill = new FixedBill();
        fixedBill.setRecurrenceType(type);
        fixedBill.setOperationType(OperationType.DEBIT);
        fixedBill.setDescription(test);
        fixedBill.setAmount(amount);
        fixedBill.setStartDate(START_DATE);
        fixedBill.setEndDate(END_DATE);
        fixedBill.setDays(days);
        fixedBill.setFlgLeapYear(flgLeapYear);
        return fixedBill;
    }

}
