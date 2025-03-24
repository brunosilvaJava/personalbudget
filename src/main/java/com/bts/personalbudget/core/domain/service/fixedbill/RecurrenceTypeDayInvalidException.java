package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import java.util.List;

public class RecurrenceTypeDayInvalidException extends RuntimeException {

    public RecurrenceTypeDayInvalidException(RecurrenceType recurrenceType, List<Integer> days){

        super("Invalid days for recurrence type - recurrenceType=" + recurrenceType + ", days=" + days);
    }
}
