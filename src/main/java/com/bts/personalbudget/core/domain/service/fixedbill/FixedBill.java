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
    private UUID categoryCode;

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
            if (this.startDate == null) {
                this.startDate = LocalDate.now();
            }
        }
    }

    boolean update(final FixedBillUpdate changes) {

        boolean hasChanges = false;

        if (changes.operationType() != null && this.operationType != changes.operationType()) {
            this.operationType = changes.operationType();
            hasChanges = true;
        }

        if (changes.description() != null && !this.description.equals(changes.description())) {
            this.description = changes.description();
            hasChanges = true;
        }

        if (changes.amount() != null && (this.amount == null || this.amount.compareTo(changes.amount()) != 0)) {
            this.amount = changes.amount();
            hasChanges = true;
        }

        if (changes.recurrenceType() != null && this.recurrenceType != changes.recurrenceType()) {
            this.recurrenceType = changes.recurrenceType();
            hasChanges = true;
        }

        if (changes.days() != null && !this.days.equals(changes.days())) {
            this.days = changes.days();
            hasChanges = true;
        }

        if (changes.referenceYear() != null
                && (this.referenceYear == null || !this.referenceYear.equals(changes.referenceYear()))) {
            this.referenceYear = changes.referenceYear();
            hasChanges = true;
        }

        if (changes.status() != null && this.status != changes.status()) {
            this.status = changes.status();
            hasChanges = true;
        }

        if (changes.startDate() != null
                && (this.startDate == null || !this.startDate.equals(changes.startDate()))) {
            this.startDate = changes.startDate();
            hasChanges = true;
        }

        if (changes.endDate() != null
                && (this.endDate == null || !this.endDate.equals(changes.endDate()))) {
            this.endDate = changes.endDate();
            hasChanges = true;
        }

        if (changes.categoryCode() != null && !changes.categoryCode().equals(this.categoryCode)) {
            this.categoryCode = changes.categoryCode();
            hasChanges = true;
        }

        return hasChanges;
    }

    public Schedule schedule() {
        return new Schedule(recurrenceType, days == null ? null : Set.copyOf(days), referenceYear,
                status, startDate, endDate);
    }

    public record Schedule(RecurrenceType recurrenceType,
                           Set<Integer> days,
                           Integer referenceYear,
                           FixedBillStatus status,
                           LocalDate startDate,
                           LocalDate endDate) {
    }

    public void conclude() {
        if (status == FixedBillStatus.INACTIVE) {
            log.info("m=conclude code={} msg=fixedBill_already_inactive", code);
            return;
        }
        log.info("m=conclude code={} msg=fixedBill_concluded", code);
        status = FixedBillStatus.INACTIVE;
        nextDueDate = null;
    }

    public boolean isLeapYear() {
        return Year.isLeap(referenceYear);
    }

    public boolean isCurrent(final LocalDate baseDate) {
        if (status != FixedBillStatus.ACTIVE) {
            return false;
        }

        if (startDate != null && baseDate.isBefore(startDate)) {
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
