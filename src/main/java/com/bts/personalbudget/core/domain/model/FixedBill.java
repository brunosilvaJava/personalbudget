package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

}
