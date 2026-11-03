package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface BalanceCalcData {
    LocalDate findBalanceCalcDate();
    BigDecimal getBalanceCalcValue();
    OperationType getOperationType();
    PaymentStatus findStatus();
    UUID getCategoryCode();

    enum PaymentStatus {
        PENDING,
        DONE
    }
}
