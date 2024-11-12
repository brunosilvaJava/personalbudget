package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.factory.DailyBalanceFactory;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillFactory;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void mustCreateBalanceListOfFinancialMovementWhenCurrentDateBetweenInitialDateAndEndDate() {

        LocalDate currentDate = LocalDate.now();

        LocalDate initialDate = currentDate.minusDays(2);
        LocalDate secondDate = currentDate.minusDays(1);
        LocalDate thirdDate = currentDate;
        LocalDate fourthDate = currentDate.plusDays(1);
        LocalDate endDate = currentDate.plusDays(2);


        BigDecimal initialBalance = BigDecimal.valueOf(1);

        Set<DailyBalance> dailyBalances = mustCreateBalanceListOfFinancialMovement(
                initialBalance,
                initialDate, endDate,
                List.of(FinancialMovementFactory.buildModel(secondDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "50.00", LATE),
                        FinancialMovementFactory.buildModel(thirdDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(fourthDate, DEBIT, "10.00", PENDING)),
                List.of(FixedBillFactory.buildModel(fourthDate, DEBIT, "100.00")),
                List.of(InstallmentBillFactory.buildModel(fourthDate, CREDIT, "100.00")),

                List.of(DailyBalanceFactory.buildDailyBalance(initialDate, "1", "0", "0"),
                        DailyBalanceFactory.buildDailyBalance(secondDate, "1", "100", "0"),
                        DailyBalanceFactory.buildDailyBalance(thirdDate, "101", "100", "50"),
                        DailyBalanceFactory.buildDailyBalance(fourthDate, "151", "100", "110"),
                        DailyBalanceFactory.buildDailyBalance(endDate, "141", "0", "0")));

        assertFinalBalance(initialDate, "1", dailyBalances);
        assertFinalBalance(secondDate, "101", dailyBalances);
        assertFinalBalance(thirdDate, "151", dailyBalances);
        assertFinalBalance(fourthDate, "141", dailyBalances);
        assertFinalBalance(endDate, "141", dailyBalances);
    }

    @Test
    void mustCreateBalanceListOfFinancialMovementWhenCurrentDateAfterEndDate() {

        LocalDate currentDate = LocalDate.now();

        LocalDate initialDate = currentDate.minusDays(4);
        LocalDate secondDate = initialDate.plusDays(1);
        LocalDate thirdDate = secondDate.plusDays(1);
        LocalDate endDate = thirdDate.plusDays(1);

        BigDecimal initialBalance = BigDecimal.valueOf(0);

        Set<DailyBalance> dailyBalances = mustCreateBalanceListOfFinancialMovement(
                initialBalance,
                initialDate, endDate,
                List.of(FinancialMovementFactory.buildModel(initialDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "50.00", LATE),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "10.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(endDate, CREDIT, "100.00", PAID_OUT)),
                List.of(FixedBillFactory.buildModel(thirdDate, DEBIT, "100.00")),
                List.of(InstallmentBillFactory.buildModel(thirdDate, CREDIT, "100.00")),

                List.of(DailyBalanceFactory.buildDailyBalance(initialDate, "0", "100", "0"),
                        DailyBalanceFactory.buildDailyBalance(secondDate, "100", "0", "10"),
                        DailyBalanceFactory.buildDailyBalance(thirdDate, "90", "100", "100"),
                        DailyBalanceFactory.buildDailyBalance(endDate, "90", "100", "50")));

        assertFinalBalance(initialDate, "100", dailyBalances);
        assertFinalBalance(secondDate, "90", dailyBalances);
        assertFinalBalance(thirdDate, "90", dailyBalances);
        assertFinalBalance(endDate, "140", dailyBalances);
    }

    @Test
    void mustCreateBalanceListOfFinancialMovementWhenCurrentDateBeforeInitialDate() {

        LocalDate currentDate = LocalDate.now();

        LocalDate initialDate = currentDate.plusDays(1); // 12
        LocalDate secondDate = initialDate.plusDays(1);  // 13
        LocalDate thirdDate = secondDate.plusDays(1); // 14
        LocalDate endDate = thirdDate.plusDays(1); // 15

        BigDecimal initialBalance = BigDecimal.valueOf(-100);

        Set<DailyBalance> dailyBalances = mustCreateBalanceListOfFinancialMovement(
                initialBalance,
                initialDate, endDate,

                // When
                List.of(FinancialMovementFactory.buildModel(initialDate, CREDIT, "100.00", PENDING),
                        FinancialMovementFactory.buildModel(initialDate, DEBIT, "50.00", PENDING),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "10.00", PENDING),
                        FinancialMovementFactory.buildModel(endDate, CREDIT, "100.00", PENDING)),
                List.of(FixedBillFactory.buildModel(thirdDate, DEBIT, "100.00")),
                List.of(InstallmentBillFactory.buildModel(thirdDate, CREDIT, "100.00")),

                // Assertions
                List.of(DailyBalanceFactory.buildDailyBalance(initialDate, "-100", "100", "50"),
                        DailyBalanceFactory.buildDailyBalance(secondDate, "-50", "0", "10"),
                        DailyBalanceFactory.buildDailyBalance(thirdDate, "-60", "100", "100"),
                        DailyBalanceFactory.buildDailyBalance(endDate, "-60", "100", "0")));

        assertFinalBalance(initialDate, "-50", dailyBalances);
        assertFinalBalance(secondDate, "-60", dailyBalances);
        assertFinalBalance(thirdDate, "-60", dailyBalances);
        assertFinalBalance(endDate, "40", dailyBalances);
    }

    private Set<DailyBalance> mustCreateBalanceListOfFinancialMovement(BigDecimal initialBalance,
                                                                       LocalDate initialDate,
                                                                       LocalDate endDate,
                                                                       List<FinancialMovement> mockFinancialMovementList,
                                                                       List<FixedBill> mockFixedBillList,
                                                                       List<InstallmentBill> mockInstallmentBillList,
                                                                       List<DailyBalance> expectedDailyBalanceList) {

        when(financialMovementService.findBalance(initialDate.minusDays(1))).thenReturn(initialBalance);
        when(financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate)).thenReturn(mockFinancialMovementList);
        when(fixedBillService.findAllByNextDueDateBetween(initialDate, endDate)).thenReturn(mockFixedBillList);
        when(installmentBillService.findAllByNextInstallmentDateBetween(initialDate, endDate)).thenReturn(mockInstallmentBillList);

        Set<DailyBalance> resultDailyBalanceList = balanceService.findDailyBalanceBetween(initialDate, endDate);

        assertEquals(expectedDailyBalanceList.size(), resultDailyBalanceList.size(),
                String.format("expectedDailyBalanceList - expected size: %s, actual size: %s",
                        expectedDailyBalanceList.size(), resultDailyBalanceList.size()));

        expectedDailyBalanceList.forEach(expectedDailyBalance ->
                assertsDailyBalance(expectedDailyBalance, resultDailyBalanceList));

        return resultDailyBalanceList;
    }

    private void assertsDailyBalance(
            DailyBalance expectedDailyBalance,
            Set<DailyBalance> dailyBalanceListResult
    ) {
        LocalDate date = expectedDailyBalance.getDate();
        Optional<DailyBalance> dailyBalanceOptional = findDailyBalance(dailyBalanceListResult, date);
        assertTrue(dailyBalanceOptional.isPresent(), String.format("date: %s - daily balance not present", date));
        assertValue(date, "previousBalance", expectedDailyBalance.getPreviousBalance(), dailyBalanceOptional.get().getPreviousBalance());
        assertValue(date, "totalRevenue", expectedDailyBalance.getTotalRevenue(), dailyBalanceOptional.get().getTotalRevenue());
        assertValue(date, "totalExpense", expectedDailyBalance.getTotalExpense(), dailyBalanceOptional.get().getTotalExpense());
        assertValue(date, "finalBalance", expectedDailyBalance.getFinalBalance(), dailyBalanceOptional.get().getFinalBalance());
    }

    private void assertValue(LocalDate date, String valueDesc, BigDecimal expectedValue, BigDecimal actualValue) {
        assertThat(actualValue)
                .as("Date: %s, %s - actual: %s, expected: %s", date, valueDesc, actualValue, expectedValue)
                .isEqualByComparingTo(expectedValue);
    }

    private void assertFinalBalance(LocalDate date, String expectedFinalBalance, Set<DailyBalance> dailyBalances) {
        DailyBalance initialDailyBalance = dailyBalances.stream()
                .filter(db -> db.getDate().equals(date))
                .findFirst()
                .orElseThrow();
        BigDecimal expectedFinalBalanceValue = new BigDecimal(expectedFinalBalance);
        assertThat(initialDailyBalance.getFinalBalance())
                .as("Date: %s, %s - actual: %s, expected: %s", initialDailyBalance.getDate(),
                        "finalBalance", initialDailyBalance.getFinalBalance(), expectedFinalBalanceValue)
                .isEqualByComparingTo(expectedFinalBalanceValue);
    }

    private Optional<DailyBalance> findDailyBalance(Set<DailyBalance> dailyBalanceList, LocalDate today) {
        return dailyBalanceList.stream()
                .filter(db -> db.getDate().equals(today))
                .findAny();
    }

}
