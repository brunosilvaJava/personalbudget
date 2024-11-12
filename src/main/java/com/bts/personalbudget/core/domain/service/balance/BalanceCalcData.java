package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface BalanceCalcData {
    LocalDate findBalanceCalcDate();
    BigDecimal getBalanceCalcValue();
    OperationType getOperationType();
}
