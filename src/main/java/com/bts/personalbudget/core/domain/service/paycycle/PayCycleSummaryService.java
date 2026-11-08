package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import com.bts.personalbudget.core.domain.service.balance.ProjectedOccurrence;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.income.IncomeProjectionPending;
import com.bts.personalbudget.core.domain.service.income.IncomeProjectionWindow;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary.Pending.Source.INCOME_SCHEDULE;
import static com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary.Pending.Source.PAY_CYCLE;
import static com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary.Pending.Type;

@Slf4j
@RequiredArgsConstructor
@Service
public class PayCycleSummaryService {

    static final String UNCATEGORIZED_NAME = "Sem categoria";

    private final PayCycleService payCycleService;
    private final FinancialMovementService financialMovementService;
    private final BalanceService balanceService;
    private final IncomeScheduleService incomeScheduleService;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public PayCycleSummary summarize(final UUID code) {
        log.info("m=summarize, code={}", code);
        final PayCycleView cycle = payCycleService.findByCode(code);
        final LocalDate from = cycle.cycle().startDate();
        final LocalDate toExclusive = cycle.cycle().endDate();
        final List<FinancialMovement> movements =
                financialMovementService.findByCompetenceInterval(from, toExclusive);
        final List<ProjectedOccurrence> projectedBills =
                balanceService.findProjectedBillOccurrencesBetween(from, toExclusive);
        final IncomeProjectionWindow incomeProjection =
                incomeScheduleService.findProjectionBetween(from, toExclusive);

        final Map<OperationType, Amounts> totals = new EnumMap<>(OperationType.class);
        for (final OperationType operationType : OperationType.values()) {
            totals.put(operationType, new Amounts());
        }
        final Map<CategoryKey, Amounts> amountsByCategory = new HashMap<>();
        movements.forEach(movement -> add(
                amountsByCategory, totals, movement.getOperationType(), movement.getCategoryCode(),
                movement.getBalanceCalcValue(), movement.findStatus() == BalanceCalcData.PaymentStatus.DONE));
        projectedBills.forEach(occurrence -> add(
                amountsByCategory, totals, occurrence.getOperationType(), occurrence.getCategoryCode(),
                occurrence.getBalanceCalcValue(), false));
        incomeProjection.occurrences().forEach(income -> add(
                amountsByCategory, totals, OperationType.CREDIT, income.categoryCode(), income.amount(), false));

        final Map<UUID, Category> categoriesByCode = categoryService.findAllByCodes(
                        amountsByCategory.keySet().stream()
                                .map(CategoryKey::categoryCode)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Category::code, Function.identity()));

        final List<PayCycleSummary.CategoryDetail> categoryDetails = amountsByCategory.entrySet().stream()
                .map(entry -> categoryDetail(entry.getKey(), entry.getValue(), categoriesByCode))
                .sorted(Comparator.comparing(PayCycleSummary.CategoryDetail::operationType)
                        .thenComparing(PayCycleSummary.CategoryDetail::projectedAmount, Comparator.reverseOrder())
                        .thenComparing(PayCycleSummary.CategoryDetail::categoryName))
                .toList();

        final List<PayCycleSummary.Pending> pending = new ArrayList<>();
        cycle.pending().forEach(item -> pending.add(new PayCycleSummary.Pending(
                PAY_CYCLE,
                Type.valueOf(item.type().name()),
                item.cycleCode(),
                item.nominalMonth(),
                item.year(),
                item.boundaryDate(),
                item.anchorDate())));
        incomeProjection.pending().forEach(item -> pending.add(toPending(item)));

        final Amounts credit = totals.get(OperationType.CREDIT);
        final Amounts debit = totals.get(OperationType.DEBIT);
        return new PayCycleSummary(cycle, credit.recognized, debit.recognized,
                credit.projected, debit.projected, categoryDetails,
                pending.stream()
                        .sorted(Comparator.comparing(PayCycleSummary.Pending::source)
                                .thenComparing(PayCycleSummary.Pending::nominalMonth,
                                        Comparator.nullsFirst(Comparator.naturalOrder()))
                                .thenComparing(PayCycleSummary.Pending::type))
                        .toList());
    }

    private static void add(final Map<CategoryKey, Amounts> amountsByCategory,
                            final Map<OperationType, Amounts> totals,
                            final OperationType operationType,
                            final UUID categoryCode,
                            final BigDecimal signedAmount,
                            final boolean recognized) {
        final BigDecimal amount = operationType == OperationType.DEBIT
                ? signedAmount.abs() : signedAmount;
        totals.get(operationType).add(amount, recognized);
        amountsByCategory.computeIfAbsent(new CategoryKey(operationType, categoryCode), ignored -> new Amounts())
                .add(amount, recognized);
    }

    private static PayCycleSummary.CategoryDetail categoryDetail(final CategoryKey key,
                                                                  final Amounts amounts,
                                                                  final Map<UUID, Category> categoriesByCode) {
        if (key.categoryCode() == null) {
            return new PayCycleSummary.CategoryDetail(null, UNCATEGORIZED_NAME, key.operationType(),
                    amounts.recognized, amounts.projected);
        }
        final Category category = categoriesByCode.get(key.categoryCode());
        if (category == null) {
            throw new IllegalStateException("Category not found for code " + key.categoryCode());
        }
        return new PayCycleSummary.CategoryDetail(category.code(), category.name(), key.operationType(),
                amounts.recognized, amounts.projected);
    }

    private static PayCycleSummary.Pending toPending(final IncomeProjectionPending pending) {
        return new PayCycleSummary.Pending(
                INCOME_SCHEDULE,
                Type.valueOf(pending.type().name()),
                pending.scheduleCode(),
                pending.nominalMonth(),
                pending.year(),
                null,
                null);
    }

    private record CategoryKey(OperationType operationType, UUID categoryCode) {
    }

    private static final class Amounts {

        private BigDecimal recognized = BigDecimal.ZERO;
        private BigDecimal projected = BigDecimal.ZERO;

        void add(final BigDecimal amount, final boolean isRecognized) {
            projected = projected.add(amount);
            if (isRecognized) {
                recognized = recognized.add(amount);
            }
        }
    }
}
