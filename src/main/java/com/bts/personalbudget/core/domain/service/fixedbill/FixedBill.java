package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
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
public class FixedBill {
    private Long id;
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
}
