package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.BudgetEnvelopeEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class BudgetEnvelopeFactory {

    public static final UUID ENVELOPE_CODE = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final UUID CYCLE_CODE = UUID.fromString("88888888-8888-8888-8888-888888888888");
    public static final BigDecimal PLANNED = new BigDecimal("500.00");

    private BudgetEnvelopeFactory() {
    }

    public static PayCycleEntity buildCycleEntity() {
        return PayCycleEntity.builder()
                .id(30L)
                .code(CYCLE_CODE)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 12, 1))
                .kind(PayCycleKind.TRANSITION)
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .version(0L)
                .build();
    }

    /** An envelope of 500.00 for the factory category in the factory cycle. */
    public static BudgetEnvelopeEntity buildEntity() {
        return BudgetEnvelopeEntity.builder()
                .id(40L)
                .code(ENVELOPE_CODE)
                .cycle(buildCycleEntity())
                .categoryCode(CategoryFactory.CATEGORY_CODE)
                .plannedAmount(PLANNED)
                .flagActive(true)
                .version(0L)
                .build();
    }

    public static BudgetEnvelope buildModel() {
        return new BudgetEnvelope(ENVELOPE_CODE, CYCLE_CODE, CategoryFactory.CATEGORY_CODE,
                CategoryFactory.CATEGORY_NAME, PLANNED, new BigDecimal("100.00"), new BigDecimal("200.00"),
                new BigDecimal("300.00"), new BigDecimal("200.00"), new BigDecimal("200.00"), BigDecimal.ZERO, 0L);
    }
}
