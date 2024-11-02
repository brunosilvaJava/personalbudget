package com.bts.personalbudget.core.domain.service.fixedbill.calc;

import com.bts.personalbudget.core.domain.model.FixedBill;
import java.time.LocalDate;

public interface CalcFixedBill {
    LocalDate calcNextDueDate(FixedBill fixedBill, LocalDate baseDate);
}
