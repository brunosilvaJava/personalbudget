package com.bts.personalbudget.core.domain.service.fixedbill.calc;

import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CalcFixedBill.YEARLY")
public class YearlyCalcFixedBillImpl implements CalcFixedBill {

    private static final int LAST_FIXED_YEAR_DAY = 59;

    @Override
    public LocalDate calcNextDueDate(final FixedBill fixedBill,
                                     final LocalDate baseDate) {
        final List<Integer> dueDays = fixedBill.getDays();
        final int dayOfYearBaseDate = baseDate.getDayOfYear();
        Year yearDueDate = Year.of(baseDate.getYear());

        int nextDueDay = 0;
        for (int dueDay : dueDays) {
            if (dueDay > LAST_FIXED_YEAR_DAY) {
                if (fixedBill.isLeapYear() && !baseDate.isLeapYear()) {
                    dueDay--;
                } else if (!fixedBill.isLeapYear() && baseDate.isLeapYear()) {
                    dueDay++;
                }
            }
            if (dueDay >= dayOfYearBaseDate) {
                nextDueDay = dueDay;
                break;
            }
        }
        if (nextDueDay == 0) {
            nextDueDay = dueDays.getFirst();
            yearDueDate = yearDueDate.plusYears(1);
            if (nextDueDay > LAST_FIXED_YEAR_DAY) {
                if (fixedBill.isLeapYear() && !yearDueDate.isLeap()) {
                    nextDueDay--;
                } else if (!fixedBill.isLeapYear() && yearDueDate.isLeap()) {
                    nextDueDay++;
                }
            }
        }
        return LocalDate.ofYearDay(yearDueDate.getValue(), nextDueDay);
    }
}
