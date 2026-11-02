package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.holidaycalendar.HolidayChangeImpactResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeOccurrenceResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleRequest;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleUpdateRequest;
import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeCalendarImpact;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * Written as default methods on purpose: the rule is one value in the model and four columns in the
 * entity, and a month is a YearMonth in the model and the first day of the month in the entity.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IncomeScheduleMapper {

    IncomeScheduleMapper INSTANCE = Mappers.getMapper(IncomeScheduleMapper.class);

    default IncomeSchedule toModel(final IncomeScheduleEntity entity) {
        return new IncomeSchedule(
                entity.getCode(),
                entity.getDescription(),
                entity.getAmount(),
                entity.getCategoryCode(),
                new IncomeDateRule(entity.getRuleType(), entity.getRuleValue(), entity.getBusinessWeek(),
                        entity.getNonBusinessDayPolicy()),
                toYearMonth(entity.getStartMonth()),
                toYearMonth(entity.getEndMonth()),
                Boolean.TRUE.equals(entity.getAnchor()),
                entity.getStatus(),
                entity.getReplacesFixedBillCode());
    }

    default List<IncomeSchedule> toModelList(final List<IncomeScheduleEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    /**
     * A new entity: no id, so it is inserted.
     */
    default IncomeScheduleEntity toEntity(final IncomeSchedule schedule) {
        final IncomeScheduleEntity entity = IncomeScheduleEntity.builder()
                .code(schedule.code())
                .replacesFixedBillCode(schedule.replacesFixedBillCode())
                .build();
        copyEditableFields(schedule, entity);
        entity.setStartMonth(toDate(schedule.startMonth()));
        return entity;
    }

    /**
     * Copies what an edit can change. The code, the start month and the replaced fixed bill stay as
     * they were stored.
     */
    default void copyEditableFields(final IncomeSchedule schedule, final IncomeScheduleEntity entity) {
        entity.setDescription(schedule.description());
        entity.setAmount(schedule.amount());
        entity.setCategoryCode(schedule.categoryCode());
        entity.setRuleType(schedule.rule().ruleType());
        entity.setRuleValue(schedule.rule().value());
        entity.setBusinessWeek(schedule.rule().businessWeek());
        entity.setNonBusinessDayPolicy(schedule.rule().nonBusinessDayPolicy());
        entity.setEndMonth(toDate(schedule.endMonth()));
        entity.setAnchor(schedule.anchor());
        entity.setStatus(schedule.status());
    }

    default IncomeOccurrence toModel(final IncomeOccurrenceEntity entity) {
        return new IncomeOccurrence(
                entity.getCode(),
                entity.getIncomeSchedule().getCode(),
                toYearMonth(entity.getNominalMonth()),
                entity.getPlannedDate(),
                entity.getPlannedAmount(),
                entity.getMovementCode());
    }

    default List<IncomeOccurrence> toOccurrenceModelList(final List<IncomeOccurrenceEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    default IncomeSchedule toModel(final IncomeScheduleRequest request) {
        return new IncomeSchedule(
                null,
                request.description(),
                request.amount(),
                request.categoryCode(),
                new IncomeDateRule(request.ruleType(), request.ruleValue(), request.businessWeek(),
                        request.nonBusinessDayPolicy()),
                request.startMonth(),
                request.endMonth(),
                Boolean.TRUE.equals(request.anchor()),
                null,
                request.replacesFixedBillCode());
    }

    default IncomeScheduleUpdate toUpdate(final IncomeScheduleUpdateRequest request) {
        return new IncomeScheduleUpdate(
                request.description(),
                request.amount(),
                request.categoryCode(),
                request.ruleType(),
                request.ruleValue(),
                request.businessWeek(),
                request.nonBusinessDayPolicy(),
                request.endMonth(),
                Boolean.TRUE.equals(request.removeEndMonth()),
                request.anchor());
    }

    default IncomeScheduleResponse toResponse(final IncomeSchedule schedule) {
        return new IncomeScheduleResponse(
                schedule.code(),
                schedule.description(),
                schedule.amount(),
                schedule.categoryCode(),
                schedule.rule().ruleType(),
                schedule.rule().value(),
                schedule.rule().businessWeek(),
                schedule.rule().nonBusinessDayPolicy(),
                schedule.startMonth(),
                schedule.endMonth(),
                schedule.anchor(),
                schedule.status(),
                schedule.replacesFixedBillCode());
    }

    default List<IncomeScheduleResponse> toResponseList(final List<IncomeSchedule> schedules) {
        return schedules.stream().map(this::toResponse).toList();
    }

    default IncomeOccurrenceResponse toResponse(final IncomeOccurrenceView view) {
        return new IncomeOccurrenceResponse(
                view.nominalMonth(),
                view.state(),
                view.plannedDate(),
                view.amount(),
                view.movementCode(),
                view.pendingYear());
    }

    default List<IncomeOccurrenceResponse> toOccurrenceResponseList(final List<IncomeOccurrenceView> views) {
        return views.stream().map(this::toResponse).toList();
    }

    /**
     * The occurrence returned right after a materialization, which is always MATERIALIZED.
     */
    default IncomeOccurrenceResponse toResponse(final IncomeOccurrence occurrence) {
        return toResponse(IncomeOccurrenceView.materialized(occurrence));
    }

    default HolidayChangeImpactResponse toResponse(final IncomeCalendarImpact impact) {
        return new HolidayChangeImpactResponse(
                impact.scheduleCode(),
                impact.scheduleDescription(),
                impact.change().before().nominalMonth(),
                toResponse(impact.change().before()),
                toResponse(impact.change().after()));
    }

    default List<HolidayChangeImpactResponse> toImpactResponseList(final List<IncomeCalendarImpact> impacts) {
        return impacts.stream().map(this::toResponse).toList();
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
