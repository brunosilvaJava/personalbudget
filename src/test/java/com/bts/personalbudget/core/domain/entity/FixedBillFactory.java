package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FixedBillFactory {

    public static FixedBill buildModel(RecurrenceType type, List<Integer> days) {
        return buildFixedBill(type, "test", BigDecimal.valueOf(50), days);
    }

    public static FixedBill buildModelWithEmptyMandatoryFields() {
        return buildFixedBill(RecurrenceType.WEEKLY, "     ", BigDecimal.ZERO, List.of());
    }

        private static FixedBill buildFixedBill(RecurrenceType type, String test, BigDecimal amount, List<Integer> days) {
        FixedBill fixedBill = new FixedBill();
        fixedBill.setRecurrenceType(type);
        fixedBill.setOperationType(OperationType.DEBIT);
        fixedBill.setDescription(test);
        fixedBill.setAmount(amount);
        fixedBill.setStartDate(LocalDate.now());
        fixedBill.setEndDate(LocalDate.now().plusYears(1));
        fixedBill.setDays(days);
        fixedBill.setFlgLeapYear(false);
        return fixedBill;
    }

}
