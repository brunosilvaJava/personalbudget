package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("FixedBillService.WEEKLY")
public class WeeklyFixedBillServiceImpl extends FixedBillServiceImpl {

    private static final int WEEK_DAYS_NUMBER = 7;

    public WeeklyFixedBillServiceImpl(FixedBillRepository fixedBillRepository, FixedBillMapper fixedBillMapper) {
        super(fixedBillRepository, fixedBillMapper);
    }

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
