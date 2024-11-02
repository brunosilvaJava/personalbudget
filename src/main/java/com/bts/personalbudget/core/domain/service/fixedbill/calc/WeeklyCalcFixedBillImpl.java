package com.bts.personalbudget.core.domain.service.fixedbill.calc;

import com.bts.personalbudget.core.domain.model.FixedBill;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CalcFixedBill.WEEKLY")
public class WeeklyCalcFixedBillImpl implements CalcFixedBill {

    private static final int WEEK_DAYS_NUMBER = 7;

    @Override
    public LocalDate calcNextDueDate(final FixedBill fixedBill,
                                     final LocalDate baseDate) {
        LocalDate nextDueDate = LocalDate.of(baseDate.getYear(), baseDate.getMonth(), baseDate.getDayOfMonth());
        final List<Integer> dueDayList = fixedBill.getDays();
        final int baseDay = nextDueDate.getDayOfWeek().getValue() + 1;
        int auxDay = 0;
        for (Integer dueDay : dueDayList) {
            if (dueDay >= baseDay) {
                auxDay = dueDay;
                break;
            }
        }
        int nextDay;
        if (auxDay > 0) {
            nextDay = auxDay;
        } else {
            nextDay = dueDayList.getFirst();
        }
        if (nextDay == baseDay) {
            return nextDueDate;
        } else if (nextDay > baseDay) {
            nextDueDate = baseDate.plusDays(nextDay - baseDay);
        } else {
            nextDueDate = baseDate.plusDays(WEEK_DAYS_NUMBER - (baseDay - nextDay));
        }
        return nextDueDate;
    }
}
