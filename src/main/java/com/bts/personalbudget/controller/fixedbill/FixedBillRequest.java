package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class FixedBillRequest {
    @JsonProperty("operation_type")
    private OperationType operationType;
    private String description;
    private BigDecimal amount;
    @JsonProperty("recurrence_type")
    private RecurrenceType recurrenceType;
    private List<Integer> days;
    @JsonProperty("flg_leap_year")
    private Boolean flgLeapYear;
    @JsonProperty("start_date")
    private LocalDate startDate;
    @JsonProperty("end_date")
    private LocalDate endDate;
}
