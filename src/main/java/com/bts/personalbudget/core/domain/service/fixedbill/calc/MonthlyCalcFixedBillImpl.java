package com.bts.personalbudget.core.domain.service.fixedbill.calc;

import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CalcFixedBill.MONTHLY")
public class MonthlyCalcFixedBillImpl implements CalcFixedBill {

    @Override
    public LocalDate calcNextDueDate(final FixedBill fixedBill,
                                     final LocalDate baseDate) {
        if (fixedBill.getDays() == null || fixedBill.getDays().isEmpty()) {
            log.error("m=calcNextDueDate, code={}, error=NullOrEmptyDaysList, recurrenceType=MONTHLY", fixedBill.getCode());
            throw new IllegalArgumentException("Monthly fixed bill must have at least one day configured. Code: " + fixedBill.getCode());
        }
        final List<Integer> sortedDays = fixedBill.getDays().stream().sorted().toList();
        LocalDate nextDueDate = LocalDate.of(baseDate.getYear(), baseDate.getMonth(), baseDate.getDayOfMonth());
        int nexDueDay = 0;
        for (Integer dueMonthlyDay : sortedDays) {
            if (dueMonthlyDay >= baseDate.getDayOfMonth()) {
                nexDueDay = dueMonthlyDay;
                break;
            }
        }
        if (nexDueDay == 0) {
            nextDueDate = nextDueDate.plusMonths(1);
            nexDueDay = sortedDays.stream().findFirst().orElseThrow();
        }
        int monthLength = nextDueDate.getMonth().length(nextDueDate.isLeapYear());
        if (nexDueDay > monthLength) {
            nexDueDay = monthLength;
        }
        return nextDueDate.withDayOfMonth(nexDueDay);
    }
}
