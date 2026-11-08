package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.holidaycalendar.HolidayCycleBoundaryImpactResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleActivationPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleAdjustmentResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionPreviewRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionRequest;
import com.bts.personalbudget.controller.paycycle.PayCycleAnchorTransitionResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleConfigResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleCurrentNotFoundResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleEndAdjustmentResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleIncomeImpactResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleMovementImpactResponse;
import com.bts.personalbudget.controller.paycycle.PayCyclePendingResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleSummaryResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleSummaryResponse.PayCycleSummaryCategoryResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleSummaryResponse.PayCycleSummaryPendingResponse;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleAdjustmentEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceImpact;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAnchorChange;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAnchorTransitionPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAnchorTransitionResult;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleBoundaryImpact;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleActivationPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustment;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigView;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleEndAdjustmentResult;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleLabel;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * Written as default methods on purpose: the anchor is a schedule entity in the entity and a code in
 * the model, a month is a YearMonth in the model and the first day of the month in the entity, and the
 * response carries values (label, last day, status) calculated from the model.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PayCycleMapper {

    PayCycleMapper INSTANCE = Mappers.getMapper(PayCycleMapper.class);

    HolidayCycleBoundaryImpactResponse toResponse(PayCycleBoundaryImpact impact);

    List<HolidayCycleBoundaryImpactResponse> toBoundaryImpactResponseList(List<PayCycleBoundaryImpact> impacts);

    default PayCycleConfig toModel(final PayCycleConfigEntity entity) {
        return new PayCycleConfig(
                entity.getCode(),
                entity.getMode(),
                entity.getAnchorSchedule() == null ? null : entity.getAnchorSchedule().getCode(),
                entity.getActivationDate(),
                entity.getVersion());
    }

    default PayCycle toModel(final PayCycleEntity entity) {
        return new PayCycle(
                entity.getCode(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getKind(),
                entity.getAnchorSchedule() == null ? null : entity.getAnchorSchedule().getCode(),
                toYearMonth(entity.getAnchorNominalMonth()),
                entity.getLifecycle(),
                Boolean.TRUE.equals(entity.getStartAdjusted()),
                Boolean.TRUE.equals(entity.getEndAdjusted()),
                entity.getVersion());
    }

    default List<PayCycle> toModelList(final List<PayCycleEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    /**
     * A new entity: no id, so it is inserted.
     *
     * @param anchorSchedule the schedule of the anchor occurrence that opens the cycle; null for a
     *                       TRANSITION no anchor occurrence opens
     */
    default PayCycleEntity toEntity(final PayCycle cycle, final IncomeScheduleEntity anchorSchedule) {
        return PayCycleEntity.builder()
                .code(cycle.code())
                .startDate(cycle.startDate())
                .endDate(cycle.endDate())
                .kind(cycle.kind())
                .anchorSchedule(cycle.anchorScheduleCode() == null ? null : anchorSchedule)
                .anchorNominalMonth(toDate(cycle.anchorNominalMonth()))
                .lifecycle(cycle.lifecycle())
                .startAdjusted(cycle.startAdjusted())
                .endAdjusted(cycle.endAdjusted())
                .build();
    }

    default PayCycleResponse toResponse(final PayCycleView view) {
        final PayCycle cycle = view.cycle();
        return new PayCycleResponse(
                cycle.code(),
                cycle.startDate(),
                cycle.endDate(),
                cycle.lastDay(),
                cycle.totalDays(),
                PayCycleLabel.of(cycle),
                cycle.anchorNominalMonth(),
                cycle.kind(),
                view.status(),
                cycle.anchorScheduleCode(),
                cycle.startAdjusted(),
                cycle.endAdjusted(),
                cycle.version(),
                toPendingResponseList(view.pending()));
    }

    default List<PayCycleResponse> toResponseList(final List<PayCycleView> views) {
        return views.stream().map(this::toResponse).toList();
    }

    default PayCycleSummaryResponse toSummaryResponse(final PayCycleSummary summary) {
        return new PayCycleSummaryResponse(
                toResponse(summary.cycle()),
                summary.recognizedRevenue(),
                summary.recognizedExpense(),
                summary.projectedRevenue(),
                summary.projectedExpense(),
                summary.projectionIncomplete(),
                summary.categories().stream().map(this::toSummaryCategoryResponse).toList(),
                summary.pending().stream().map(this::toSummaryPendingResponse).toList());
    }

    default PayCycleSummaryCategoryResponse toSummaryCategoryResponse(final PayCycleSummary.CategoryDetail category) {
        return new PayCycleSummaryCategoryResponse(
                category.categoryCode(),
                category.categoryName(),
                category.operationType(),
                category.recognizedAmount(),
                category.projectedAmount());
    }

    default PayCycleSummaryPendingResponse toSummaryPendingResponse(final PayCycleSummary.Pending pending) {
        return new PayCycleSummaryPendingResponse(
                pending.source().name(),
                pending.type().name(),
                pending.sourceCode(),
                pending.nominalMonth(),
                pending.year(),
                pending.boundaryDate(),
                pending.anchorDate());
    }

    default PayCycleConfigResponse toResponse(final PayCycleConfigView view) {
        return toResponse(view.config(), view.pending());
    }

    default PayCycleConfigResponse toResponse(final PayCycleConfig config, final List<PayCyclePending> pending) {
        return new PayCycleConfigResponse(
                config.mode(),
                config.anchorScheduleCode(),
                config.activationDate(),
                config.version(),
                toPendingResponseList(pending));
    }

    default PayCycleActivationPreviewResponse toResponse(final PayCycleActivationPreview preview) {
        return new PayCycleActivationPreviewResponse(
                preview.version(),
                toResponseList(preview.cycles()),
                toPendingResponseList(preview.pending()));
    }

    default PayCycleAnchorChange toModel(final PayCycleAnchorTransitionPreviewRequest request) {
        return new PayCycleAnchorChange(
                request.anchorScheduleCode(),
                toScheduleUpdate(request.ruleType(), request.ruleValue(), request.businessWeek(),
                        request.nonBusinessDayPolicy(), request.endMonth(), request.removeEndMonth()),
                request.transitionShape(),
                null,
                null);
    }

    default PayCycleAnchorChange toModel(final PayCycleAnchorTransitionRequest request) {
        return new PayCycleAnchorChange(
                request.anchorScheduleCode(),
                toScheduleUpdate(request.ruleType(), request.ruleValue(), request.businessWeek(),
                        request.nonBusinessDayPolicy(), request.endMonth(), request.removeEndMonth()),
                request.transitionShape(),
                request.justification(),
                request.expectedVersion());
    }

    default IncomeScheduleUpdate toScheduleUpdate(
            final com.bts.personalbudget.core.domain.enumerator.DateRuleType ruleType,
            final Integer ruleValue,
            final com.bts.personalbudget.core.domain.enumerator.BusinessWeek businessWeek,
            final com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy nonBusinessDayPolicy,
            final YearMonth endMonth,
            final Boolean removeEndMonth) {
        return new IncomeScheduleUpdate(null, null, null, ruleType, ruleValue, businessWeek,
                nonBusinessDayPolicy, endMonth, Boolean.TRUE.equals(removeEndMonth), null);
    }

    default PayCycleAnchorTransitionPreviewResponse toResponse(final PayCycleAnchorTransitionPreview preview) {
        return new PayCycleAnchorTransitionPreviewResponse(
                preview.expectedVersion(),
                preview.previousAnchorScheduleCode(),
                preview.anchorScheduleCode(),
                preview.boundary(),
                preview.oldBoundary(),
                preview.newBoundary(),
                preview.transitionShape(),
                toResponseList(preview.preservedCycles()),
                toResponseList(preview.cancelledCycles()),
                toResponseList(preview.newCycles()),
                toPendingResponseList(preview.pending()));
    }

    default PayCycleAnchorTransitionResponse toResponse(final PayCycleAnchorTransitionResult result) {
        return new PayCycleAnchorTransitionResponse(
                toResponse(result.configuration()),
                toResponse(result.adjustment()),
                toResponseList(result.preservedCycles()),
                toResponseList(result.cancelledCycles()),
                toResponseList(result.newCycles()));
    }

    default PayCycleCurrentNotFoundResponse toNotFoundResponse(final CurrentPayCycle current) {
        return new PayCycleCurrentNotFoundResponse(
                "Nenhum ciclo em andamento",
                toPendingResponseList(current.pending()));
    }

    default PayCyclePendingResponse toResponse(final PayCyclePending pending) {
        return new PayCyclePendingResponse(
                pending.type(),
                pending.nominalMonth(),
                pending.year(),
                pending.cycleCode(),
                pending.boundaryDate(),
                pending.anchorDate());
    }

    default List<PayCyclePendingResponse> toPendingResponseList(final List<PayCyclePending> pending) {
        return pending.stream().map(this::toResponse).toList();
    }

    // --- history of adjustments

    default PayCycleAdjustment toModel(final PayCycleAdjustmentEntity entity) {
        return new PayCycleAdjustment(
                entity.getCode(),
                entity.getType(),
                entity.getPreviousCycle() == null ? null : entity.getPreviousCycle().getCode(),
                entity.getNextCycle() == null ? null : entity.getNextCycle().getCode(),
                entity.getOldBoundary(),
                entity.getNewBoundary(),
                entity.getJustification(),
                entity.getExpectedPreviousVersion(),
                entity.getExpectedNextVersion(),
                entity.getExpectedConfigVersion(),
                entity.getCreatedDate());
    }

    default List<PayCycleAdjustment> toAdjustmentModelList(final List<PayCycleAdjustmentEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    /**
     * A new history entity: no id, so it is inserted, and no creation date, which auditing fills. The
     * model only carries the codes of the cycles, so the entities they point to come from the caller;
     * either may be null.
     */
    default PayCycleAdjustmentEntity toEntity(final PayCycleAdjustment adjustment,
                                              final PayCycleEntity previousCycle,
                                              final PayCycleEntity nextCycle) {
        return PayCycleAdjustmentEntity.builder()
                .code(adjustment.code())
                .type(adjustment.type())
                .previousCycle(previousCycle)
                .nextCycle(nextCycle)
                .oldBoundary(adjustment.oldBoundary())
                .newBoundary(adjustment.newBoundary())
                .justification(adjustment.justification())
                .expectedPreviousVersion(adjustment.expectedPreviousVersion())
                .expectedNextVersion(adjustment.expectedNextVersion())
                .expectedConfigVersion(adjustment.expectedConfigVersion())
                .build();
    }

    default PayCycleAdjustmentResponse toResponse(final PayCycleAdjustment adjustment) {
        return new PayCycleAdjustmentResponse(
                adjustment.code(),
                adjustment.type(),
                adjustment.previousCycleCode(),
                adjustment.nextCycleCode(),
                adjustment.oldBoundary(),
                adjustment.newBoundary(),
                adjustment.justification(),
                adjustment.expectedPreviousVersion(),
                adjustment.expectedNextVersion(),
                adjustment.expectedConfigVersion(),
                adjustment.createdDate());
    }

    default List<PayCycleAdjustmentResponse> toAdjustmentResponseList(final List<PayCycleAdjustment> adjustments) {
        return adjustments.stream().map(this::toResponse).toList();
    }

    default PayCycleEndAdjustmentResponse toResponse(final PayCycleEndAdjustmentResult result) {
        return new PayCycleEndAdjustmentResponse(
                toResponse(result.adjustment()),
                toResponse(result.previous()),
                toResponse(result.next()));
    }

    /**
     * The versions to send back come from the cycles as they are (the first two of {@code before}).
     */
    default PayCycleEndAdjustmentPreviewResponse toResponse(final PayCycleEndAdjustmentPreview preview) {
        return new PayCycleEndAdjustmentPreviewResponse(
                preview.oldEndDate(),
                preview.newEndDate(),
                preview.before().get(0).cycle().version(),
                preview.before().get(1).cycle().version(),
                toResponseList(preview.before()),
                toResponseList(preview.after()),
                preview.leavingCycleCode(),
                preview.receivingCycleCode(),
                preview.movements().stream().map(this::toResponse).toList(),
                preview.incomeOccurrences().stream().map(this::toResponse).toList());
    }

    /**
     * The amount is always positive, as in the rest of the API; the operation type says the direction.
     */
    default PayCycleMovementImpactResponse toResponse(final FinancialMovement movement) {
        final BigDecimal amount = movement.amount() != null && movement.operationType() == OperationType.DEBIT
                ? movement.amount().abs()
                : movement.amount();
        return new PayCycleMovementImpactResponse(
                movement.code(),
                movement.description(),
                movement.operationType(),
                amount,
                movement.status(),
                movement.competenceDate(),
                movement.categoryCode());
    }

    default PayCycleIncomeImpactResponse toResponse(final IncomeOccurrenceImpact impact) {
        final IncomeOccurrenceView occurrence = impact.occurrence();
        return new PayCycleIncomeImpactResponse(
                impact.scheduleCode(),
                impact.scheduleDescription(),
                occurrence.nominalMonth(),
                occurrence.state(),
                occurrence.plannedDate(),
                occurrence.amount(),
                occurrence.movementCode());
    }

    /**
     * @return the first day of the month, or null when there is no month
     */
    default LocalDate toDate(final YearMonth month) {
        return month == null ? null : month.atDay(1);
    }

    default YearMonth toYearMonth(final LocalDate date) {
        return date == null ? null : YearMonth.from(date);
    }

}
