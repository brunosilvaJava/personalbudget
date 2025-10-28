package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static java.math.BigDecimal.ZERO;

@Slf4j
@ToString
@Getter
@Setter
public class FixedBill implements BalanceCalcData {

    private Long id;
    private UUID code;
    private OperationType operationType;
    private String description;
    private BigDecimal amount;
    private RecurrenceType recurrenceType;
    private Set<Integer> days;
    private Integer referenceYear;
    private FixedBillStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextDueDate;

    public FixedBill(Long id, UUID code, OperationType operationType, String description, BigDecimal amount,
                     RecurrenceType recurrenceType, Set<Integer> days, Integer referenceYear, FixedBillStatus status,
                     LocalDate startDate, LocalDate endDate, LocalDate nextDueDate) {
        this.id = id;
        this.code = code;
        this.operationType = operationType;
        this.description = description;
        this.amount = amount;
        this.recurrenceType = recurrenceType;
        this.days = days;
        this.referenceYear = referenceYear;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextDueDate = nextDueDate;
        init(code);
    }

    private void init(final UUID code) {
        final boolean isNew = code == null;
        if (isNew) {
            this.code = UUID.randomUUID();
        }
    }

    boolean update(final OperationType operationType,
                   final String description,
                   final BigDecimal amount,
                   final RecurrenceType recurrenceType,
                   final Set<Integer> days,
                   final Integer referenceYear,
                   final FixedBillStatus status,
                   final LocalDate startDate,
                   final LocalDate endDate) {

        boolean hasChanges = false;

        if (operationType != null && this.operationType != operationType) {
            this.operationType = operationType;
            hasChanges = true;
        }

        if (description != null && !this.description.equals(description)) {
            this.description = description;
            hasChanges = true;
        }

        if (amount != null && (this.amount == null || this.amount.compareTo(amount) != 0)) {
            this.amount = amount;
            hasChanges = true;
        }

        if (recurrenceType != null && this.recurrenceType != recurrenceType) {
            this.recurrenceType = recurrenceType;
            hasChanges = true;
        }

        if (days != null && !this.days.equals(days)) {
            this.days = days;
            hasChanges = true;
        }

        if (referenceYear != null && (this.referenceYear == null || !this.referenceYear.equals(referenceYear))) {
            this.referenceYear = referenceYear;
            hasChanges = true;
        }

        if (status != null && this.status != status) {
            this.status = status;
            hasChanges = true;
        }

        if (startDate != null && (this.startDate == null || !this.startDate.equals(startDate))) {
            this.startDate = startDate;
            hasChanges = true;
        }

        if (endDate != null && (this.endDate == null || !this.endDate.equals(endDate))) {
            this.endDate = endDate;
            hasChanges = true;
        }

        return hasChanges;
    }

    public boolean isLeapYear() {
        return Year.isLeap(referenceYear);
    }

    public boolean isCurrent(final LocalDate baseDate) {
        if (status != FixedBillStatus.ACTIVE) {
            return false;
        }

        if (baseDate.isBefore(startDate)) {
            return false;
        }

        return endDate == null || !baseDate.isAfter(endDate);
    }

    public void validationDays() {
        log.info("m=validationDays fixedBillDays={}", days);
        final List<Integer> enabledDays = defineEnableDays(recurrenceType);
        for (Integer day : days) {
            boolean isValid = false;
            for (Integer enabledDay : enabledDays) {
                if (day.equals(enabledDay)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                log.error("m=validationDays, error=InvalidDays,  recurrenceType={}, days={}", recurrenceType, days);
                throw new RecurrenceTypeDayInvalidException(recurrenceType, days);
            }
        }
    }

    private List<Integer> defineEnableDays(final RecurrenceType recurrenceType) {
        List<Integer> numberList = new ArrayList<>();
        for (int x = recurrenceType.getInitialDay(); x <= recurrenceType.getEndDay(); x++) {
            numberList.add(x);
        }
        return numberList;
    }

    @Override
    public LocalDate findBalanceCalcDate() {
        return nextDueDate;
    }

    @Override
    public BigDecimal getBalanceCalcValue() {
        if (operationType == OperationType.DEBIT) {
            if (amount != null && amount.compareTo(ZERO) > 0) {
                return amount.negate();
            }
        }
        return amount;
    }

    @Override
    public PaymentStatus findStatus() {
        return PaymentStatus.PENDING;
    }

}
