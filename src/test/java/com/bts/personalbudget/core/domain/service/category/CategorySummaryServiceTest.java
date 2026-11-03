package com.bts.personalbudget.core.domain.service.category;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.model.CategoryGroup;
import com.bts.personalbudget.core.domain.model.CategorySummary;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.GROUP_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.OTHER_CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.OTHER_GROUP_CODE;
import static com.bts.personalbudget.core.domain.service.balance.BalanceCalcData.PaymentStatus.DONE;
import static com.bts.personalbudget.core.domain.service.balance.BalanceCalcData.PaymentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategorySummaryServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 30);

    private static final UUID RENT = CATEGORY_CODE;
    private static final UUID CONDO = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID COURSE = OTHER_CATEGORY_CODE;

    private static final CategoryGroup HOME_GROUP = new CategoryGroup(GROUP_CODE, "Residência", 1);
    private static final CategoryGroup STUDY_GROUP = new CategoryGroup(OTHER_GROUP_CODE, "Instrução", 3);

    @InjectMocks
    private CategorySummaryService service;

    @Mock
    private BalanceService balanceService;

    @Mock
    private CategoryService categoryService;

    @Test
    void shouldTotalPaidAndProjectedByCategory() {
        stubScenario();

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.startDate()).isEqualTo(START);
        assertThat(summary.endDate()).isEqualTo(END);
        assertThat(summary.operationType()).isEqualTo(DEBIT);
        assertThat(summary.total()).isEqualByComparingTo("-2000");
        assertThat(summary.projectedTotal()).isEqualByComparingTo("-3000");

        final CategorySummary.Line rent = summary.groups().get(0).categories().get(0);
        assertThat(rent.code()).isEqualTo(RENT);
        assertThat(rent.name()).isEqualTo("Aluguel");
        assertThat(rent.status()).isEqualTo(CategoryStatus.ACTIVE);
        // Rent: 1000 paid and 500 still pending.
        assertThat(rent.total()).isEqualByComparingTo("-1000");
        assertThat(rent.projectedTotal()).isEqualByComparingTo("-1500");

        final CategorySummary.Group home = summary.groups().get(0);
        assertThat(home.total()).isEqualByComparingTo("-1250");
        assertThat(home.projectedTotal()).isEqualByComparingTo("-1750");
    }

    @Test
    void shouldCalculatePercentsOverTheAbsoluteTotal() {
        stubScenario();

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        final CategorySummary.Group home = summary.groups().get(0);
        assertThat(home.percent()).isEqualByComparingTo("62.50");
        assertThat(home.projectedPercent()).isEqualByComparingTo("58.33");
        assertThat(home.categories().get(0).percent()).isEqualByComparingTo("50.00");
        assertThat(home.categories().get(0).projectedPercent()).isEqualByComparingTo("50.00");
        assertThat(home.categories().get(1).percent()).isEqualByComparingTo("12.50");
        assertThat(home.categories().get(1).projectedPercent()).isEqualByComparingTo("8.33");

        final CategorySummary.Group study = summary.groups().get(1);
        assertThat(study.percent()).isEqualByComparingTo("37.50");
        assertThat(study.projectedPercent()).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldOrderGroupsByDisplayOrderAndCategoriesByProjectedTotal() {
        stubScenario();

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.groups()).extracting(CategorySummary.Group::name)
                .containsExactly("Residência", "Instrução", "Sem categoria");
        // Condo comes first in the data; rent (1500 projected) goes before it (250).
        assertThat(summary.groups().get(0).categories()).extracting(CategorySummary.Line::code)
                .containsExactly(RENT, CONDO);
    }

    @Test
    void shouldOrderCategoriesWithTheSameTotalByName() {
        final UUID internet = UUID.fromString("66666666-6666-6666-6666-666666666666");
        final UUID cleaning = UUID.fromString("77777777-7777-7777-7777-777777777777");
        stubData(item(internet, DEBIT, "-100", DONE), item(RENT, DEBIT, "-100", DONE),
                item(cleaning, DEBIT, "-300", DONE));
        stubCategories(List.of(
                        category(internet, "Internet", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP),
                        category(RENT, "Aluguel", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP),
                        category(cleaning, "Zeladoria", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP)),
                HOME_GROUP);

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.groups().get(0).categories()).extracting(CategorySummary.Line::name)
                .containsExactly("Zeladoria", "Aluguel", "Internet");
    }

    @Test
    void shouldPutUncategorizedMovementsInLastGroup() {
        stubScenario();

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        final CategorySummary.Group uncategorized = summary.groups().get(2);
        assertThat(uncategorized.code()).isNull();
        assertThat(uncategorized.displayOrder()).isNull();
        assertThat(uncategorized.total()).isEqualByComparingTo("0");
        assertThat(uncategorized.projectedTotal()).isEqualByComparingTo("-500");
        assertThat(uncategorized.projectedPercent()).isEqualByComparingTo("16.67");
        assertThat(uncategorized.categories()).hasSize(1);
        assertThat(uncategorized.categories().get(0).code()).isNull();
        assertThat(uncategorized.categories().get(0).status()).isNull();
        assertThat(uncategorized.categories().get(0).name()).isEqualTo("Sem categoria");
    }

    @Test
    void shouldCountPendingOnlyInProjectedTotal() {
        stubData(item(RENT, DEBIT, "-100", PENDING));
        stubCategories(List.of(category(RENT, "Aluguel", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP)), HOME_GROUP);

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.total()).isEqualByComparingTo("0");
        assertThat(summary.projectedTotal()).isEqualByComparingTo("-100");
        // Nothing paid: the share of the paid total is 0, not a division by zero.
        assertThat(summary.groups().get(0).percent()).isEqualByComparingTo("0.00");
        assertThat(summary.groups().get(0).projectedPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldIgnoreMovementsOfTheOtherOperationType() {
        stubData(item(RENT, DEBIT, "-100", DONE), item(COURSE, CREDIT, "900", DONE));
        stubCategories(List.of(category(RENT, "Aluguel", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP)), HOME_GROUP);

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.total()).isEqualByComparingTo("-100");
        assertThat(summary.groups()).hasSize(1);
        // The credit category is not even looked up.
        verify(categoryService).findAllByCodes(Set.of(RENT));
    }

    @Test
    void shouldSummarizeCreditsAsPositiveValues() {
        stubData(item(COURSE, CREDIT, "900", DONE), item(COURSE, CREDIT, "100", PENDING));
        stubCategories(List.of(category(COURSE, "Salário", CREDIT, CategoryStatus.ACTIVE, STUDY_GROUP)), STUDY_GROUP);

        final CategorySummary summary = service.summarize(START, END, CREDIT);

        assertThat(summary.operationType()).isEqualTo(CREDIT);
        assertThat(summary.total()).isEqualByComparingTo("900");
        assertThat(summary.projectedTotal()).isEqualByComparingTo("1000");
        assertThat(summary.groups().get(0).categories().get(0).projectedPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldKeepInactiveCategoryOfPastMovements() {
        stubData(item(RENT, DEBIT, "-100", DONE));
        stubCategories(List.of(category(RENT, "Aluguel", DEBIT, CategoryStatus.INACTIVE, HOME_GROUP)), HOME_GROUP);

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.groups().get(0).categories().get(0).status()).isEqualTo(CategoryStatus.INACTIVE);
    }

    @Test
    void shouldRoundPercentHalfEven() {
        // 1 of 800 is 0.125%: half-even gives 0.12 (half-up would give 0.13); 799 of 800 is
        // 99.875%, which half-even rounds up to 99.88 because the digit before is odd.
        stubData(item(RENT, DEBIT, "-1", DONE), item(COURSE, DEBIT, "-799", DONE));
        stubCategories(List.of(
                        category(RENT, "Aluguel", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP),
                        category(COURSE, "Curso", DEBIT, CategoryStatus.ACTIVE, STUDY_GROUP)),
                HOME_GROUP, STUDY_GROUP);

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.groups().get(0).percent()).isEqualByComparingTo("0.12");
        assertThat(summary.groups().get(1).percent()).isEqualByComparingTo("99.88");
    }

    @Test
    void shouldReturnEmptySummaryWhenPeriodHasNoMovements() {
        when(balanceService.findBalanceCalcDataBetween(START, END)).thenReturn(List.of());

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.total()).isEqualByComparingTo("0");
        assertThat(summary.projectedTotal()).isEqualByComparingTo("0");
        assertThat(summary.groups()).isEmpty();
        verifyNoInteractions(categoryService);
    }

    @Test
    void shouldNotLookUpCategoriesWhenOnlyUncategorizedMovements() {
        stubData(item(null, DEBIT, "-100", DONE));

        final CategorySummary summary = service.summarize(START, END, DEBIT);

        assertThat(summary.groups()).extracting(CategorySummary.Group::name).containsExactly("Sem categoria");
        assertThat(summary.groups().get(0).percent()).isEqualByComparingTo("100.00");
        verifyNoInteractions(categoryService);
    }

    @Test
    void shouldFailWhenCategoryOfAMovementDoesNotExist() {
        stubData(item(RENT, DEBIT, "-100", DONE));
        when(categoryService.findAllByCodes(any())).thenReturn(List.of());
        when(categoryService.findGroups()).thenReturn(List.of(HOME_GROUP));

        assertThrows(IllegalStateException.class, () -> service.summarize(START, END, DEBIT));
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.summarize(END, START, DEBIT));

        assertThat(exception.getValidations()).containsKey("endDate");
        verifyNoInteractions(balanceService, categoryService);
    }

    @Test
    void shouldRejectPeriodLongerThan366Days() {
        final LocalDate tooLate = START.plusDays(366);

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.summarize(START, tooLate, DEBIT));

        assertThat(exception.getValidations()).containsKey("endDate");
        verifyNoInteractions(balanceService, categoryService);
    }

    @Test
    void shouldAcceptPeriodOf366Days() {
        final LocalDate lastDay = START.plusDays(365);

        final CategorySummary summary = service.summarize(START, lastDay, DEBIT);

        assertThat(summary.endDate()).isEqualTo(lastDay);
        verify(balanceService).findBalanceCalcDataBetween(START, lastDay);
    }

    @Test
    void shouldAcceptSingleDayPeriod() {
        final CategorySummary summary = service.summarize(START, START, DEBIT);

        assertThat(summary.groups()).isEmpty();
        verify(balanceService).findBalanceCalcDataBetween(START, START);
    }

    /**
     * Debit period with two groups, three categories and one uncategorized pending movement.
     * Paid: rent 1000, condo 250, course 750. Pending: rent 500, uncategorized 500.
     */
    private void stubScenario() {
        stubData(
                item(CONDO, DEBIT, "-250", DONE),
                item(RENT, DEBIT, "-1000", DONE),
                item(RENT, DEBIT, "-500", PENDING),
                item(COURSE, DEBIT, "-750", DONE),
                item(null, DEBIT, "-500", PENDING));
        stubCategories(List.of(
                        category(RENT, "Aluguel", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP),
                        category(CONDO, "Condomínio", DEBIT, CategoryStatus.ACTIVE, HOME_GROUP),
                        category(COURSE, "Curso", DEBIT, CategoryStatus.ACTIVE, STUDY_GROUP)),
                HOME_GROUP, STUDY_GROUP);
    }

    private void stubData(final BalanceCalcData... items) {
        when(balanceService.findBalanceCalcDataBetween(START, END)).thenReturn(List.of(items));
    }

    private void stubCategories(final List<Category> categories, final CategoryGroup... groups) {
        when(categoryService.findAllByCodes(any())).thenReturn(categories);
        when(categoryService.findGroups()).thenReturn(List.of(groups));
    }

    private static Category category(final UUID code,
                                     final String name,
                                     final OperationType operationType,
                                     final CategoryStatus status,
                                     final CategoryGroup group) {
        return new Category(code, name, operationType, status, group.code(), group.name());
    }

    private static BalanceCalcData item(final UUID categoryCode,
                                        final OperationType operationType,
                                        final String value,
                                        final BalanceCalcData.PaymentStatus status) {
        return new Item(categoryCode, operationType, new BigDecimal(value), status);
    }

    /**
     * What the balance hands over for a period: a value with its sign, a status and a category.
     */
    private record Item(UUID categoryCode,
                        OperationType operationType,
                        BigDecimal value,
                        BalanceCalcData.PaymentStatus status) implements BalanceCalcData {

        @Override
        public LocalDate findBalanceCalcDate() {
            return START;
        }

        @Override
        public BigDecimal getBalanceCalcValue() {
            return value;
        }

        @Override
        public OperationType getOperationType() {
            return operationType;
        }

        @Override
        public PaymentStatus findStatus() {
            return status;
        }

        @Override
        public UUID getCategoryCode() {
            return categoryCode;
        }
    }

}
