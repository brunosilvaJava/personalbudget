package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.category.CategorySummaryService;
import com.bts.personalbudget.core.domain.service.dashboard.DashboardService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummaryService;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ReadSnapshotIsolationTest {

    @Test
    void shouldUseRepeatableReadForBalanceDashboardAndCategoryReportBoundaries() throws Exception {
        assertRepeatableRead(BalanceService.class, "findDailyBalanceBetween", LocalDate.class, LocalDate.class);
        assertRepeatableRead(BalanceService.class, "findBalanceCalcDataBetween", LocalDate.class, LocalDate.class);
        assertRepeatableRead(BalanceService.class, "findProjectedBillOccurrencesBetween", LocalDate.class, LocalDate.class);
        assertRepeatableRead(DashboardService.class, "getDashboard");
        assertRepeatableRead(DashboardService.class, "getDashboardForMonth", YearMonth.class);
        assertRepeatableRead(CategorySummaryService.class, "summarize", LocalDate.class, LocalDate.class, OperationType.class);
        assertRepeatableRead(FinancialMovementService.class, "findProjectedBalance", LocalDate.class);
        assertRepeatableRead(PayCycleSummaryService.class, "summarize", UUID.class);
    }
    private static void assertRepeatableRead(final Class<?> serviceClass, final String methodName,
                                             final Class<?>... parameterTypes) throws NoSuchMethodException {
        final Transactional transactional = serviceClass.getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(transactional).as("%s.%s", serviceClass.getSimpleName(), methodName).isNotNull();
        assertThat(transactional.readOnly() && transactional.isolation() == Isolation.REPEATABLE_READ).isTrue();
    }
}
