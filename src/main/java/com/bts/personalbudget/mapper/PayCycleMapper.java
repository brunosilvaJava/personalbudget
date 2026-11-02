package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.paycycle.PayCycleActivationPreviewResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleConfigResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleCurrentNotFoundResponse;
import com.bts.personalbudget.controller.paycycle.PayCyclePendingResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleResponse;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleActivationPreview;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigView;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleLabel;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
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
                .anchorSchedule(anchorSchedule)
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
