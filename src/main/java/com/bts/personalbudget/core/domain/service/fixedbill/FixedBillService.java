package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.model.FixedBill;
import java.time.LocalDate;
import java.util.Optional;

public interface FixedBillService {
    void save(FixedBill fixedBill);
    Optional<LocalDate> defineNextDueDate(final FixedBill fixedBill, final LocalDate baseDate);
}
