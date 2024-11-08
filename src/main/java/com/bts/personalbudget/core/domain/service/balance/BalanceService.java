package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class BalanceService {

    private final FinancialMovementService financialMovementService;
    private final InstallmentBillService installmentBillService;
    private final FixedBillService fixedBillService;

    public Set<DailyBalance> findDailyBalanceBetween(final LocalDate initialDate,
                                                     final LocalDate endDate) {
        final Set<DailyBalance> dailyBalanceList = new HashSet<>();

        final List<FinancialMovement> financialMovementList =
                financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate);
        final List<InstallmentBill> installmentBillList =
                installmentBillService.findAllByNextInstallmentDateBetween(initialDate, endDate);
        final List<FixedBill> fixedBillList =
                fixedBillService.findAllByFlagActiveTrueAndNextDueDateBetween(initialDate, endDate);

        final List<BalanceCalc> balanceCalcList = new ArrayList<>();
        balanceCalcList.addAll(financialMovementList);
        balanceCalcList.addAll(installmentBillList);
        balanceCalcList.addAll(fixedBillList);

        balanceCalcList.forEach(
                balanceCalc -> {
                    final LocalDate date = balanceCalc.getBalanceCalcDate();
                    final DailyBalance dailyBalance = dailyBalanceList.stream()
                            .filter(db -> db.getDate().equals(date))
                            .findFirst()
                            .orElse(new DailyBalance(date));
                    switch (balanceCalc.getOperationType()) {
                        case CREDIT -> dailyBalance.addRevenue(balanceCalc.getBalanceCalcValue());
                        case DEBIT -> dailyBalance.addExpense(balanceCalc.getBalanceCalcValue());
                    }
                    dailyBalanceList.add(dailyBalance);
                }
        );

        return dailyBalanceList;
    }

//    private void extracted(LocalDate initialDate, LocalDate endDate, Set<DailyBalance> dailyBalanceList) {
//        final List<FinancialMovement> financialMovementList = financialMovementService.findAllByStatusAndDates(initialDate, endDate);
//
//        financialMovementList.forEach(
//                financialMovement -> {
//                    final FinancialMovementStatus status = financialMovement.status();
//                    final Optional<LocalDate> dateOptional = Optional.ofNullable(switch (status) {
//                        case PENDING -> financialMovement.dueDate().toLocalDate();
//                        case PAID_OUT -> financialMovement.payDate().toLocalDate();
//                        case LATE -> isLocalDateBetween(LocalDate.now(), initialDate, endDate) ? LocalDate.now() : null;
//                    });
//
//                    if (dateOptional.isPresent()) {
//                        final LocalDate date = dateOptional.get();
//                        final DailyBalance dailyBalance = dailyBalanceList.stream()
//                                .filter(db -> db.getDate().equals(date))
//                                .findFirst()
//                                .orElse(new DailyBalance(date));
//                        final BigDecimal value = switch (status) {
//                            case PENDING, LATE -> financialMovement.findAmountForCalc().orElseThrow();
//                            case PAID_OUT -> financialMovement.findAmountPaidForCalc().orElseThrow();
//                        };
//                        switch (financialMovement.operationType()) {
//                            case CREDIT -> dailyBalance.addRevenue(value);
//                            case DEBIT -> dailyBalance.addExpense(value);
//                        }
//                        dailyBalanceList.add(dailyBalance);
//                    }
//                }
//        );
//    }

}
