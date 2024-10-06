package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FixedBill {
    private OperationType operationType;
    private String description;
    private BigDecimal amount;
    private RecurrenceType recurrenceType;
    private List<Integer> days;
    private Boolean flgLeapYear;
    private LocalDate startDate;
    private LocalDate endDate;
}