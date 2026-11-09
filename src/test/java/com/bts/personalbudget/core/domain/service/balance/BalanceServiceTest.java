package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.factory.DailyBalanceFactory;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.income.IncomeProjection;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillFactory;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;

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

    @Mock
    private IncomeScheduleService incomeScheduleService;

    @Mock
    private MovementSettlementService movementSettlementService;

    @BeforeEach
    void setUp() {
        lenient().when(movementSettlementService.findForCashDateBetween(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    @Test
    void shouldRecognizeEachPartialPaymentOnItsEffectiveDateAndProjectOnlyTheResidual() {
        final LocalDate start = LocalDate.of(2026, 11, 1);
        final LocalDate end = start.plusDays(2);
        final UUID code = UUID.randomUUID();
        when(financialMovementService.findBalance(start.minusDays(1))).thenReturn(BigDecimal.ZERO);
        when(financialMovementService.findProjectedBalance(start.minusDays(1))).thenReturn(BigDecimal.ZERO);
        when(financialMovementService.findMovementsForBalanceCalculation(start, end)).thenReturn(List.of(
                new FinancialMovement(code, DEBIT, "Parcial", new BigDecimal("-100"),
                        new BigDecimal("-50"), start.atStartOfDay(), end.atStartOfDay(),
                        null, PENDING, true, null, null, null)));
        when(movementSettlementService.findForCashDateBetween(start, end)).thenReturn(List.of(
                new MovementSettlement(UUID.randomUUID(), code, new BigDecimal("30"), start,
                        null, 0L, DEBIT, null, start),
                new MovementSettlement(UUID.randomUUID(), code, new BigDecimal("20"), start.plusDays(1),
                        null, 0L, DEBIT, null, start)));

        final List<DailyBalance> balances = balanceService.findDailyBalanceBetween(start, end).stream().toList();

        assertThat(balances.get(0).getClosingBalance()).isEqualByComparingTo("-30");
        assertThat(balances.get(1).getClosingBalance()).isEqualByComparingTo("-50");
        assertThat(balances.get(2).getClosingBalance()).isEqualByComparingTo("-50");
        assertThat(balances.get(2).getProjectedClosingBalance()).isEqualByComparingTo("-100");
    }

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

    private static final LocalDate JULY_1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate SEPTEMBER_30 = LocalDate.of(2026, 9, 30);

    @Test
    void shouldProjectAllOccurrencesInPeriodWhenBalanceProjected() {
        final FixedBill rent = buildMonthlyDebitFixedBill(LocalDate.of(2026, 7, 10));
        final List<LocalDate> occurrences = List.of(
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10));

        stubPeriod(JULY_1, SEPTEMBER_30, List.of());
        when(fixedBillService.findAllByNextDueDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of(rent));
        when(fixedBillService.findOccurrencesUntil(rent, SEPTEMBER_30)).thenReturn(occurrences);
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of());

        Set<DailyBalance> result = balanceService.findDailyBalanceBetween(JULY_1, SEPTEMBER_30);

        occurrences.forEach(date -> assertValue(date, "pendingTotalExpense", new BigDecimal("-100"),
                findDailyBalance(result, date).orElseThrow().getPendingTotalExpense()));
        assertValue(SEPTEMBER_30, "projectedClosingBalance", new BigDecimal("-300"),
                findDailyBalance(result, SEPTEMBER_30).orElseThrow().getProjectedClosingBalance());
    }

    @Test
    void shouldNotProjectOccurrenceAlreadyPosted() {
        final LocalDate july10 = LocalDate.of(2026, 7, 10);
        final LocalDate august10 = LocalDate.of(2026, 8, 10);
        final LocalDate september10 = LocalDate.of(2026, 9, 10);
        final FixedBill rent = buildMonthlyDebitFixedBill(july10);

        // August's occurrence already exists as a movement (e.g. the schedule was edited after
        // the job posted it): it must count once, as the movement, not again as a projection.
        final FinancialMovement postedAugust = new FinancialMovement(DEBIT, "rent", new BigDecimal("100.00"),
                august10.atStartOfDay(), august10.atStartOfDay(), PENDING, rent.getCode(), august10);

        stubPeriod(JULY_1, SEPTEMBER_30, List.of(postedAugust));
        when(fixedBillService.findAllByNextDueDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of(rent));
        when(fixedBillService.findOccurrencesUntil(rent, SEPTEMBER_30)).thenReturn(List.of(july10, august10, september10));
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of());
        when(financialMovementService.findRecurrenceOccurrencesBetween(Set.of(rent.getCode()), july10, september10))
                .thenReturn(List.of(postedAugust));

        Set<DailyBalance> result = balanceService.findDailyBalanceBetween(JULY_1, SEPTEMBER_30);

        List.of(july10, august10, september10).forEach(date -> assertValue(date, "pendingTotalExpense",
                new BigDecimal("-100"), findDailyBalance(result, date).orElseThrow().getPendingTotalExpense()));
        assertValue(SEPTEMBER_30, "projectedClosingBalance", new BigDecimal("-300"),
                findDailyBalance(result, SEPTEMBER_30).orElseThrow().getProjectedClosingBalance());
    }

    @Test
    void shouldProjectRemainingInstallmentsInPeriod() {
        // 2 of 6 installments posted: 4 remain, monthly from July 5; 3 fall in the period.
        final InstallmentBill television = new InstallmentBill(UUID.randomUUID(), DEBIT, "television",
                new BigDecimal("50.00"), InstallmentBillStatus.PENDING, LocalDate.of(2026, 4, 5), 6,
                2, LocalDate.of(2026, 6, 5), LocalDate.of(2026, 7, 5));
        // 2 of 3 posted: only July 20 remains, nothing a month later.
        final InstallmentBill almostPaid = new InstallmentBill(UUID.randomUUID(), DEBIT, "almost paid",
                new BigDecimal("30.00"), InstallmentBillStatus.PENDING, LocalDate.of(2026, 4, 20), 3,
                2, LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 20));

        stubPeriod(JULY_1, SEPTEMBER_30, List.of());
        when(fixedBillService.findAllByNextDueDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of());
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(SEPTEMBER_30))
                .thenReturn(List.of(television, almostPaid));

        Set<DailyBalance> result = balanceService.findDailyBalanceBetween(JULY_1, SEPTEMBER_30);

        List.of(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5))
                .forEach(date -> assertValue(date, "pendingTotalExpense", new BigDecimal("-50"),
                        findDailyBalance(result, date).orElseThrow().getPendingTotalExpense()));
        assertValue(LocalDate.of(2026, 7, 20), "pendingTotalExpense", new BigDecimal("-30"),
                findDailyBalance(result, LocalDate.of(2026, 7, 20)).orElseThrow().getPendingTotalExpense());
        assertValue(LocalDate.of(2026, 8, 19), "pendingTotalExpense", BigDecimal.ZERO,
                findDailyBalance(result, LocalDate.of(2026, 8, 19)).orElseThrow().getPendingTotalExpense());
        assertValue(SEPTEMBER_30, "projectedClosingBalance", new BigDecimal("-180"),
                findDailyBalance(result, SEPTEMBER_30).orElseThrow().getProjectedClosingBalance());
    }

    @Test
    void shouldAddOccurrencesBeforePeriodToProjectedOpeningBalance() {
        final LocalDate august1 = LocalDate.of(2026, 8, 1);
        final LocalDate august31 = LocalDate.of(2026, 8, 31);
        final LocalDate july10 = LocalDate.of(2026, 7, 10);
        final LocalDate august10 = LocalDate.of(2026, 8, 10);
        final FixedBill rent = buildMonthlyDebitFixedBill(july10);

        stubPeriod(august1, august31, List.of());
        when(fixedBillService.findAllByNextDueDateLessThanEqual(august31)).thenReturn(List.of(rent));
        when(fixedBillService.findOccurrencesUntil(rent, august31)).thenReturn(List.of(july10, august10));
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(august31)).thenReturn(List.of());

        Set<DailyBalance> result = balanceService.findDailyBalanceBetween(august1, august31);

        final DailyBalance firstDay = findDailyBalance(result, august1).orElseThrow();
        assertValue(august1, "openingBalance", BigDecimal.ZERO, firstDay.getOpeningBalance());
        assertValue(august1, "projectedOpeningBalance", new BigDecimal("-100"), firstDay.getProjectedOpeningBalance());
        assertValue(august10, "pendingTotalExpense", new BigDecimal("-100"),
                findDailyBalance(result, august10).orElseThrow().getPendingTotalExpense());
        assertValue(august31, "projectedClosingBalance", new BigDecimal("-200"),
                findDailyBalance(result, august31).orElseThrow().getProjectedClosingBalance());
    }

    private static final UUID INCOME_SCHEDULE_CODE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SALARY_CATEGORY = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void shouldProjectIncomeScheduleOccurrenceAsPendingRevenue() {
        final LocalDate july7 = LocalDate.of(2026, 7, 7);

        stubPeriod(JULY_1, SEPTEMBER_30, List.of());
        when(incomeScheduleService.findProjectedOccurrencesUntil(SEPTEMBER_30)).thenReturn(List.of(
                new IncomeProjection(INCOME_SCHEDULE_CODE, YearMonth.of(2026, 7), july7,
                        new BigDecimal("5000.00"), SALARY_CATEGORY)));

        Set<DailyBalance> result = balanceService.findDailyBalanceBetween(JULY_1, SEPTEMBER_30);

        assertValue(july7, "pendingTotalRevenue", new BigDecimal("5000.00"),
                findDailyBalance(result, july7).orElseThrow().getPendingTotalRevenue());
        assertValue(SEPTEMBER_30, "projectedClosingBalance", new BigDecimal("5000.00"),
                findDailyBalance(result, SEPTEMBER_30).orElseThrow().getProjectedClosingBalance());
        // Projection only: the real (paid) balance does not move.
        assertValue(SEPTEMBER_30, "closingBalance", BigDecimal.ZERO,
                findDailyBalance(result, SEPTEMBER_30).orElseThrow().getClosingBalance());
    }

    @Test
    void shouldKeepIncomeCategoryOnProjectedOccurrences() {
        final LocalDate july7 = LocalDate.of(2026, 7, 7);

        stubMovements(JULY_1, SEPTEMBER_30, List.of());
        when(incomeScheduleService.findProjectedOccurrencesUntil(SEPTEMBER_30)).thenReturn(List.of(
                new IncomeProjection(INCOME_SCHEDULE_CODE, YearMonth.of(2026, 7), july7,
                        new BigDecimal("5000.00"), SALARY_CATEGORY)));

        final List<BalanceCalcData> result = balanceService.findBalanceCalcDataBetween(JULY_1, SEPTEMBER_30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperationType()).isEqualTo(CREDIT);
        assertThat(result.get(0).getBalanceCalcValue()).isEqualByComparingTo("5000.00");
        assertThat(result.get(0).getCategoryCode()).isEqualTo(SALARY_CATEGORY);
        assertThat(result.get(0).findStatus()).isEqualTo(BalanceCalcData.PaymentStatus.PENDING);
    }

    @Test
    void shouldNotCountIncomeOccurrenceAlreadyPostedAsMovement() {
        final LocalDate july7 = LocalDate.of(2026, 7, 7);
        // The occurrence key is the nominal month; the due date is the planned date.
        final FinancialMovement postedSalary = new FinancialMovement(CREDIT, "Salário", new BigDecimal("5000.00"),
                july7.atStartOfDay(), july7.atStartOfDay(), PENDING, INCOME_SCHEDULE_CODE, JULY_1);

        stubPeriod(JULY_1, SEPTEMBER_30, List.of(postedSalary));
        when(incomeScheduleService.findProjectedOccurrencesUntil(SEPTEMBER_30)).thenReturn(List.of(
                new IncomeProjection(INCOME_SCHEDULE_CODE, YearMonth.of(2026, 7), july7,
                        new BigDecimal("5000.00"), SALARY_CATEGORY)));
        when(financialMovementService.findRecurrenceOccurrencesBetween(Set.of(INCOME_SCHEDULE_CODE), JULY_1, JULY_1))
                .thenReturn(List.of(postedSalary));

        Set<DailyBalance> result = balanceService.findDailyBalanceBetween(JULY_1, SEPTEMBER_30);

        assertValue(july7, "pendingTotalRevenue", new BigDecimal("5000.00"),
                findDailyBalance(result, july7).orElseThrow().getPendingTotalRevenue());
        assertValue(SEPTEMBER_30, "projectedClosingBalance", new BigDecimal("5000.00"),
                findDailyBalance(result, SEPTEMBER_30).orElseThrow().getProjectedClosingBalance());
    }

    @Test
    void shouldKeepIncomeProjectionWhenAnotherNominalMonthHasTheSamePlannedDate() {
        final LocalDate plannedDate = LocalDate.of(2026, 3, 2);
        final LocalDate februaryKey = LocalDate.of(2026, 2, 1);
        final LocalDate marchKey = LocalDate.of(2026, 3, 1);
        final FinancialMovement februarySalary = new FinancialMovement(
                null, CREDIT, "Salário", new BigDecimal("5000.00"), null,
                plannedDate.atStartOfDay(), plannedDate.atStartOfDay(), null, PENDING, true,
                INCOME_SCHEDULE_CODE, februaryKey, SALARY_CATEGORY, plannedDate, false);

        stubMovements(marchKey, LocalDate.of(2026, 3, 31), List.of(februarySalary));
        when(fixedBillService.findAllByNextDueDateLessThanEqual(LocalDate.of(2026, 3, 31))).thenReturn(List.of());
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of());
        when(incomeScheduleService.findProjectedOccurrencesUntil(LocalDate.of(2026, 3, 31))).thenReturn(List.of(
                new IncomeProjection(INCOME_SCHEDULE_CODE, YearMonth.of(2026, 3), plannedDate,
                        new BigDecimal("5000.00"), SALARY_CATEGORY)));
        when(financialMovementService.findRecurrenceOccurrencesBetween(
                Set.of(INCOME_SCHEDULE_CODE), marchKey, marchKey)).thenReturn(List.of());

        final List<BalanceCalcData> result =
                balanceService.findBalanceCalcDataBetween(marchKey, LocalDate.of(2026, 3, 31));

        assertThat(result).hasSize(2);
        assertThat(sum(result, CREDIT, BalanceCalcData.PaymentStatus.PENDING)).isEqualByComparingTo("10000.00");
    }

    private static final UUID RENT_CATEGORY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TELEVISION_CATEGORY = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void shouldKeepBillCategoryOnProjectedOccurrences() {
        final FixedBill rent = buildMonthlyDebitFixedBill(LocalDate.of(2026, 7, 10));
        rent.setCategoryCode(RENT_CATEGORY);
        final InstallmentBill television = new InstallmentBill(UUID.randomUUID(), DEBIT, "television",
                new BigDecimal("50.00"), InstallmentBillStatus.PENDING, LocalDate.of(2026, 4, 5), 6,
                2, LocalDate.of(2026, 6, 5), LocalDate.of(2026, 7, 5));
        television.setCategoryCode(TELEVISION_CATEGORY);

        stubMovements(JULY_1, SEPTEMBER_30, List.of());
        when(fixedBillService.findAllByNextDueDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of(rent));
        when(fixedBillService.findOccurrencesUntil(rent, SEPTEMBER_30))
                .thenReturn(List.of(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10)));
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(SEPTEMBER_30))
                .thenReturn(List.of(television));

        final List<BalanceCalcData> result = balanceService.findBalanceCalcDataBetween(JULY_1, SEPTEMBER_30);

        // 2 occurrences of the rent and 3 remaining installments (July 5, August 5, September 5).
        assertThat(result).hasSize(5);
        assertThat(result).allMatch(data -> data.findStatus() == BalanceCalcData.PaymentStatus.PENDING);
        assertThat(result.stream().filter(data -> RENT_CATEGORY.equals(data.getCategoryCode())).count()).isEqualTo(2);
        assertThat(result.stream().filter(data -> TELEVISION_CATEGORY.equals(data.getCategoryCode())).count())
                .isEqualTo(3);
    }

    @Test
    void shouldReturnMovementsWithTheirCategoryAndStatus() {
        final LocalDate day = LocalDate.of(2026, 7, 15);
        final FinancialMovement paid = FinancialMovementFactory.withCategory(
                FinancialMovementFactory.buildModel(day, DEBIT, "80.00", PAID_OUT), RENT_CATEGORY);
        final FinancialMovement pending = FinancialMovementFactory.buildModel(day, DEBIT, "20.00", PENDING);

        stubMovements(JULY_1, SEPTEMBER_30, List.of(paid, pending));
        when(fixedBillService.findAllByNextDueDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of());
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of());

        final List<BalanceCalcData> result = balanceService.findBalanceCalcDataBetween(JULY_1, SEPTEMBER_30);

        assertThat(result).containsExactlyInAnyOrder(paid, pending);
        assertThat(paid.getCategoryCode()).isEqualTo(RENT_CATEGORY);
        assertThat(paid.findStatus()).isEqualTo(BalanceCalcData.PaymentStatus.DONE);
        assertThat(pending.getCategoryCode()).isNull();
        assertThat(pending.findStatus()).isEqualTo(BalanceCalcData.PaymentStatus.PENDING);
    }

    @Test
    void shouldLeaveOutOccurrencesBeforeThePeriodFromCalcData() {
        final LocalDate august1 = LocalDate.of(2026, 8, 1);
        final LocalDate august31 = LocalDate.of(2026, 8, 31);
        final LocalDate july10 = LocalDate.of(2026, 7, 10);
        final LocalDate august10 = LocalDate.of(2026, 8, 10);
        final FixedBill rent = buildMonthlyDebitFixedBill(july10);

        stubMovements(august1, august31, List.of());
        when(fixedBillService.findAllByNextDueDateLessThanEqual(august31)).thenReturn(List.of(rent));
        when(fixedBillService.findOccurrencesUntil(rent, august31)).thenReturn(List.of(july10, august10));
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(august31)).thenReturn(List.of());

        final List<BalanceCalcData> result = balanceService.findBalanceCalcDataBetween(august1, august31);

        // July's occurrence only weighs on the projected opening balance of the daily balance.
        assertThat(result).extracting(BalanceCalcData::findBalanceCalcDate).containsExactly(august10);
    }

    @Test
    void shouldTotalCalcDataTheSameWayAsTheDailyBalance() {
        // A summary that reads findBalanceCalcDataBetween must match the daily balance of the
        // same period, to the cent: paid is "real", paid plus pending is "projected".
        final LocalDate day1 = LocalDate.of(2026, 7, 3);
        final LocalDate day2 = LocalDate.of(2026, 7, 20);
        final FixedBill rent = buildMonthlyDebitFixedBill(LocalDate.of(2026, 7, 10));
        rent.setCategoryCode(RENT_CATEGORY);
        final List<FinancialMovement> movements = List.of(
                FinancialMovementFactory.withCategory(
                        FinancialMovementFactory.buildModel(day1, DEBIT, "80.00", PAID_OUT), RENT_CATEGORY),
                FinancialMovementFactory.buildModel(day2, DEBIT, "20.00", PENDING),
                FinancialMovementFactory.buildModel(day2, CREDIT, "500.00", PAID_OUT));

        stubPeriod(JULY_1, SEPTEMBER_30, movements);
        when(fixedBillService.findAllByNextDueDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of(rent));
        when(fixedBillService.findOccurrencesUntil(rent, SEPTEMBER_30)).thenReturn(List.of(
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10)));
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(SEPTEMBER_30)).thenReturn(List.of());

        final Set<DailyBalance> dailyBalances = balanceService.findDailyBalanceBetween(JULY_1, SEPTEMBER_30);
        final List<BalanceCalcData> calcData = balanceService.findBalanceCalcDataBetween(JULY_1, SEPTEMBER_30);

        final BigDecimal dailyPaidExpense = dailyBalances.stream()
                .map(DailyBalance::getTotalExpense).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal dailyPendingExpense = dailyBalances.stream()
                .map(DailyBalance::getPendingTotalExpense).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal dailyPaidRevenue = dailyBalances.stream()
                .map(DailyBalance::getTotalRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum(calcData, DEBIT, BalanceCalcData.PaymentStatus.DONE)).isEqualByComparingTo(dailyPaidExpense);
        assertThat(sum(calcData, DEBIT, BalanceCalcData.PaymentStatus.PENDING))
                .isEqualByComparingTo(dailyPendingExpense);
        assertThat(sum(calcData, CREDIT, BalanceCalcData.PaymentStatus.DONE)).isEqualByComparingTo(dailyPaidRevenue);
        // 80 paid; 20 pending plus three rent occurrences of 100.
        assertThat(dailyPaidExpense).isEqualByComparingTo("-80");
        assertThat(dailyPendingExpense).isEqualByComparingTo("-320");
    }

    private BigDecimal sum(List<BalanceCalcData> calcData,
                           OperationType operationType,
                           BalanceCalcData.PaymentStatus status) {
        return calcData.stream()
                .filter(data -> data.getOperationType() == operationType && data.findStatus() == status)
                .map(BalanceCalcData::getBalanceCalcValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void stubMovements(LocalDate initialDate, LocalDate endDate, List<FinancialMovement> financialMovements) {
        when(financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate)).thenReturn(financialMovements);
    }

    private void stubPeriod(LocalDate initialDate, LocalDate endDate, List<FinancialMovement> financialMovements) {
        when(financialMovementService.findBalance(initialDate.minusDays(1))).thenReturn(BigDecimal.ZERO);
        when(financialMovementService.findProjectedBalance(initialDate.minusDays(1))).thenReturn(BigDecimal.ZERO);
        when(financialMovementService.findMovementsForBalanceCalculation(initialDate, endDate)).thenReturn(financialMovements);
    }

    private FixedBill buildMonthlyDebitFixedBill(LocalDate nextDueDate) {
        return FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(nextDueDate.getDayOfMonth()),
                FixedBillFactory.OPERATION_TYPE, DEBIT,
                FixedBillFactory.AMOUNT, new BigDecimal("100.00"),
                FixedBillFactory.START_DATE, LocalDate.of(2026, 1, 1),
                FixedBillFactory.END_DATE, LocalDate.of(2027, 12, 31),
                FixedBillFactory.NEXT_DUE_DATE, nextDueDate));
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
        when(fixedBillService.findAllByNextDueDateLessThanEqual(endDate)).thenReturn(mockFixedBillList);
        // These scenarios have a single occurrence per fixed bill inside the period.
        mockFixedBillList.forEach(fixedBill -> when(fixedBillService.findOccurrencesUntil(fixedBill, endDate))
                .thenReturn(List.of(fixedBill.getNextDueDate())));
        when(installmentBillService.findAllByNextInstallmentDateLessThanEqual(endDate)).thenReturn(mockInstallmentBillList);

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
