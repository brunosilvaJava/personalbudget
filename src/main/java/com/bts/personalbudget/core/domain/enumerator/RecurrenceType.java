package com.bts.personalbudget.core.domain.enumerator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum RecurrenceType {
    WEEKLY(1, 7),
    MONTHLY(1, 31),
    YEARLY(1,365);

    private final int initialDay;
    private final int endDay;

}
