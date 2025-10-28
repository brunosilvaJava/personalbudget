package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import java.util.List;
import java.util.Set;

public class RecurrenceTypeDayInvalidException extends RuntimeException {

    public RecurrenceTypeDayInvalidException(RecurrenceType recurrenceType, Set<Integer> days){

        super("Invalid days for recurrence type - recurrenceType=" + recurrenceType + ", days=" + days);
    }
}
