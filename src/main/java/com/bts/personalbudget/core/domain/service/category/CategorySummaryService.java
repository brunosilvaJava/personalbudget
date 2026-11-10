package com.bts.personalbudget.core.domain.service.category;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.model.CategoryGroup;
import com.bts.personalbudget.core.domain.model.CategorySummary;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData.PaymentStatus;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Totals a period by category. It adds up the same data as the daily balance
 * (BalanceService.findBalanceCalcDataBetween), so the totals match the dashboard: real is what was
 * paid in the period, projected is real plus what is pending in it, including the occurrences of
 * fixed and installment bills that were not posted yet.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CategorySummaryService {

    static final long MAX_PERIOD_DAYS = 366;
    static final String UNCATEGORIZED_NAME = "Sem categoria";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;

    private final BalanceService balanceService;
    private final CategoryService categoryService;

    /**
     * @throws InvalidFieldsException (400) when end_date is before start_date or the period is
     *                                longer than {@value MAX_PERIOD_DAYS} days
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CategorySummary summarize(final LocalDate startDate,
                                     final LocalDate endDate,
                                     final OperationType operationType) {
        log.info("m=summarize, startDate={}, endDate={}, operationType={}", startDate, endDate, operationType);
        validatePeriod(startDate, endDate);

        final Map<UUID, Amounts> amountsByCategory = sumByCategory(startDate, endDate, operationType);

        final BigDecimal total = amountsByCategory.values().stream()
                .map(Amounts::real)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal projectedTotal = amountsByCategory.values().stream()
                .map(Amounts::projected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final List<CategorySummary.Group> groups = buildGroups(amountsByCategory, total, projectedTotal);

        return new CategorySummary(startDate, endDate, operationType, total, projectedTotal, groups);
    }

    private Map<UUID, Amounts> sumByCategory(final LocalDate startDate,
                                             final LocalDate endDate,
                                             final OperationType operationType) {
        final Map<UUID, Amounts> amountsByCategory = new HashMap<>();
        for (final BalanceCalcData data : balanceService.findBalanceCalcDataBetween(startDate, endDate)) {
            if (data.isPassThrough() || data.getOperationType() != operationType) {
                continue;
            }
            amountsByCategory.computeIfAbsent(data.getCategoryCode(), code -> new Amounts()).add(data);
        }
        return amountsByCategory;
    }

    private List<CategorySummary.Group> buildGroups(final Map<UUID, Amounts> amountsByCategory,
                                                    final BigDecimal total,
                                                    final BigDecimal projectedTotal) {
        final List<CategorySummary.Group> groups = buildCategorizedGroups(amountsByCategory, total, projectedTotal);

        final Amounts uncategorized = amountsByCategory.get(null);
        if (uncategorized != null) {
            final CategorySummary.Line line =
                    buildLine(null, UNCATEGORIZED_NAME, null, uncategorized, total, projectedTotal);
            groups.add(buildGroup(null, UNCATEGORIZED_NAME, null, List.of(line), total, projectedTotal));
        }
        return groups;
    }

    /**
     * Skips the category and group lookups when nothing in the period has a category.
     *
     * @return a modifiable list, ordered by the groups' display_order
     */
    private List<CategorySummary.Group> buildCategorizedGroups(final Map<UUID, Amounts> amountsByCategory,
                                                               final BigDecimal total,
                                                               final BigDecimal projectedTotal) {
        final Set<UUID> categoryCodes = amountsByCategory.keySet().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final List<CategorySummary.Group> groups = new ArrayList<>();
        if (categoryCodes.isEmpty()) {
            return groups;
        }

        final Map<UUID, Category> categories = categoryService.findAllByCodes(categoryCodes).stream()
                .collect(Collectors.toMap(Category::code, Function.identity()));
        final Map<UUID, CategoryGroup> groupsByCode = categoryService.findGroups().stream()
                .collect(Collectors.toMap(CategoryGroup::code, Function.identity()));

        final Map<UUID, List<CategorySummary.Line>> linesByGroup = new HashMap<>();
        for (final UUID categoryCode : categoryCodes) {
            final Category category = categories.get(categoryCode);
            if (category == null) {
                // category_code is a foreign key and categories are never deleted.
                throw new IllegalStateException("Category not found for code " + categoryCode);
            }
            linesByGroup.computeIfAbsent(category.groupCode(), code -> new ArrayList<>())
                    .add(buildLine(category.code(), category.name(), category,
                            amountsByCategory.get(categoryCode), total, projectedTotal));
        }

        linesByGroup.forEach((groupCode, lines) -> {
            final CategoryGroup group = groupsByCode.get(groupCode);
            lines.sort(LINE_ORDER);
            groups.add(buildGroup(group.code(), group.name(), group.displayOrder(), lines, total, projectedTotal));
        });
        groups.sort(Comparator.comparing(CategorySummary.Group::displayOrder));
        return groups;
    }

    private static final Comparator<CategorySummary.Line> LINE_ORDER = Comparator
            .comparing((CategorySummary.Line line) -> line.projectedTotal().abs()).reversed()
            .thenComparing(CategorySummary.Line::name);

    private CategorySummary.Line buildLine(final UUID code,
                                           final String name,
                                           final Category category,
                                           final Amounts amounts,
                                           final BigDecimal total,
                                           final BigDecimal projectedTotal) {
        return new CategorySummary.Line(
                code,
                name,
                category == null ? null : category.status(),
                amounts.real(),
                amounts.projected(),
                percentOf(amounts.real(), total),
                percentOf(amounts.projected(), projectedTotal));
    }

    private CategorySummary.Group buildGroup(final UUID code,
                                             final String name,
                                             final Integer displayOrder,
                                             final List<CategorySummary.Line> lines,
                                             final BigDecimal total,
                                             final BigDecimal projectedTotal) {
        final BigDecimal groupTotal = lines.stream()
                .map(CategorySummary.Line::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal groupProjectedTotal = lines.stream()
                .map(CategorySummary.Line::projectedTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CategorySummary.Group(
                code,
                name,
                displayOrder,
                groupTotal,
                groupProjectedTotal,
                percentOf(groupTotal, total),
                percentOf(groupProjectedTotal, projectedTotal),
                lines);
    }

    /**
     * Absolute value of the row over the absolute total, in percent. Zero when the total is zero.
     */
    private BigDecimal percentOf(final BigDecimal value, final BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return value.abs()
                .multiply(ONE_HUNDRED)
                .divide(total.abs(), PERCENT_SCALE, RoundingMode.HALF_EVEN);
    }

    private void validatePeriod(final LocalDate startDate, final LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidFieldsException("Parâmetros inválidos", CategorySummaryService.class,
                    Map.of("endDate", "deve ser igual ou posterior a start_date"));
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_PERIOD_DAYS) {
            throw new InvalidFieldsException("Parâmetros inválidos", CategorySummaryService.class,
                    Map.of("endDate", "o período não pode passar de " + MAX_PERIOD_DAYS + " dias"));
        }
    }

    /**
     * Running totals of one category: what was paid, and what is still pending.
     */
    private static final class Amounts {

        private BigDecimal real = BigDecimal.ZERO;
        private BigDecimal pending = BigDecimal.ZERO;

        void add(final BalanceCalcData data) {
            final BigDecimal value = data.getBalanceCalcValue() == null ? BigDecimal.ZERO : data.getBalanceCalcValue();
            if (data.findStatus() == PaymentStatus.DONE) {
                real = real.add(value);
            } else {
                pending = pending.add(value);
            }
        }

        BigDecimal real() {
            return real;
        }

        BigDecimal projected() {
            return real.add(pending);
        }
    }

}
