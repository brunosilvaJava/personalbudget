package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class BalanceService {

    private final FinancialMovementService financialMovementService;
    private final InstallmentBillService installmentBillService;
    private final FixedBillService fixedBillService;

    public Set<DailyBalance> findDailyBalanceBetween(final LocalDate initialDate,
                                                     final LocalDate endDate) {
        log.info("m=findDailyBalanceBetween initialDate={} endDate={}", initialDate, endDate);

        final Set<DailyBalance> dailyBalanceList = new HashSet<>();
        final List<BalanceCalcData> balanceCalcDataList = findBalanceCalcDataList(initialDate, endDate);

        final AtomicReference<BigDecimal> previousBalance = new AtomicReference<>(
                financialMovementService.findBalance(initialDate.minusDays(1)));

        final List<LocalDate> datesList = initialDate.datesUntil(endDate.plusDays(1)).toList();

        datesList.forEach(date -> {
            final DailyBalance dailyBalance = new DailyBalance(date, previousBalance.get());
            final List<BalanceCalcData> balanceCalcData = balanceCalcDataList.stream()
                    .filter(bcd -> date.equals(bcd.findBalanceCalcDate()))
                    .toList();
            balanceCalcData.forEach(bcd -> {
                switch (bcd.getOperationType()) {
                    case CREDIT -> dailyBalance.addRevenue(bcd.getBalanceCalcValue());
                    case DEBIT -> dailyBalance.addExpense(bcd.getBalanceCalcValue());
                }
            });
            dailyBalanceList.add(dailyBalance);

            previousBalance.set(dailyBalance.getFinalBalance());
        });

        if (!datesList.contains(LocalDate.now())) {
            DailyBalance dailyBalance = dailyBalanceList.stream()
                    .filter(db -> db.getDate().equals(endDate))
                    .findFirst().orElseThrow();

            final List<BalanceCalcData> balanceCalcData =
                    balanceCalcDataList.stream()
                            .filter(bcd -> bcd.findBalanceCalcDate().equals(LocalDate.now()))
                            .toList();

            balanceCalcData.forEach(bcd -> {
                switch (bcd.getOperationType()) {
                    case CREDIT -> dailyBalance.addRevenue(bcd.getBalanceCalcValue());
                    case DEBIT -> dailyBalance.addExpense(bcd.getBalanceCalcValue());
                }
            });
        }

        return dailyBalanceList;
    }

    private List<BalanceCalcData> findBalanceCalcDataList(final LocalDate initialDate,
                                                          final LocalDate endDate) {
        final List<FinancialMovement> financialMovementList = financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate);
        final List<InstallmentBill> installmentBillList = installmentBillService.findAllByNextInstallmentDateBetween(initialDate, endDate);
        final List<FixedBill> fixedBillList = fixedBillService.findAllByNextDueDateBetween(initialDate, endDate);

        final List<BalanceCalcData> balanceCalcDataList = new ArrayList<>();
        balanceCalcDataList.addAll(financialMovementList);
        balanceCalcDataList.addAll(installmentBillList);
        balanceCalcDataList.addAll(fixedBillList);
        return balanceCalcDataList;
    }

}
