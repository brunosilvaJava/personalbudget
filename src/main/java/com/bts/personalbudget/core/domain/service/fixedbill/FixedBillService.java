package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.model.FixedBill;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixedBillService {
    void save(FixedBill fixedBill);
    void updateNextDueDate(FixedBill fixedBill, LocalDate baseDate);
    Optional<LocalDate> defineNextDueDate(FixedBill fixedBill, LocalDate baseDate);
    List<FixedBill> findByNextDueDate(LocalDate nextDueDate);
    List<FixedBill> findAllByFlagActiveTrueAndNextDueDateBetween(LocalDate inicialDate, LocalDate endDate);

}
