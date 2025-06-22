package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
//        if (operationType == OperationType.DEBIT) {
//            if (amount != null && amount.compareTo(ZERO) > 0) {
//                amount = amount.negate();
//            }
//        }
    }

    void update(final OperationType operationType,
                final String description,
                final BigDecimal amount,
                final RecurrenceType recurrenceType,
                final List<Integer> days,
                final Boolean flgLeapYear,
                final FixedBillStatus status,
                final LocalDate startDate,
                final LocalDate endDate) {
        this.operationType = operationType != null ? operationType : this.operationType;
        this.description = description != null ? description : this.description;
        this.amount = amount != null ? amount : this.amount;
        this.recurrenceType = recurrenceType != null ? recurrenceType : this.recurrenceType;
        this.days = days != null ? days : this.days;
        this.flgLeapYear = flgLeapYear != null ? flgLeapYear : this.flgLeapYear;
        this.status = status != null ? status : this.status;
        this.startDate = startDate != null ? startDate : this.startDate;
        this.endDate = endDate != null ? endDate : this.endDate;
    }

    public boolean isLeapYear() {
        return flgLeapYear != null && flgLeapYear;
    }

    public boolean isCurrent(final LocalDate baseDate) {
        return status == FixedBillStatus.ACTIVE && !(baseDate.isBefore(startDate) || baseDate.isAfter(endDate));
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
                log.error("m=validationDays, error=InvalidDays,  recurrenceType={}, days={}",  recurrenceType, days);
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
