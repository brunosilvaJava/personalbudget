package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData.PaymentStatus;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
    private final IncomeScheduleService incomeScheduleService;

    public Set<DailyBalance> findDailyBalanceBetween(final LocalDate initialDate,
                                                     final LocalDate endDate) {
        log.info("m=findDailyBalanceBetween initialDate={} endDate={}", initialDate, endDate);

        final Set<DailyBalance> dailyBalanceList = new LinkedHashSet<>();
        final List<ProjectedOccurrence> projectedOccurrences = findProjectedOccurrencesUntil(endDate);
        final List<BalanceCalcData> balanceCalcDataList = findBalanceCalcDataList(initialDate, endDate, projectedOccurrences);

        final LocalDate openingBalanceDate = initialDate.minusDays(1);
        final AtomicReference<BigDecimal> openingBalance = new AtomicReference<>(
                financialMovementService.findBalance(openingBalanceDate));

        // Occurrences dated before the period that are still to be posted (e.g. the rest of this
        // month, when projecting next month) are already committed by the period's start.
        final BigDecimal projectedBeforePeriod = projectedOccurrences.stream()
                .filter(occurrence -> occurrence.occurrenceDate().isBefore(initialDate))
                .map(ProjectedOccurrence::getBalanceCalcValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final AtomicReference<BigDecimal> projectedOpeningBalance = new AtomicReference<>(
                financialMovementService.findProjectedBalance(openingBalanceDate).add(projectedBeforePeriod));

        final List<LocalDate> datesList = initialDate.datesUntil(endDate.plusDays(1)).toList();

        datesList.forEach(date -> {
            final DailyBalance dailyBalance = new DailyBalance(date, openingBalance.get(), projectedOpeningBalance.get());
            final List<BalanceCalcData> balanceCalcData = balanceCalcDataList.stream()
                    .filter(bcd -> date.equals(bcd.findBalanceCalcDate()))
                    .toList();
            balanceCalcData.forEach(bcd -> addCalcValue(bcd, dailyBalance));
            dailyBalanceList.add(dailyBalance);
            openingBalance.set(dailyBalance.getClosingBalance());
            projectedOpeningBalance.set(dailyBalance.getProjectedClosingBalance());
        });

        return dailyBalanceList;
    }

    /**
     * What findDailyBalanceBetween adds up for the period: the movements that count in it (PAID_OUT
     * by pay date, PENDING/LATE by due date) and the bills' occurrences not posted yet. Exposed so
     * other reports total the period the same way and match the balance and the dashboard.
     */
    public List<BalanceCalcData> findBalanceCalcDataBetween(final LocalDate initialDate,
                                                            final LocalDate endDate) {
        log.info("m=findBalanceCalcDataBetween initialDate={} endDate={}", initialDate, endDate);
        return findBalanceCalcDataList(initialDate, endDate, findProjectedOccurrencesUntil(endDate));
    }

    private void addCalcValue(BalanceCalcData balanceCalcData, DailyBalance dailyBalance) {
        final BigDecimal balanceCalcValue = balanceCalcData.getBalanceCalcValue();
        switch (balanceCalcData.getOperationType()) {
            case CREDIT -> {
                if (balanceCalcData.findStatus() == PaymentStatus.DONE) {
                    dailyBalance.addRevenue(balanceCalcValue);
                } else {
                    dailyBalance.addProjectedRevenue(balanceCalcValue);
                }
            }
            case DEBIT -> {
                if (balanceCalcData.findStatus() == PaymentStatus.DONE) {
                    dailyBalance.addExpense(balanceCalcValue);
                } else {
                    dailyBalance.addProjectedExpense(balanceCalcValue);
                }
            }
        }
    }

    private List<BalanceCalcData> findBalanceCalcDataList(final LocalDate initialDate,
                                                          final LocalDate endDate,
                                                          final List<ProjectedOccurrence> projectedOccurrences) {
        final List<FinancialMovement> financialMovementList = financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate);

        final List<BalanceCalcData> balanceCalcDataList = new ArrayList<>(financialMovementList);
        projectedOccurrences.stream()
                .filter(occurrence -> !occurrence.occurrenceDate().isBefore(initialDate))
                .forEach(balanceCalcDataList::add);
        return balanceCalcDataList;
    }

    /**
     * Every occurrence of the active fixed bills, pending installment bills and active income
     * schedules that has not been posted as a FinancialMovement yet, up to {@code endDate} - not
     * only each bill's next one.
     */
    private List<ProjectedOccurrence> findProjectedOccurrencesUntil(final LocalDate endDate) {
        final List<ProjectedOccurrence> projectedOccurrences = new ArrayList<>();

        fixedBillService.findAllByNextDueDateLessThanEqual(endDate).forEach(fixedBill ->
                fixedBillService.findOccurrencesUntil(fixedBill, endDate).forEach(occurrenceDate ->
                        projectedOccurrences.add(ProjectedOccurrence.of(fixedBill.getCode(), fixedBill, occurrenceDate))));

        installmentBillService.findAllByNextInstallmentDateLessThanEqual(endDate).forEach(installmentBill ->
                installmentBill.findRemainingInstallmentDatesUntil(endDate).forEach(occurrenceDate ->
                        projectedOccurrences.add(ProjectedOccurrence.of(installmentBill.getCode(), installmentBill, occurrenceDate))));

        // An income occurrence stops being projected as soon as it is materialized (its movement
        // counts instead), so the same receipt is never in the balance twice.
        incomeScheduleService.findProjectedOccurrencesUntil(endDate).forEach(income ->
                projectedOccurrences.add(new ProjectedOccurrence(income.scheduleCode(), income.date(),
                        income.amount(), OperationType.CREDIT, income.categoryCode())));

        return excludeAlreadyPosted(projectedOccurrences);
    }

    /**
     * By construction a bill's occurrences from its next date on are not posted yet; this is the
     * safety net for the cases where that does not hold (a schedule edited after the job ran, data
     * from before the posting rules changed), so an occurrence is never counted twice: once as
     * its FinancialMovement and once as a projection.
     */
    private List<ProjectedOccurrence> excludeAlreadyPosted(final List<ProjectedOccurrence> projectedOccurrences) {
        if (projectedOccurrences.isEmpty()) {
            return projectedOccurrences;
        }
        final Set<UUID> recurrenceBillCodes = projectedOccurrences.stream()
                .map(ProjectedOccurrence::recurrenceBillCode)
                .collect(Collectors.toSet());
        final LocalDate firstOccurrence = projectedOccurrences.stream()
                .map(ProjectedOccurrence::occurrenceDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        final LocalDate lastOccurrence = projectedOccurrences.stream()
                .map(ProjectedOccurrence::occurrenceDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        final Set<OccurrenceKey> postedOccurrences = financialMovementService
                .findRecurrenceOccurrencesBetween(recurrenceBillCodes, firstOccurrence, lastOccurrence)
                .stream()
                .map(financialMovement -> new OccurrenceKey(financialMovement.recurrenceBillCode(), financialMovement.occurrenceDate()))
                .collect(Collectors.toSet());

        return projectedOccurrences.stream()
                .filter(occurrence -> !postedOccurrences.contains(
                        new OccurrenceKey(occurrence.recurrenceBillCode(), occurrence.occurrenceDate())))
                .toList();
    }

    private record OccurrenceKey(UUID recurrenceBillCode, LocalDate occurrenceDate) {
    }

}
