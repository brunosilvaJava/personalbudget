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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Balanço diário com datas entre data vigente")
    void mustCreateBalanceListOfFinancialMovementWhenCurrentDateBetweenInitialDateAndEndDate() {

        LocalDate currentDate = LocalDate.now();

        LocalDate initialDate = currentDate.minusDays(2);
        LocalDate secondDate = currentDate.minusDays(1);
        LocalDate fourthDate = currentDate.plusDays(1);
        LocalDate endDate = currentDate.plusDays(2);

        BigDecimal initialBalance = BigDecimal.valueOf(1);
        BigDecimal projectedOpeningBalance = BigDecimal.valueOf(1);

        mustValidateFinalBalanceList(
                initialBalance,
                projectedOpeningBalance,
                initialDate,
                endDate,
                // Movimentações financeiras
                List.of(FinancialMovementFactory.buildModel(secondDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "50.00", LATE),
                        FinancialMovementFactory.buildModel(currentDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(fourthDate, DEBIT, "10.00", PENDING),
                        FinancialMovementFactory.buildModel(endDate, CREDIT, "100.00", PENDING)),
                // Contas fixas
                List.of(FixedBillFactory.buildModel(fourthDate, DEBIT, "100.00")),
                // Contas parceladas
                List.of(InstallmentBillFactory.buildModel(fourthDate, CREDIT, "100.00")),
                // Balanços diários esperados
                List.of(DailyBalanceFactory.buildDailyBalance(initialDate,
                                "1","0","0","1",
                                "1","0","0","1"),
                        DailyBalanceFactory.buildDailyBalance(secondDate,
                                "1","100","0","101",
                                "1","0","-50","51"),
                        DailyBalanceFactory.buildDailyBalance(currentDate,
                                "101","100","0","201",
                                "51","0","0","151"),
                        DailyBalanceFactory.buildDailyBalance(fourthDate,
                                "201","0", "0","201",
                                "151","100","-110","141"),
                        DailyBalanceFactory.buildDailyBalance(endDate,
                                "201","0", "0","201",
                                "141","100","0","241")));
    }

    @Test
    @DisplayName("Balanço diário com datas no passado")
    void mustCreateBalanceListOfFinancialMovementWhenCurrentDateAfterEndDate() {

        LocalDate currentDate = LocalDate.now();

        LocalDate initialDate = currentDate.minusDays(4);
        LocalDate secondDate = initialDate.plusDays(1);
        LocalDate thirdDate = secondDate.plusDays(1);
        LocalDate yesterday = thirdDate.plusDays(1);

        BigDecimal initialBalance = BigDecimal.ZERO;
        BigDecimal projectedOpeningBalance = BigDecimal.ZERO;

        mustValidateFinalBalanceList(
                initialBalance,
                projectedOpeningBalance,
                initialDate,
                yesterday,
                // Movimentações financeiras
                List.of(FinancialMovementFactory.buildModel(initialDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "50.00", LATE),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "10.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(thirdDate, CREDIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(thirdDate, DEBIT, "100.00", PAID_OUT),
                        FinancialMovementFactory.buildModel(yesterday, CREDIT, "100.00", PAID_OUT)
                ),
                // Contas fixas
                List.of(),
                // Contas parceladas
                List.of(),
                // Balanços diários esperados
                List.of(DailyBalanceFactory.buildDailyBalance(initialDate,
                                "0","100","0","100",
                                "0","0","0","100"),
                        DailyBalanceFactory.buildDailyBalance(secondDate,
                                "100","0","-10","90",
                                "100","0","-50","40"),
                        DailyBalanceFactory.buildDailyBalance(thirdDate,
                                "90","100","-100","90",
                                "40","0","0","40"),
                        DailyBalanceFactory.buildDailyBalance(yesterday,
                                "90","100", "0","190",
                                "40","0","0","140")));
    }

    @Test
    @DisplayName("Balanço diário com datas no futuro")
    void mustCreateBalanceListOfFinancialMovementWhenCurrentDateBeforeInitialDate() {

        LocalDate currentDate = LocalDate.now();

        LocalDate tomorrow = currentDate.plusDays(1);
        LocalDate secondDate = tomorrow.plusDays(1);
        LocalDate thirdDate = secondDate.plusDays(1);
        LocalDate endDate = thirdDate.plusDays(1);

        BigDecimal initialBalance = BigDecimal.valueOf(-100);
        BigDecimal projectedOpeningBalance = BigDecimal.valueOf(-100);

        mustValidateFinalBalanceList(
                initialBalance,
                projectedOpeningBalance,
                tomorrow,
                endDate,
                // Movimentações financeiras
                List.of(FinancialMovementFactory.buildModel(tomorrow, CREDIT, "100.00", PENDING),
                        FinancialMovementFactory.buildModel(tomorrow, DEBIT, "50.00", PENDING),
                        FinancialMovementFactory.buildModel(secondDate, DEBIT, "10.00", PENDING),
                        FinancialMovementFactory.buildModel(endDate, CREDIT, "100.00", PENDING)),
                // Contas fixas
                List.of(FixedBillFactory.buildModel(thirdDate, DEBIT, "100.00")),
                // Contas parceladas
                List.of(InstallmentBillFactory.buildModel(thirdDate, CREDIT, "100.00")),
                // Balanços diários esperados
                List.of(DailyBalanceFactory.buildDailyBalance(tomorrow,
                                "-100","0","0","-100",
                                "-100","100","-50","-50"),
                        DailyBalanceFactory.buildDailyBalance(secondDate,
                                "-100","0","0","-100",
                                "-50","0","-10","-60"),
                        DailyBalanceFactory.buildDailyBalance(thirdDate,
                                "-100","0","0","-100",
                                "-60","100","-100","-60"),
                        DailyBalanceFactory.buildDailyBalance(endDate,
                                "-100","0","0","-100",
                                "-60","100","0","40")));
    }

    private void mustValidateFinalBalanceList(BigDecimal initialBalance,
                                              BigDecimal projectedOpeningBalance,
                                              LocalDate initialDate,
                                              LocalDate endDate,
                                              List<FinancialMovement> mockFinancialMovementList,
                                              List<FixedBill> mockFixedBillList,
                                              List<InstallmentBill> mockInstallmentBillList,
                                              List<DailyBalance> expectedDailyBalanceList) {

        when(financialMovementService.findBalance(initialDate.minusDays(1))).thenReturn(initialBalance);
        when(financialMovementService.findProjectedBalance(initialDate.minusDays(1))).thenReturn(projectedOpeningBalance);
        when(financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate)).thenReturn(mockFinancialMovementList);
        when(fixedBillService.findAllByNextDueDateBetween(initialDate, endDate)).thenReturn(mockFixedBillList);
        when(installmentBillService.findAllByNextInstallmentDateBetween(initialDate, endDate)).thenReturn(mockInstallmentBillList);
        when(installmentBillService.findAllByNextInstallmentDateBetween(initialDate, endDate)).thenReturn(mockInstallmentBillList);

        Set<DailyBalance> resultDailyBalanceList = balanceService.findDailyBalanceBetween(initialDate, endDate);

        assertEquals(expectedDailyBalanceList.size(), resultDailyBalanceList.size(),
                String.format("expectedDailyBalanceList - expected size: %s, actual size: %s",
                        expectedDailyBalanceList.size(), resultDailyBalanceList.size()));

        expectedDailyBalanceList.forEach(expectedDailyBalance ->
                assertsDailyBalance(expectedDailyBalance, resultDailyBalanceList));
    }

    private void assertsDailyBalance(
            DailyBalance expectedDailyBalance,
            Set<DailyBalance> dailyBalanceListResult
    ) {
        LocalDate date = expectedDailyBalance.getDate();
        Optional<DailyBalance> dailyBalanceOptional = findDailyBalance(dailyBalanceListResult, date);

        assertTrue(dailyBalanceOptional.isPresent(), String.format("date: %s - daily balance not present", date));

        assertValue(date, "openingBalance", expectedDailyBalance.getOpeningBalance(), dailyBalanceOptional.get().getOpeningBalance());
        assertValue(date, "totalRevenue", expectedDailyBalance.getTotalRevenue(), dailyBalanceOptional.get().getTotalRevenue());
        assertValue(date, "totalExpense", expectedDailyBalance.getTotalExpense(), dailyBalanceOptional.get().getTotalExpense());
        assertValue(date, "closingBalance", expectedDailyBalance.getClosingBalance(), dailyBalanceOptional.get().getClosingBalance());

        assertValue(date, "projectedOpeningBalance", expectedDailyBalance.getProjectedOpeningBalance(), dailyBalanceOptional.get().getProjectedOpeningBalance());
        assertValue(date, "pendingTotalRevenue", expectedDailyBalance.getPendingTotalRevenue(), dailyBalanceOptional.get().getPendingTotalRevenue());
        assertValue(date, "pendingTotalExpense", expectedDailyBalance.getPendingTotalExpense(), dailyBalanceOptional.get().getPendingTotalExpense());
        assertValue(date, "projectedClosingBalance", expectedDailyBalance.getProjectedClosingBalance(), dailyBalanceOptional.get().getProjectedClosingBalance());
    }

    private void assertValue(LocalDate date, String valueDesc, BigDecimal expectedValue, BigDecimal actualValue) {
        assertThat(actualValue)
                .as("Date: %s, %s - actual: %s, expected: %s", date, valueDesc, actualValue, expectedValue)
                .isEqualByComparingTo(expectedValue);
    }

    private Optional<DailyBalance> findDailyBalance(Set<DailyBalance> dailyBalanceList, LocalDate today) {
        return dailyBalanceList.stream()
                .filter(db -> db.getDate().equals(today))
                .findAny();
    }

}
