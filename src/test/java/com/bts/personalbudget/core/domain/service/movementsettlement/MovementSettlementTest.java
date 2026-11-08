package com.bts.personalbudget.core.domain.service.movementsettlement;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovementSettlementTest {

    @Test
    void shouldRepresentDebitSettlementAsNegativeCashOnItsEffectiveDate() {
        final MovementSettlement settlement = new MovementSettlement(
                null, null, new BigDecimal("25.50"), LocalDate.of(2026, 11, 8),
                null, null, OperationType.DEBIT, null, LocalDate.of(2026, 10, 1));

        assertThat(settlement.getBalanceCalcValue()).isEqualByComparingTo("-25.50");
        assertThat(settlement.findBalanceCalcDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(settlement.findStatus()).isEqualTo(BalanceCalcData.PaymentStatus.DONE);
    }

    @Test
    void shouldRepresentCreditSettlementAsPositiveCash() {
        final MovementSettlement settlement = new MovementSettlement(
                null, null, new BigDecimal("25.50"), LocalDate.of(2026, 11, 8),
                null, null, OperationType.CREDIT, null, LocalDate.of(2026, 10, 1));

        assertThat(settlement.getBalanceCalcValue()).isEqualByComparingTo("25.50");
    }
}
