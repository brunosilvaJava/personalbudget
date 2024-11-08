package com.bts.personalbudget.util;

import java.time.LocalDate;

public abstract class DateUtil {
    public static boolean isLocalDateBetween(
            final LocalDate baseDate,
            final LocalDate initialDate,
            final LocalDate endDate) {
        return !(baseDate.isBefore(initialDate) || baseDate.isAfter(endDate));
    }
}
