package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.math.BigDecimal.ZERO;

@ToString
@Getter
@Setter
public class FixedBill implements BalanceCalcData {

    private UUID code;
    private OperationType operationType;
    private String description;
    private BigDecimal amount;
    private RecurrenceType recurrenceType;
    private List<Integer> days;
    private Boolean flgLeapYear;
    private FixedBillStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextDueDate;

    public FixedBill(UUID code, OperationType operationType, String description, BigDecimal amount, RecurrenceType recurrenceType, List<Integer> days, Boolean flgLeapYear, FixedBillStatus status, LocalDate startDate, LocalDate endDate, LocalDate nextDueDate) {
        this.code = code;
        this.operationType = operationType;
        this.description = description;
        this.amount = amount;
        this.recurrenceType = recurrenceType;
        this.days = days;
        this.flgLeapYear = flgLeapYear;
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
        if (operationType == OperationType.DEBIT) {
            if (amount != null && amount.compareTo(ZERO) > 0) {
                amount = amount.negate();
            }
        }
    }

    public boolean isLeapYear() {
        return flgLeapYear != null && flgLeapYear;
    }

    public boolean isCurrent(final LocalDate baseDate) {
        return status == FixedBillStatus.ACTIVE && !(baseDate.isBefore(startDate) || baseDate.isAfter(endDate));
    }

    @Override
    public LocalDate findBalanceCalcDate() {
        return nextDueDate;
    }

    @Override
    public BigDecimal getBalanceCalcValue() {
        return amount;
    }

    @Override
    public PaymentStatus findStatus() {
        return PaymentStatus.PENDING;
    }

}
