package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.OperationType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BalanceCalc {
    LocalDate getBalanceCalcDate();
    BigDecimal getBalanceCalcValue();
    OperationType getOperationType();



}
