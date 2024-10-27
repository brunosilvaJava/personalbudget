package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class FixedBillFactory {

    private static final boolean COMMONS_YEAR = false;
    private static final String BLANK_DESCRIPTION = "     ";
    private static final List<Integer> EMPTY_DAYS = List.of();
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

    public static final String OPERATION_TYPE = "operationType";
    public static final String DESCRIPTION = "description";
    public static final String AMOUNT = "amount";
    public static final String RECURRENCE_TYPE = "recurrenceType";
    public static final String DAYS = "days";
    public static final String FLG_LEAP_YEAR = "flgLeapYear";
    public static final String STATUS = "status";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";

    private static final Map<String, Object> PARAMS = Map.of(
            OPERATION_TYPE, OperationType.DEBIT,
            DESCRIPTION, "test",
            AMOUNT, BigDecimal.valueOf(50),
            RECURRENCE_TYPE, RecurrenceType.WEEKLY,
            DAYS, List.of(1),
            FLG_LEAP_YEAR, COMMONS_YEAR,
            STATUS, FixedBillStatus.ACTIVE,
            START_DATE, LocalDate.now().minusYears(10),
            END_DATE, LocalDate.now().plusYears(10)
    );

    public static FixedBill buildModelWithEmptyMandatoryFields() {
        return buildFixedBill(Map.of(
                RECURRENCE_TYPE, RecurrenceType.WEEKLY,
                DESCRIPTION, BLANK_DESCRIPTION,
                AMOUNT, ZERO_AMOUNT,
                DAYS, EMPTY_DAYS,
                FLG_LEAP_YEAR, false,
                STATUS, FixedBillStatus.ACTIVE
        ));
    }

    public static FixedBill buildModel(RecurrenceType type, List<Integer> days) {
        return buildFixedBill(Map.of(RECURRENCE_TYPE, type, DAYS, days));
    }

    public static FixedBill buildModel(RecurrenceType type, List<Integer> days, Boolean flgLeapYear) {
        return buildFixedBill(Map.of(RECURRENCE_TYPE, type, DAYS, days, FLG_LEAP_YEAR, flgLeapYear));
    }

    public static FixedBill buildFixedBill(Map<String, Object> params) {
        final FixedBill fixedBill = new FixedBill();
        fixedBill.setRecurrenceType(params.get(RECURRENCE_TYPE) == null ? (RecurrenceType) PARAMS.get(RECURRENCE_TYPE) : (RecurrenceType) params.get(RECURRENCE_TYPE));
        fixedBill.setOperationType(params.get(OPERATION_TYPE) == null ? (OperationType) PARAMS.get(OPERATION_TYPE) : (OperationType) params.get(OPERATION_TYPE));
        fixedBill.setDescription(params.get(DESCRIPTION) == null ? (String) PARAMS.get(DESCRIPTION) : (String) params.get(DESCRIPTION));
        fixedBill.setAmount(params.get(AMOUNT) == null ? (BigDecimal) PARAMS.get(AMOUNT) : (BigDecimal) params.get(AMOUNT));
        fixedBill.setStartDate(params.get(START_DATE) == null ? (LocalDate) PARAMS.get(START_DATE) : (LocalDate) params.get(START_DATE));
        fixedBill.setEndDate(params.get(END_DATE) == null ? (LocalDate) PARAMS.get(END_DATE) : (LocalDate) params.get(END_DATE));
        fixedBill.setDays(params.get(DAYS) == null ? (List) PARAMS.get(DAYS) : (List) params.get(DAYS));
        fixedBill.setFlgLeapYear(params.get(FLG_LEAP_YEAR) == null ? (Boolean) PARAMS.get(FLG_LEAP_YEAR) : (Boolean) params.get(FLG_LEAP_YEAR));
        fixedBill.setStatus(params.get(STATUS) == null ? (FixedBillStatus) PARAMS.get(STATUS) : (FixedBillStatus) params.get(STATUS));
        return fixedBill;
    }

}
