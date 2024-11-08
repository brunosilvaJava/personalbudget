package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.AMOUNT_PAID;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.OPERATION_TYPE;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.FinancialMovementProperty.STATUS;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.buildModel;
import static com.bts.personalbudget.core.domain.factory.FinancialMovementFactory.data;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BalanceServiceTest {

    @InjectMocks
    private BalanceService balanceService;

    @Mock
    private FinancialMovementService financialMovementService;

    @Mock
    private InstallmentBillService installmentBillService;

    @Mock
    private FixedBillService fixedBillService;

    @Test
    void mustCreateBalanceListOfFinancialMovement() {

        LocalDate initialDate = LocalDate.now().minusDays(2);
        LocalDate endDate = LocalDate.now().plusDays(2);

        when(financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate))
                .thenReturn(List.of(
                        mockFinancialMovement(CREDIT, "100.00", PAID_OUT),
                        mockFinancialMovement(CREDIT, "100.00", PAID_OUT),
                        mockFinancialMovement(DEBIT, "50.00", LATE)));

        when(fixedBillService.findAllByFlagActiveTrueAndNextDueDateBetween(initialDate, endDate))
                .thenReturn(List.of(
                        FixedBillFactory.buildFixedBill(Map.of(
                                FixedBillFactory.AMOUNT, BigDecimal.valueOf(100.00),
                                FixedBillFactory.OPERATION_TYPE, OperationType.DEBIT,
                                FixedBillFactory.NEXT_DUE_DATE, LocalDate.now().plusDays(1)
                        ))
                ));

        when(installmentBillService.findAllByNextInstallmentDateBetween(initialDate, endDate))
                .thenReturn(List.of(
                ));

        Set<DailyBalance> dailyBalanceList = balanceService.findDailyBalanceBetween(initialDate, endDate);

        assertFalse(dailyBalanceList.isEmpty());
        assertEquals(2, dailyBalanceList.size());

        Optional<DailyBalance> dailyBalanceNowOptional = dailyBalanceList.stream()
                .filter(db -> db.equals(new DailyBalance(LocalDate.now().atStartOfDay().toLocalDate())))
                .findAny();

        Optional<DailyBalance> dailyBalanceTomorrowOptional = dailyBalanceList.stream()
                .filter(db -> db.equals(new DailyBalance(LocalDate.now().plusDays(1).atStartOfDay().toLocalDate())))
                .findAny();

        assertTrue(dailyBalanceNowOptional.isPresent());
        assertEquals(BigDecimal.valueOf(20000, 2), dailyBalanceNowOptional.get().getTotalRevenue());
        assertEquals(BigDecimal.valueOf(5000, 2), dailyBalanceNowOptional.get().getTotalExpense());
        assertEquals(BigDecimal.valueOf(15000, 2), dailyBalanceNowOptional.get().calcFinalBalance());

        assertTrue(dailyBalanceTomorrowOptional.isPresent());
        assertEquals(BigDecimal.valueOf(15000), dailyBalanceTomorrowOptional.get().getTotalRevenue());
        assertEquals(BigDecimal.valueOf(1000, 1), dailyBalanceTomorrowOptional.get().getTotalExpense());
        assertEquals(BigDecimal.valueOf(-1000, 1), dailyBalanceTomorrowOptional.get().calcFinalBalance());

    }

    private static FinancialMovement mockFinancialMovement(OperationType credit, String v2, FinancialMovementStatus paidOut) {
        return buildModel(data(Map.of(
                OPERATION_TYPE, credit.name(),
                AMOUNT_PAID, v2,
                STATUS, paidOut.name())));
    }

}
