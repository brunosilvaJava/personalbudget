package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.category.CategorySummaryService;
import com.bts.personalbudget.core.domain.service.dashboard.DashboardService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummaryService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadSnapshotIsolationTest {

    @Test
    void shouldUseRepeatableReadForBalanceDashboardAndCategoryReportBoundaries() throws Exception {
        assertRepeatableRead(BalanceService.class, "findDailyBalanceBetween",
                java.time.LocalDate.class, java.time.LocalDate.class);
        assertRepeatableRead(BalanceService.class, "findBalanceCalcDataBetween",
                java.time.LocalDate.class, java.time.LocalDate.class);
        assertRepeatableRead(BalanceService.class, "findProjectedBillOccurrencesBetween",
                java.time.LocalDate.class, java.time.LocalDate.class);
        assertRepeatableRead(DashboardService.class, "getDashboard");
        assertRepeatableRead(DashboardService.class, "getDashboardForMonth", java.time.YearMonth.class);
        assertRepeatableRead(CategorySummaryService.class, "summarize",
                java.time.LocalDate.class, java.time.LocalDate.class,
                com.bts.personalbudget.core.domain.enumerator.OperationType.class);
    }

    @Test
    void shouldReadProjectedBalanceWithinOneRepeatableReadTransaction() throws Exception {
        assertRepeatableRead(FinancialMovementService.class, "findProjectedBalance", java.time.LocalDate.class);
    }

    @Test
    void shouldKeepPayCycleSummaryReadsInOneRepeatableReadTransaction() throws Exception {
        assertRepeatableRead(PayCycleSummaryService.class, "summarize", java.util.UUID.class);
    }

    private static void assertRepeatableRead(final Class<?> serviceClass,
                                             final String methodName,
                                             final Class<?>... parameterTypes) throws NoSuchMethodException {
        final Method method = serviceClass.getMethod(methodName, parameterTypes);
        final Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("%s.%s should declare a repeatable-read snapshot", serviceClass.getSimpleName(), methodName)
                .isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
