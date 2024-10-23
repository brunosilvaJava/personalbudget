package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("FixedBillService.MONTHLY")
public class MonthlyFixedBillServiceImpl extends FixedBillServiceImpl {

    public MonthlyFixedBillServiceImpl(FixedBillRepository fixedBillRepository, FixedBillMapper fixedBillMapper) {
        super(fixedBillRepository, fixedBillMapper);
    }

    @Override
    public LocalDate calcNextDueDate(final FixedBill fixedBill,
                                     final LocalDate baseDate) {
        LocalDate nextDueDate = LocalDate.of(baseDate.getYear(), baseDate.getMonth(), baseDate.getDayOfMonth());
        int nexDueDay = 0;
        for (Integer dueMonthlyDay : fixedBill.getDays()) {
            if (dueMonthlyDay >= baseDate.getDayOfMonth()) {
                nexDueDay = dueMonthlyDay;
                break;
            }
        }
        if (nexDueDay == 0) {
            nextDueDate = nextDueDate.plusMonths(1);
            nexDueDay = fixedBill.getDays().getFirst();
        }
        int monthLength = nextDueDate.getMonth().length(nextDueDate.isLeapYear());
        if (nexDueDay > monthLength) {
            nexDueDay = monthLength;
        }
        return nextDueDate.withDayOfMonth(nexDueDay);
    }
}
