package com.bts.personalbudget.core.domain.service.income;

import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * The main income as its own domain: a rule that says on which day of each month the money is
 * expected, and the months it serves. Always a CREDIT.
 *
 * @param amount               expected amount of each occurrence, positive
 * @param categoryCode         category (CREDIT) the generated movements carry; optional
 * @param rule                 how the date inside a month is picked
 * @param startMonth           first nominal month served
 * @param endMonth             last nominal month served; null means no end
 * @param anchor               true for the main income that will open the pay cycles (E4); at most
 *                             one ACTIVE schedule is the anchor
 * @param replacesFixedBillCode the FixedBill whose receipt this schedule took over, when it was
 *                             created as a replacement; null otherwise
 */
public record IncomeSchedule(
        UUID code,
        String description,
        BigDecimal amount,
        UUID categoryCode,
        IncomeDateRule rule,
        YearMonth startMonth,
        YearMonth endMonth,
        boolean anchor,
        IncomeScheduleStatus status,
        UUID replacesFixedBillCode
) {

    public IncomeSchedule {
        if (code == null) {
            code = UUID.randomUUID();
        }
        if (status == null) {
            status = IncomeScheduleStatus.ACTIVE;
        }
    }

    public boolean isActive() {
        return status == IncomeScheduleStatus.ACTIVE;
    }

    /**
     * Whether the nominal month is inside the schedule's validity, whatever its status.
     */
    public boolean covers(final YearMonth month) {
        return !month.isBefore(startMonth) && (endMonth == null || !month.isAfter(endMonth));
    }

    /**
     * Whether the schedule still produces a new occurrence for the month: active and inside its
     * validity. An inactive schedule keeps the occurrences it already materialized and nothing more.
     */
    public boolean generates(final YearMonth month) {
        return isActive() && covers(month);
    }
}
