package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class FixedBillFactory {

    private static final String BLANK_DESCRIPTION = "     ";
    private static final Set<Integer> EMPTY_DAYS = Set.of();
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

    public static final String OPERATION_TYPE = "operationType";
    public static final String DESCRIPTION = "description";
    public static final String AMOUNT = "amount";
    public static final String RECURRENCE_TYPE = "recurrenceType";
    public static final String DAYS = "days";
    public static final String REFERENCE_YEAR = "referenceYear";
    public static final String STATUS = "status";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String NEXT_DUE_DATE = "nextDueDate";

    public static final Map<String, Object> PARAMS = Map.of(
            OPERATION_TYPE, OperationType.DEBIT,
            DESCRIPTION, "test",
            AMOUNT, BigDecimal.valueOf(50),
            RECURRENCE_TYPE, RecurrenceType.YEARLY,
            DAYS, Set.of(1, 180),
            REFERENCE_YEAR, 2025,
            STATUS, FixedBillStatus.ACTIVE,
            START_DATE, LocalDate.now(),
            END_DATE, LocalDate.now().plusYears(1),
            NEXT_DUE_DATE, LocalDate.now().plusDays(1)
    );

    public static FixedBill buildModel(RecurrenceType recurrenceType, Set<Integer> days) {
        return new FixedBill(null, UUID.randomUUID(), (OperationType) PARAMS.get(OPERATION_TYPE),
                (String) PARAMS.get(DESCRIPTION), (BigDecimal) PARAMS.get(AMOUNT),
                recurrenceType, days,  (Integer) PARAMS.get(REFERENCE_YEAR),
                FixedBillStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusYears(1), null);
    }

    public static FixedBill buildModel(RecurrenceType recurrenceType, Set<Integer> days, Integer referenceYear) {
        return new FixedBill(null, UUID.randomUUID(), (OperationType) PARAMS.get(OPERATION_TYPE),
                (String) PARAMS.get(DESCRIPTION), (BigDecimal) PARAMS.get(AMOUNT),
                recurrenceType, days,  referenceYear,
                FixedBillStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusYears(1), null);
    }

    public static FixedBill buildModel(LocalDate date, OperationType operationType, String value) {
        return buildFixedBill(Map.of(
                FixedBillFactory.AMOUNT, new BigDecimal(value),
                FixedBillFactory.OPERATION_TYPE, operationType,
                FixedBillFactory.NEXT_DUE_DATE, date));
    }

    public static FixedBill buildModelWithEmptyMandatoryFields() {
        return new FixedBill(null, UUID.randomUUID(), (OperationType) PARAMS.get(OPERATION_TYPE),
                BLANK_DESCRIPTION, ZERO_AMOUNT,
                null, EMPTY_DAYS,  (Integer) PARAMS.get(REFERENCE_YEAR),
                FixedBillStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusYears(1), null);
    }

    public static FixedBill buildFixedBill(Map<String, Object> params) {
        return new FixedBill(
                null,
                UUID.randomUUID(),
                params.get(OPERATION_TYPE) == null ? (OperationType) PARAMS.get(OPERATION_TYPE) : (OperationType) params.get(OPERATION_TYPE),
                params.get(DESCRIPTION) == null ? (String) PARAMS.get(DESCRIPTION) : (String) params.get(DESCRIPTION),
                params.get(AMOUNT) == null ? (BigDecimal) PARAMS.get(AMOUNT) : (BigDecimal) params.get(AMOUNT),
                params.get(RECURRENCE_TYPE) == null ? (RecurrenceType) PARAMS.get(RECURRENCE_TYPE) : (RecurrenceType) params.get(RECURRENCE_TYPE),
                params.get(DAYS) == null ? (Set<Integer>) PARAMS.get(DAYS) : (Set<Integer>) params.get(DAYS),
                params.get(REFERENCE_YEAR) == null ? (Integer) PARAMS.get(REFERENCE_YEAR) : (Integer) params.get(REFERENCE_YEAR),
                params.get(STATUS) == null ? (FixedBillStatus) PARAMS.get(STATUS) : (FixedBillStatus) params.get(STATUS),
                params.get(START_DATE) == null ? (LocalDate) PARAMS.get(START_DATE) : (LocalDate) params.get(START_DATE),
                params.get(END_DATE) == null ? (LocalDate) PARAMS.get(END_DATE) : (LocalDate) params.get(END_DATE),
                params.get(NEXT_DUE_DATE) == null ? (LocalDate) PARAMS.get(NEXT_DUE_DATE) : (LocalDate) params.get(NEXT_DUE_DATE));
    }

    public static FixedBillEntity buildFixedBillEntity(Map<String, Object> params) {
        final FixedBillEntity fixedBill = new FixedBillEntity();
        fixedBill.setCode(UUID.randomUUID());
        fixedBill.setRecurrenceType(params.get(RECURRENCE_TYPE) == null ? (RecurrenceType) PARAMS.get(RECURRENCE_TYPE) : (RecurrenceType) params.get(RECURRENCE_TYPE));
        fixedBill.setOperationType(params.get(OPERATION_TYPE) == null ? (OperationType) PARAMS.get(OPERATION_TYPE) : (OperationType) params.get(OPERATION_TYPE));
        fixedBill.setDescription(params.get(DESCRIPTION) == null ? (String) PARAMS.get(DESCRIPTION) : (String) params.get(DESCRIPTION));
        fixedBill.setAmount(params.get(AMOUNT) == null ? (BigDecimal) PARAMS.get(AMOUNT) : (BigDecimal) params.get(AMOUNT));
        fixedBill.setStartDate(params.get(START_DATE) == null ? (LocalDate) PARAMS.get(START_DATE) : (LocalDate) params.get(START_DATE));
        fixedBill.setEndDate(params.get(END_DATE) == null ? (LocalDate) PARAMS.get(END_DATE) : (LocalDate) params.get(END_DATE));
        fixedBill.setStatus(params.get(STATUS) == null ? (FixedBillStatus) PARAMS.get(STATUS) : (FixedBillStatus) params.get(STATUS));
        fixedBill.setReferenceYear(params.get(REFERENCE_YEAR) == null ? (Integer) PARAMS.get(REFERENCE_YEAR) : (Integer) params.get(REFERENCE_YEAR));

        if(fixedBill.getOperationType() == OperationType.DEBIT) {
            fixedBill.setAmount(fixedBill.getAmount().negate());
        }

        final Set<Integer> days = (Set<Integer>) PARAMS.get(DAYS);

        final Set<CalendarFixedBillEntity> calendarFixedBillEntityList =
                days.stream().map(day ->
                        new CalendarFixedBillEntity(day, fixedBill)
                ).collect(Collectors.toSet());

        fixedBill.setCalendarFixedBillEntityList(calendarFixedBillEntityList);

        fixedBill.setCreatedDate(Instant.now());
        fixedBill.setFlagActive(Boolean.TRUE);

        return fixedBill;
    }
}