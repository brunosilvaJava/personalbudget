package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.budgetenvelope.BudgetEnvelopeRequest;
import com.bts.personalbudget.controller.budgetenvelope.BudgetEnvelopeResponse;
import com.bts.personalbudget.controller.budgetenvelope.CycleEnvelopesResponse;
import com.bts.personalbudget.core.domain.entity.BudgetEnvelopeEntity;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelope;
import com.bts.personalbudget.core.domain.service.budgetenvelope.CycleEnvelopes;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BudgetEnvelopeMapper {

    BudgetEnvelope toModel(BudgetEnvelopeRequest request);

    BudgetEnvelopeResponse toResponse(BudgetEnvelope envelope);

    CycleEnvelopesResponse toResponse(CycleEnvelopes envelopes);

    /**
     * Joins the plan with what the cycle summary says about its category. {@code detail} is null
     * when the cycle has no expense in the category yet: nothing consumed.
     */
    default BudgetEnvelope assemble(final BudgetEnvelopeEntity entity, final String categoryName,
                                    final PayCycleSummary.CategoryDetail detail) {
        final BigDecimal planned = entity.getPlannedAmount();
        final BigDecimal consumed = detail == null ? BigDecimal.ZERO : detail.projectedAmount();
        final BigDecimal recognized = detail == null ? BigDecimal.ZERO : detail.recognizedAmount();
        final BigDecimal remaining = planned.subtract(consumed);
        return new BudgetEnvelope(
                entity.getCode(),
                entity.getCycle().getCode(),
                entity.getCategoryCode(),
                categoryName,
                planned,
                recognized,
                consumed.subtract(recognized),
                consumed,
                remaining,
                remaining.max(BigDecimal.ZERO),
                remaining.negate().max(BigDecimal.ZERO),
                entity.getVersion());
    }
}
